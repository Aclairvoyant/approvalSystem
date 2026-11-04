package com.approval.system.service.impl;

import com.approval.system.common.enums.GameStatusEnum;
import com.approval.system.common.enums.MahjongModeEnum;
import com.approval.system.common.enums.MahjongSeatTypeEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.dto.MahjongCreateRequest;
import com.approval.system.dto.MahjongRoomResponse;
import com.approval.system.dto.MahjongSeatResponse;
import com.approval.system.entity.MahjongGame;
import com.approval.system.entity.MahjongMove;
import com.approval.system.entity.User;
import com.approval.system.mapper.MahjongGameMapper;
import com.approval.system.mapper.MahjongMoveMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IMahjongService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.mahjong.MahjongAction;
import com.approval.system.service.mahjong.MahjongEngine;
import com.approval.system.service.mahjong.MahjongEvent;
import com.approval.system.service.mahjong.MahjongPhase;
import com.approval.system.service.mahjong.MahjongRound;
import com.approval.system.service.mahjong.MahjongRuleException;
import com.approval.system.service.mahjong.MahjongRules;
import com.approval.system.service.mahjong.MahjongViews;
import com.approval.system.service.notification.MahjongRoomChangedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
public class MahjongServiceImpl implements IMahjongService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int OVERDUE_BATCH_SIZE = 50;
    /** 截止时间过了这么久还没执行才由兜底任务补跑，避免和正常调度抢 */
    private static final long OVERDUE_GRACE_SECONDS = 2;
    private static final String HIDDEN_MOVE_DATA = "{\"hidden\":true}";

    private final MahjongGameMapper gameMapper;
    private final MahjongMoveMapper moveMapper;
    private final UserMapper userMapper;
    private final IUserRelationService userRelationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ObjectReader roundReader;

    private Clock clock = Clock.systemDefaultZone();
    private Random random = new SecureRandom();

    public MahjongServiceImpl(MahjongGameMapper gameMapper,
                              MahjongMoveMapper moveMapper,
                              UserMapper userMapper,
                              IUserRelationService userRelationService,
                              ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper) {
        this.gameMapper = gameMapper;
        this.moveMapper = moveMapper;
        this.userMapper = userMapper;
        this.userRelationService = userRelationService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.roundReader = objectMapper.readerFor(MahjongRound.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    void setRandom(Random random) {
        this.random = random;
    }

    // ------------------------------------------------------------------ 房间

    @Override
    @Transactional
    public MahjongRoomResponse createRoom(Long userId, MahjongCreateRequest request) {
        MahjongModeEnum mode = request.getMode();
        Integer seatCount = request.getSeatCount();
        if (mode == null || seatCount == null || seatCount < 2 || seatCount > 4) {
            throw BusinessException.badRequest("请选择玩法和 2 到 4 个座位");
        }
        int capPoints = request.getCapPoints() == null ? MahjongRules.DEFAULT_CAP_POINTS : request.getCapPoints();
        if (capPoints < 0 || capPoints > MahjongRules.MAX_CAP_POINTS) {
            throw BusinessException.badRequest("封顶分数不正确");
        }
        Long inviteeId = request.getInviteUserId();
        if (inviteeId != null) {
            ensureCanInvite(userId, inviteeId);
        }

        LocalDateTime now = now();
        MahjongGame game = MahjongGame.builder()
                .gameCode(generateGameCode())
                .ownerId(userId)
                .mode(mode.getCode())
                .seatCount(seatCount)
                .seat0UserId(userId)
                .botMask(0)
                .invitedUserId(inviteeId)
                .capPoints(capPoints)
                .gameStatus(GameStatusEnum.WAITING.getCode())
                .roundNo(0)
                .dealerSeat(0)
                .huangCount(0)
                .totalScores(writeJson(zeros(seatCount)))
                .stateVersion(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        gameMapper.insert(game);
        return view(game, null, userId);
    }

    @Override
    @Transactional
    public MahjongRoomResponse joinRoom(Long userId, String gameCode) {
        String code = gameCode == null ? "" : gameCode.trim();
        MahjongGame game = gameMapper.selectOne(new LambdaQueryWrapper<MahjongGame>()
                .eq(MahjongGame::getGameCode, code)
                .last("FOR UPDATE"));
        if (game == null) {
            throw BusinessException.notFound("房间不存在，请检查房间号");
        }
        if (seatOf(game, userId) != null) {
            return view(game, readRound(game), userId);
        }
        if (GameStatusEnum.CANCELLED.getCode().equals(game.getGameStatus())) {
            throw BusinessException.badRequest("房间已经取消");
        }
        if (!GameStatusEnum.WAITING.getCode().equals(game.getGameStatus())) {
            throw BusinessException.badRequest("这桌已经开局，不能再加入");
        }
        int seat = firstEmptySeat(game);
        if (seat < 0) {
            throw BusinessException.conflict("房间已满");
        }
        setSeatUser(game, seat, userId);
        saveRoomChange(game);
        return view(game, null, userId);
    }

    @Override
    public MahjongRoomResponse getRoom(Long gameId, Long userId) {
        MahjongGame game = requireGame(gameId);
        if (seatOf(game, userId) == null && !Objects.equals(game.getInvitedUserId(), userId)) {
            throw BusinessException.forbidden("你不在这个麻将房间里");
        }
        return view(game, readRound(game), userId);
    }

    @Override
    public Page<MahjongRoomResponse> listRooms(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        int size = Math.min(Math.max(pageSize == null ? 10 : pageSize, 1), MAX_PAGE_SIZE);
        int current = Math.max(pageNum == null ? 1 : pageNum, 1);
        LambdaQueryWrapper<MahjongGame> wrapper = new LambdaQueryWrapper<MahjongGame>()
                .select(MahjongGame.class, column -> !"state_data".equals(column.getColumn()))
                .and(w -> w.eq(MahjongGame::getSeat0UserId, userId)
                        .or().eq(MahjongGame::getSeat1UserId, userId)
                        .or().eq(MahjongGame::getSeat2UserId, userId)
                        .or().eq(MahjongGame::getSeat3UserId, userId)
                        .or().eq(MahjongGame::getInvitedUserId, userId));
        if (status != null) {
            wrapper.eq(MahjongGame::getGameStatus, status);
        }
        wrapper.orderByDesc(MahjongGame::getUpdatedAt);

        Page<MahjongGame> page = new Page<>(current, size);
        gameMapper.selectPage(page, wrapper);
        List<MahjongGame> games = page.getRecords() == null ? List.of() : page.getRecords();
        Set<Long> userIds = new LinkedHashSet<>();
        games.forEach(game -> userIds.addAll(relatedUserIds(game)));
        Map<Long, User> users = loadUsers(userIds);

        Page<MahjongRoomResponse> result = new Page<>(current, size, page.getTotal());
        result.setRecords(games.stream()
                .map(game -> toResponse(game, null, userId, users, List.of()))
                .toList());
        return result;
    }

    @Override
    @Transactional
    public MahjongRoomResponse setSeat(Long gameId, Long userId, int seatIndex, boolean bot) {
        MahjongGame game = lockGame(gameId);
        requireOwner(game, userId, "只有房主可以设置电脑玩家");
        requireWaiting(game, "已经开局，不能再调整座位");
        if (seatIndex <= 0 || seatIndex >= game.getSeatCount()) {
            throw BusinessException.badRequest("座位不存在");
        }
        if (bot) {
            if (seatUser(game, seatIndex) != null || isBotSeat(game, seatIndex)) {
                throw BusinessException.conflict("这个座位已经有人了");
            }
        } else if (!isBotSeat(game, seatIndex)) {
            throw BusinessException.badRequest("这个座位不是电脑");
        }
        setBotSeat(game, seatIndex, bot);
        saveRoomChange(game);
        return view(game, null, userId);
    }

    @Override
    @Transactional
    public MahjongRoomResponse invite(Long gameId, Long userId, Long inviteeId) {
        MahjongGame game = lockGame(gameId);
        requireOwner(game, userId, "只有房主可以邀请");
        requireWaiting(game, "已经开局，不能再邀请");
        ensureCanInvite(userId, inviteeId);
        if (seatOf(game, inviteeId) != null) {
            throw BusinessException.badRequest("对方已经在房间里了");
        }
        game.setInvitedUserId(inviteeId);
        saveRoomChange(game);
        return view(game, null, userId);
    }

    @Override
    @Transactional
    public void leaveRoom(Long gameId, Long userId) {
        MahjongGame game = lockGame(gameId);
        Integer seat = seatOf(game, userId);
        if (seat == null) {
            throw BusinessException.badRequest("你不在这个房间里");
        }
        if (Objects.equals(game.getOwnerId(), userId)) {
            throw BusinessException.badRequest("房主不能离开，可以取消房间");
        }
        requireWaiting(game, "已经开局，不能离开座位");
        setSeatUser(game, seat, null);
        saveRoomChange(game);
    }

    @Override
    @Transactional
    public MahjongRoomResponse startGame(Long gameId, Long userId) {
        MahjongGame game = lockGame(gameId);
        requireOwner(game, userId, "只有房主可以开局");
        requireWaiting(game, "房间已经开局或已经取消");
        for (int seat = 0; seat < game.getSeatCount(); seat++) {
            if (seatUser(game, seat) == null && !isBotSeat(game, seat)) {
                throw BusinessException.badRequest("还有空位，等人加入或设成电脑后再开局");
            }
        }
        game.setStartedAt(now());
        MahjongRound round = startRound(game, 1, 0, 0, null);
        return view(game, round, userId);
    }

    @Override
    @Transactional
    public MahjongRoomResponse nextRound(Long gameId, Long userId) {
        MahjongGame game = lockGame(gameId);
        requireOwner(game, userId, "只有房主可以开始下一局");
        if (!GameStatusEnum.FINISHED.getCode().equals(game.getGameStatus())) {
            throw BusinessException.badRequest("本局还没结束");
        }
        MahjongRound previous = readRound(game);
        // 庄家每局顺延到下一个座位
        int dealer = (game.getDealerSeat() + 1) % game.getSeatCount();
        MahjongRound round = startRound(game, game.getRoundNo() + 1, dealer, game.getHuangCount(), previous);
        return view(game, round, userId);
    }

    @Override
    @Transactional
    public void cancelRoom(Long gameId, Long userId) {
        MahjongGame game = lockGame(gameId);
        requireOwner(game, userId, "只有房主可以取消房间");
        requireWaiting(game, "已经开局的房间不能取消");
        game.setGameStatus(GameStatusEnum.CANCELLED.getCode());
        game.setEndedAt(now());
        saveRoomChange(game);
    }

    // ------------------------------------------------------------------ 牌局

    @Override
    @Transactional
    public MahjongRoomResponse act(Long gameId, Long userId, MahjongActionRequest request) {
        MahjongGame game = lockGame(gameId);
        Integer seat = seatOf(game, userId);
        if (seat == null) {
            throw BusinessException.forbidden("你不是这桌的玩家");
        }
        if (!GameStatusEnum.PLAYING.getCode().equals(game.getGameStatus())) {
            throw BusinessException.badRequest("现在没有进行中的牌局");
        }
        MahjongRound round = requireRound(game);
        try {
            MahjongEngine.apply(round, seat, new MahjongAction(request.getType(), request.getTile(),
                    request.getChiStart()));
        } catch (MahjongRuleException e) {
            throw BusinessException.badRequest(e.getMessage());
        }
        saveRoundChange(game, round);
        return view(game, round, userId);
    }

    @Override
    @Transactional
    public void runScheduledStep(Long gameId, int expectedVersion) {
        MahjongGame game = gameMapper.selectOne(new LambdaQueryWrapper<MahjongGame>()
                .eq(MahjongGame::getId, gameId)
                .last("FOR UPDATE"));
        if (game == null || !GameStatusEnum.PLAYING.getCode().equals(game.getGameStatus())
                || !Objects.equals(game.getStateVersion(), expectedVersion)) {
            return;
        }
        MahjongRound round = readRound(game);
        if (round == null || round.isFinished() || round.getDeadline() > clock.millis()) {
            return;
        }
        if (MahjongEngine.isAutomaticTurn(round)) {
            MahjongEngine.playAutomaticTurn(round);
        } else if (MahjongEngine.isWaitingForHuman(round)) {
            MahjongEngine.timeout(round);
        } else {
            return;
        }
        saveRoundChange(game, round);
    }

    @Override
    public List<MahjongGame> findOverdueGames() {
        List<MahjongGame> games = gameMapper.selectList(new LambdaQueryWrapper<MahjongGame>()
                .select(MahjongGame::getId, MahjongGame::getStateVersion)
                .eq(MahjongGame::getGameStatus, GameStatusEnum.PLAYING.getCode())
                .isNotNull(MahjongGame::getActionDeadline)
                .le(MahjongGame::getActionDeadline, now().minusSeconds(OVERDUE_GRACE_SECONDS))
                .last("LIMIT " + OVERDUE_BATCH_SIZE));
        return games == null ? List.of() : games;
    }

    @Override
    public boolean isSeated(Long gameId, Long userId) {
        if (gameId == null || userId == null) {
            return false;
        }
        MahjongGame game = gameMapper.selectOne(new LambdaQueryWrapper<MahjongGame>()
                .select(MahjongGame::getId, MahjongGame::getSeatCount, MahjongGame::getSeat0UserId,
                        MahjongGame::getSeat1UserId, MahjongGame::getSeat2UserId, MahjongGame::getSeat3UserId)
                .eq(MahjongGame::getId, gameId));
        return game != null && seatOf(game, userId) != null;
    }

    // ------------------------------------------------------------------ 保存与推送

    private MahjongRound startRound(MahjongGame game, int roundNo, int dealer, int huangCount, MahjongRound previous) {
        MahjongRound round = MahjongEngine.deal(modeOf(game), game.getSeatCount(), dealer, roundNo, huangCount,
                game.getCapPoints() == null ? MahjongRules.DEFAULT_CAP_POINTS : game.getCapPoints(),
                botFlags(game), random);
        round.setMoveSeq(previous != null ? previous.getMoveSeq() : 0);
        game.setRoundNo(roundNo);
        game.setDealerSeat(dealer);
        game.setGameStatus(GameStatusEnum.PLAYING.getCode());
        saveRoundChange(game, round);
        return round;
    }

    /** 牌局变化：定下一次等待的截止时间、写动作日志、本局结束就结算，保存后推送 */
    private void saveRoundChange(MahjongGame game, MahjongRound round) {
        int version = nextVersion(game);
        long nowMillis = clock.millis();
        round.setStep(version);
        round.setDeadline(round.isFinished() ? 0 : nowMillis + waitMillis(round));
        List<MahjongEvent> events = round.drainEvents();
        saveMoves(game, round, events);
        if (round.isFinished()) {
            settle(game, round);
        }
        game.setStateData(writeJson(round));
        game.setStateVersion(version);
        game.setActionDeadline(round.getDeadline() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(round.getDeadline()), clock.getZone())
                : null);
        game.setUpdatedAt(now());
        gameMapper.updateById(game);
        publish(game, round, events);
    }

    /** 只有座位、邀请或房间状态变化（开局前） */
    private void saveRoomChange(MahjongGame game) {
        game.setStateVersion(nextVersion(game));
        game.setUpdatedAt(now());
        gameMapper.updateById(game);
        publish(game, readRound(game), List.of());
    }

    private static long waitMillis(MahjongRound round) {
        if (MahjongEngine.isAutomaticTurn(round)) {
            return MahjongRules.AUTO_ACTION_DELAY_MS;
        }
        return round.getPhase() == MahjongPhase.CLAIM ? MahjongRules.CLAIM_TIMEOUT_MS : MahjongRules.TURN_TIMEOUT_MS;
    }

    private void settle(MahjongGame game, MahjongRound round) {
        List<Integer> totals = readTotals(game);
        List<Integer> deltas = round.getResult().getDeltas();
        for (int seat = 0; seat < totals.size() && seat < deltas.size(); seat++) {
            totals.set(seat, totals.get(seat) + deltas.get(seat));
        }
        game.setTotalScores(writeJson(totals));
        game.setGameStatus(GameStatusEnum.FINISHED.getCode());
        // 荒庄后下一局加一个荒番，连续荒庄累加；有人胡牌后清零
        game.setHuangCount(round.getResult().isDrawGame() ? round.getHuangCount() + 1 : 0);
        game.setEndedAt(now());
    }

    private void saveMoves(MahjongGame game, MahjongRound round, List<MahjongEvent> events) {
        LocalDateTime now = now();
        for (MahjongEvent event : events) {
            int number = round.getMoveSeq() + 1;
            round.setMoveSeq(number);
            moveMapper.insert(MahjongMove.builder()
                    .gameId(game.getId())
                    .roundNo(round.getRoundNo())
                    .moveNumber(number)
                    .seatIndex(event.getSeat())
                    .playerId(seatUser(game, event.getSeat()))
                    .actionType(event.getType())
                    .tile(event.getTile())
                    .moveData(event.isHidden() ? HIDDEN_MOVE_DATA : null)
                    .createdAt(now)
                    .build());
        }
    }

    /**
     * 推送给房间：公共视角（不含任何人的暗牌和牌墙）发到 topic，每个真人自己的手牌单独发。
     * 由监听器在事务提交后发送。
     */
    private void publish(MahjongGame game, MahjongRound round, List<MahjongEvent> events) {
        Map<Long, User> users = loadUsers(relatedUserIds(game));
        MahjongRoomResponse room = toResponse(game, round, null, users, events);
        Map<Long, MahjongViews.PrivateHand> hands = new LinkedHashMap<>();
        if (round != null) {
            for (int seat = 0; seat < game.getSeatCount(); seat++) {
                Long userId = seatUser(game, seat);
                if (userId != null) {
                    hands.put(userId, MahjongViews.privateView(round, seat, events));
                }
            }
        }
        long nextRunAt = round != null && !round.isFinished()
                && GameStatusEnum.PLAYING.getCode().equals(game.getGameStatus()) ? round.getDeadline() : 0;
        eventPublisher.publishEvent(new MahjongRoomChangedEvent(game.getId(), game.getStateVersion(), room,
                hands, nextRunAt));
    }

    // ------------------------------------------------------------------ 视角

    private MahjongRoomResponse view(MahjongGame game, MahjongRound round, Long viewerId) {
        return toResponse(game, round, viewerId, loadUsers(relatedUserIds(game)), List.of());
    }

    /** viewerId 为 null 表示房间公共视角（推送用），不带任何人的手牌 */
    private MahjongRoomResponse toResponse(MahjongGame game, MahjongRound round, Long viewerId,
                                           Map<Long, User> users, List<MahjongEvent> events) {
        MahjongModeEnum mode = modeOf(game);
        List<Integer> totals = readTotals(game);
        List<MahjongSeatResponse> seats = new ArrayList<>();
        for (int seat = 0; seat < game.getSeatCount(); seat++) {
            Long userId = seatUser(game, seat);
            MahjongSeatResponse.MahjongSeatResponseBuilder builder = MahjongSeatResponse.builder()
                    .seatIndex(seat)
                    .totalScore(totals.get(seat))
                    .owner(seat == 0);
            if (userId != null) {
                User user = users.get(userId);
                builder.type(MahjongSeatTypeEnum.HUMAN.name())
                        .userId(userId)
                        .name(displayName(user))
                        .avatar(user != null ? user.getAvatar() : null);
            } else if (isBotSeat(game, seat)) {
                builder.type(MahjongSeatTypeEnum.BOT.name()).name("电脑 " + (seat + 1));
            } else {
                builder.type(MahjongSeatTypeEnum.EMPTY.name());
            }
            seats.add(builder.build());
        }
        Integer mySeat = viewerId == null ? null : seatOf(game, viewerId);
        User invited = game.getInvitedUserId() != null ? users.get(game.getInvitedUserId()) : null;
        return MahjongRoomResponse.builder()
                .id(game.getId())
                .gameCode(game.getGameCode())
                .mode(mode.name())
                .modeName(mode.getDesc())
                .seatCount(game.getSeatCount())
                .capPoints(game.getCapPoints())
                .ownerId(game.getOwnerId())
                .gameStatus(game.getGameStatus())
                .roundNo(game.getRoundNo())
                .dealerSeat(game.getDealerSeat())
                .huangCount(game.getHuangCount())
                .invitedUserId(game.getInvitedUserId())
                .invitedUserName(game.getInvitedUserId() != null ? displayName(invited) : null)
                .seats(seats)
                .mySeat(mySeat)
                .version(game.getStateVersion())
                .serverTime(clock.millis())
                .round(round != null ? MahjongViews.publicView(round, events) : null)
                .me(round != null && mySeat != null ? MahjongViews.privateView(round, mySeat, List.of()) : null)
                .createdAt(game.getCreatedAt())
                .startedAt(game.getStartedAt())
                .endedAt(game.getEndedAt())
                .updatedAt(game.getUpdatedAt())
                .build();
    }

    private static String displayName(User user) {
        if (user == null) {
            return "玩家";
        }
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername() != null ? user.getUsername() : "玩家";
    }

    private Map<Long, User> loadUsers(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        Map<Long, User> byId = new HashMap<>();
        if (users != null) {
            users.forEach(user -> byId.put(user.getId(), user));
        }
        return byId;
    }

    private Set<Long> relatedUserIds(MahjongGame game) {
        Set<Long> ids = new LinkedHashSet<>();
        for (int seat = 0; seat < game.getSeatCount(); seat++) {
            Long userId = seatUser(game, seat);
            if (userId != null) {
                ids.add(userId);
            }
        }
        if (game.getInvitedUserId() != null) {
            ids.add(game.getInvitedUserId());
        }
        return ids;
    }

    // ------------------------------------------------------------------ 座位

    static Long seatUser(MahjongGame game, int seat) {
        return switch (seat) {
            case 0 -> game.getSeat0UserId();
            case 1 -> game.getSeat1UserId();
            case 2 -> game.getSeat2UserId();
            case 3 -> game.getSeat3UserId();
            default -> null;
        };
    }

    private static void setSeatUser(MahjongGame game, int seat, Long userId) {
        switch (seat) {
            case 0 -> game.setSeat0UserId(userId);
            case 1 -> game.setSeat1UserId(userId);
            case 2 -> game.setSeat2UserId(userId);
            case 3 -> game.setSeat3UserId(userId);
            default -> throw BusinessException.badRequest("座位不存在");
        }
    }

    private static boolean isBotSeat(MahjongGame game, int seat) {
        int mask = game.getBotMask() == null ? 0 : game.getBotMask();
        return ((mask >> seat) & 1) == 1;
    }

    private static void setBotSeat(MahjongGame game, int seat, boolean bot) {
        int mask = game.getBotMask() == null ? 0 : game.getBotMask();
        game.setBotMask(bot ? mask | (1 << seat) : mask & ~(1 << seat));
    }

    private static Integer seatOf(MahjongGame game, Long userId) {
        if (userId == null || game.getSeatCount() == null) {
            return null;
        }
        for (int seat = 0; seat < game.getSeatCount(); seat++) {
            if (Objects.equals(seatUser(game, seat), userId)) {
                return seat;
            }
        }
        return null;
    }

    private static int firstEmptySeat(MahjongGame game) {
        for (int seat = 1; seat < game.getSeatCount(); seat++) {
            if (seatUser(game, seat) == null && !isBotSeat(game, seat)) {
                return seat;
            }
        }
        return -1;
    }

    private static boolean[] botFlags(MahjongGame game) {
        boolean[] bots = new boolean[game.getSeatCount()];
        for (int seat = 0; seat < bots.length; seat++) {
            bots[seat] = isBotSeat(game, seat);
        }
        return bots;
    }

    // ------------------------------------------------------------------ 工具

    private void ensureCanInvite(Long ownerId, Long inviteeId) {
        if (inviteeId == null || Objects.equals(ownerId, inviteeId)) {
            throw BusinessException.badRequest("不能邀请自己");
        }
        if (!userRelationService.isRelated(ownerId, inviteeId)) {
            throw BusinessException.forbidden("只能直接邀请你的对象，其他人请用房间号加入");
        }
    }

    private static void requireOwner(MahjongGame game, Long userId, String message) {
        if (!Objects.equals(game.getOwnerId(), userId)) {
            throw BusinessException.forbidden(message);
        }
    }

    private static void requireWaiting(MahjongGame game, String message) {
        if (!GameStatusEnum.WAITING.getCode().equals(game.getGameStatus())) {
            throw BusinessException.badRequest(message);
        }
    }

    private MahjongGame requireGame(Long gameId) {
        MahjongGame game = gameMapper.selectById(gameId);
        if (game == null) {
            throw BusinessException.notFound("麻将房间不存在");
        }
        return game;
    }

    private MahjongGame lockGame(Long gameId) {
        MahjongGame game = gameMapper.selectOne(new LambdaQueryWrapper<MahjongGame>()
                .eq(MahjongGame::getId, gameId)
                .last("FOR UPDATE"));
        if (game == null) {
            throw BusinessException.notFound("麻将房间不存在");
        }
        return game;
    }

    private MahjongRound requireRound(MahjongGame game) {
        MahjongRound round = readRound(game);
        if (round == null) {
            throw BusinessException.badRequest("现在没有进行中的牌局");
        }
        return round;
    }

    private static MahjongModeEnum modeOf(MahjongGame game) {
        MahjongModeEnum mode = MahjongModeEnum.getByCode(game.getMode());
        if (mode == null) {
            throw new IllegalStateException("未知的麻将玩法: " + game.getMode());
        }
        return mode;
    }

    private static int nextVersion(MahjongGame game) {
        return (game.getStateVersion() == null ? 0 : game.getStateVersion()) + 1;
    }

    private MahjongRound readRound(MahjongGame game) {
        String data = game.getStateData();
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return roundReader.readValue(data);
        } catch (IOException e) {
            throw new IllegalStateException("麻将牌局数据损坏: gameId=" + game.getId(), e);
        }
    }

    private List<Integer> readTotals(MahjongGame game) {
        List<Integer> totals = new ArrayList<>();
        String data = game.getTotalScores();
        if (data != null && !data.isBlank()) {
            try {
                totals.addAll(objectMapper.readValue(data, new TypeReference<List<Integer>>() {
                }));
            } catch (IOException e) {
                log.warn("麻将总分数据解析失败，按 0 分处理: gameId={}", game.getId(), e);
            }
        }
        while (totals.size() < game.getSeatCount()) {
            totals.add(0);
        }
        return totals;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化麻将数据失败", e);
        }
    }

    private static List<Integer> zeros(int size) {
        List<Integer> zeros = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zeros.add(0);
        }
        return zeros;
    }

    private String generateGameCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            Long count = gameMapper.selectCount(new LambdaQueryWrapper<MahjongGame>()
                    .eq(MahjongGame::getGameCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new IllegalStateException("生成麻将房间号失败");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
