package com.approval.system.service.impl;

import com.approval.system.common.enums.*;
import com.approval.system.dto.*;
import com.approval.system.entity.*;
import com.approval.system.mapper.*;
import com.approval.system.service.IMahjongEngine;
import com.approval.system.service.IMahjongService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 麻将游戏服务实现
 */
@Slf4j
@Service
public class MahjongServiceImpl implements IMahjongService {

    private final MahjongGameMapper gameMapper;
    private final MahjongRoundMapper roundMapper;
    private final MahjongActionMapper actionMapper;
    private final MahjongUserStatsMapper userStatsMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ShanghaiQiaomaEngine qiaomaEngine;
    private final ShanghaiBaidaEngine baidaEngine;

    // 游戏引擎实例缓存 (gameId -> engine)
    private final Map<Long, IMahjongEngine> engineCache = new ConcurrentHashMap<>();

    public MahjongServiceImpl(
            MahjongGameMapper gameMapper,
            MahjongRoundMapper roundMapper,
            MahjongActionMapper actionMapper,
            MahjongUserStatsMapper userStatsMapper,
            UserMapper userMapper,
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("shanghaiQiaomaEngine") ShanghaiQiaomaEngine qiaomaEngine,
            @Qualifier("shanghaiBaidaEngine") ShanghaiBaidaEngine baidaEngine) {
        this.gameMapper = gameMapper;
        this.roundMapper = roundMapper;
        this.actionMapper = actionMapper;
        this.userStatsMapper = userStatsMapper;
        this.userMapper = userMapper;
        this.messagingTemplate = messagingTemplate;
        this.qiaomaEngine = qiaomaEngine;
        this.baidaEngine = baidaEngine;
    }

    @Override
    @Transactional
    public MahjongGameResponse createGame(MahjongCreateRequest request, Long userId) {
        // 检查用户是否已在其他游戏中
        MahjongGameResponse activeGame = getActiveGame(userId);
        if (activeGame != null) {
            throw new IllegalStateException("您已在游戏中，请先退出当前游戏");
        }

        // 创建游戏
        MahjongGame game = new MahjongGame();
        game.setGameCode(generateGameCode());
        game.setRuleType(request.getRuleType());
        game.setFlowerMode(request.getFlowerMode() != null ? request.getFlowerMode() : 8);
        game.setPlayerCount(request.getPlayerCount() != null ? request.getPlayerCount() : 4);
        game.setTotalRounds(request.getTotalRounds() != null ? request.getTotalRounds() : 8);
        game.setBaseScore(request.getBaseScore() != null ? request.getBaseScore() : 1);
        game.setMaxScore(request.getMaxScore());
        game.setFlyCount(request.getFlyCount() != null ? request.getFlyCount() : 0);
        game.setPlayer1Id(userId);
        game.setGameStatus(MahjongGameStatus.WAITING.getCode());
        game.setCurrentRound(0);
        game.setDealerSeat(1);
        game.setPlayer1Score(0);
        game.setPlayer2Score(0);
        game.setPlayer3Score(0);
        game.setPlayer4Score(0);
        game.setCreatedAt(LocalDateTime.now());

        gameMapper.insert(game);

        log.info("创建麻将游戏: gameId={}, gameCode={}, userId={}", game.getId(), game.getGameCode(), userId);

        return buildGameResponse(game, userId);
    }

    @Override
    @Transactional
    public MahjongGameResponse joinGame(MahjongJoinRequest request, Long userId) {
        MahjongGame game = gameMapper.selectByGameCode(request.getGameCode());
        if (game == null) {
            throw new IllegalArgumentException("房间不存在");
        }

        if (game.getGameStatus() != MahjongGameStatus.WAITING.getCode()) {
            throw new IllegalStateException("游戏已开始或已结束");
        }

        // 检查是否已在游戏中
        if (isPlayerInGame(game, userId)) {
            return buildGameResponse(game, userId);
        }

        // 检查用户是否在其他游戏中
        MahjongGameResponse activeGame = getActiveGame(userId);
        if (activeGame != null && !activeGame.getId().equals(game.getId())) {
            throw new IllegalStateException("您已在其他游戏中");
        }

        // 加入空位
        if (game.getPlayer2Id() == null) {
            game.setPlayer2Id(userId);
        } else if (game.getPlayer3Id() == null && game.getPlayerCount() >= 3) {
            game.setPlayer3Id(userId);
        } else if (game.getPlayer4Id() == null && game.getPlayerCount() >= 4) {
            game.setPlayer4Id(userId);
        } else {
            throw new IllegalStateException("房间已满");
        }

        game.setUpdatedAt(LocalDateTime.now());
        gameMapper.updateById(game);

        log.info("玩家加入麻将游戏: gameId={}, userId={}", game.getId(), userId);

        // 广播玩家加入消息
        broadcastGameState(game);

        return buildGameResponse(game, userId);
    }

    @Override
    @Transactional
    public void leaveGame(Long gameId, Long userId) {
        MahjongGame game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new IllegalArgumentException("游戏不存在");
        }

        if (game.getGameStatus() == MahjongGameStatus.PLAYING.getCode()) {
            throw new IllegalStateException("游戏进行中，无法离开");
        }

        // 移除玩家
        if (userId.equals(game.getPlayer1Id())) {
            // 房主离开，解散房间或转移房主
            if (game.getPlayer2Id() != null) {
                game.setPlayer1Id(game.getPlayer2Id());
                game.setPlayer2Id(game.getPlayer3Id());
                game.setPlayer3Id(game.getPlayer4Id());
                game.setPlayer4Id(null);
            } else {
                game.setGameStatus(MahjongGameStatus.CANCELLED.getCode());
            }
        } else if (userId.equals(game.getPlayer2Id())) {
            game.setPlayer2Id(game.getPlayer3Id());
            game.setPlayer3Id(game.getPlayer4Id());
            game.setPlayer4Id(null);
        } else if (userId.equals(game.getPlayer3Id())) {
            game.setPlayer3Id(game.getPlayer4Id());
            game.setPlayer4Id(null);
        } else if (userId.equals(game.getPlayer4Id())) {
            game.setPlayer4Id(null);
        }

        game.setUpdatedAt(LocalDateTime.now());
        gameMapper.updateById(game);

        log.info("玩家离开麻将游戏: gameId={}, userId={}", gameId, userId);

        // 广播状态变化
        if (game.getGameStatus() != MahjongGameStatus.CANCELLED.getCode()) {
            broadcastGameState(game);
        }
    }

    @Override
    @Transactional
    public MahjongGameResponse startGame(Long gameId, Long userId) {
        MahjongGame game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new IllegalArgumentException("游戏不存在");
        }

        if (!userId.equals(game.getPlayer1Id())) {
            throw new IllegalStateException("只有房主可以开始游戏");
        }

        if (game.getGameStatus() != MahjongGameStatus.WAITING.getCode()) {
            throw new IllegalStateException("游戏状态不正确");
        }

        // 检查人数
        int playerCount = getPlayerCount(game);
        if (playerCount < game.getPlayerCount()) {
            throw new IllegalStateException("玩家人数不足");
        }

        // 更新游戏状态
        game.setGameStatus(MahjongGameStatus.PLAYING.getCode());
        game.setCurrentRound(1);
        game.setDealerSeat(1); // 第一局由玩家1做庄
        game.setStartedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());
        gameMapper.updateById(game);

        // 创建第一局
        startNewRound(game);

        log.info("麻将游戏开始: gameId={}", gameId);

        // 广播游戏开始
        broadcastGameState(game);

        return buildGameResponse(game, userId);
    }

    @Override
    public MahjongGameResponse getGameState(Long gameId, Long userId) {
        MahjongGame game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new IllegalArgumentException("游戏不存在");
        }
        return buildGameResponse(game, userId);
    }

    @Override
    public MahjongGameResponse getGameByCode(String gameCode, Long userId) {
        MahjongGame game = gameMapper.selectByGameCode(gameCode);
        if (game == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        return buildGameResponse(game, userId);
    }

    @Override
    @Transactional
    public MahjongGameResponse executeAction(Long gameId, Long userId, MahjongActionRequest request) {
        MahjongGame game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new IllegalArgumentException("游戏不存在");
        }

        if (game.getGameStatus() != MahjongGameStatus.PLAYING.getCode()) {
            throw new IllegalStateException("游戏未开始");
        }

        int playerSeat = getPlayerSeat(game, userId);
        if (playerSeat == 0) {
            throw new IllegalStateException("您不在此游戏中");
        }

        // 获取引擎
        IMahjongEngine engine = getOrCreateEngine(game);

        // 获取当前局
        MahjongRound round = roundMapper.selectCurrentRound(gameId);
        if (round == null) {
            throw new IllegalStateException("当前没有进行中的局");
        }

        // 解析操作类型
        MahjongActionType actionType = MahjongActionType.valueOf(request.getActionType());
        MahjongTile tile = request.getTile() != null ? MahjongTile.fromCode(request.getTile()) : null;

        // 执行操作
        executePlayerAction(engine, round, playerSeat, actionType, tile, game);

        // 保存操作记录
        saveAction(round.getId(), playerSeat, actionType, tile);

        // 更新局状态到数据库
        updateRoundState(round, engine);

        // 检查游戏是否结束
        checkGameEnd(game, round, engine);

        // 广播状态
        broadcastGameState(game);

        return buildGameResponse(game, userId);
    }

    @Override
    public List<MahjongGameResponse> getUserGames(Long userId) {
        List<MahjongGame> games = gameMapper.selectByUserId(userId);
        return games.stream()
                .map(g -> buildGameResponse(g, userId))
                .collect(Collectors.toList());
    }

    @Override
    public MahjongGameResponse getActiveGame(Long userId) {
        List<MahjongGame> games = gameMapper.selectByUserIdAndStatus(userId, MahjongGameStatus.PLAYING.getCode());
        if (games.isEmpty()) {
            games = gameMapper.selectByUserIdAndStatus(userId, MahjongGameStatus.WAITING.getCode());
        }
        if (games.isEmpty()) {
            return null;
        }
        return buildGameResponse(games.get(0), userId);
    }

    @Override
    public int getPlayerSeat(Long gameId, Long userId) {
        MahjongGame game = gameMapper.selectById(gameId);
        return getPlayerSeat(game, userId);
    }

    @Override
    @Transactional
    public MahjongGameResponse nextRound(Long gameId, Long userId) {
        MahjongGame game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new IllegalArgumentException("游戏不存在");
        }

        // 检查是否可以开始下一局
        if (game.getCurrentRound() >= game.getTotalRounds()) {
            throw new IllegalStateException("游戏已结束");
        }

        // 开始新一局
        game.setCurrentRound(game.getCurrentRound() + 1);
        game.setUpdatedAt(LocalDateTime.now());
        gameMapper.updateById(game);

        startNewRound(game);

        broadcastGameState(game);

        return buildGameResponse(game, userId);
    }

    // ==================== 私有方法 ====================

    private String generateGameCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }

        // 确保唯一性
        while (gameMapper.countByGameCode(code.toString()) > 0) {
            code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(chars.charAt(random.nextInt(chars.length())));
            }
        }

        return code.toString();
    }

    private boolean isPlayerInGame(MahjongGame game, Long userId) {
        return userId.equals(game.getPlayer1Id()) ||
               userId.equals(game.getPlayer2Id()) ||
               userId.equals(game.getPlayer3Id()) ||
               userId.equals(game.getPlayer4Id());
    }

    private int getPlayerSeat(MahjongGame game, Long userId) {
        if (game == null || userId == null) return 0;
        if (userId.equals(game.getPlayer1Id())) return 1;
        if (userId.equals(game.getPlayer2Id())) return 2;
        if (userId.equals(game.getPlayer3Id())) return 3;
        if (userId.equals(game.getPlayer4Id())) return 4;
        return 0;
    }

    private int getPlayerCount(MahjongGame game) {
        int count = 0;
        if (game.getPlayer1Id() != null) count++;
        if (game.getPlayer2Id() != null) count++;
        if (game.getPlayer3Id() != null) count++;
        if (game.getPlayer4Id() != null) count++;
        return count;
    }

    private IMahjongEngine getOrCreateEngine(MahjongGame game) {
        return engineCache.computeIfAbsent(game.getId(), id -> {
            if (game.getRuleType() == MahjongRuleType.BAI_DA.getCode()) {
                return baidaEngine;
            } else {
                return qiaomaEngine;
            }
        });
    }

    private void startNewRound(MahjongGame game) {
        // 创建新局记录
        MahjongRound round = new MahjongRound();
        round.setGameId(game.getId());
        round.setRoundNumber(game.getCurrentRound());
        round.setRoundStatus(MahjongRoundStatus.PLAYING.getCode());
        round.setDealerSeat(game.getDealerSeat());
        round.setCurrentTurn(game.getDealerSeat());
        round.setStartedAt(LocalDateTime.now());

        roundMapper.insert(round);

        // 初始化引擎并发牌
        IMahjongEngine engine = getOrCreateEngine(game);
        engine.initRound(round, game.getPlayerCount(), game.getFlowerMode());
        engine.shuffleAndDeal(game.getDealerSeat());

        // 如果是百搭模式，保存骰子和百搭信息
        if (engine instanceof ShanghaiBaidaEngine) {
            ShanghaiBaidaEngine baidaEngine = (ShanghaiBaidaEngine) engine;
            IMahjongEngine.DiceResult dice = baidaEngine.getDiceResult();
            if (dice != null) {
                game.setDice1(dice.getDice1());
                game.setDice2(dice.getDice2());
            }
            MahjongTile wildTile = baidaEngine.getWildTile();
            if (wildTile != null) {
                game.setWildTile(wildTile.toCode());
            }
            MahjongTile guideTile = baidaEngine.getGuideTile();
            if (guideTile != null) {
                game.setGuideTile(guideTile.toCode());
            }
            gameMapper.updateById(game);
        }

        // 保存初始手牌到round
        updateRoundState(round, engine);

        log.info("开始新局: gameId={}, roundNumber={}", game.getId(), round.getRoundNumber());
    }

    private void executePlayerAction(IMahjongEngine engine, MahjongRound round,
                                     int playerSeat, MahjongActionType actionType,
                                     MahjongTile tile, MahjongGame game) {
        switch (actionType) {
            case DRAW:
                engine.draw(playerSeat);
                break;
            case DISCARD:
                if (tile == null) {
                    throw new IllegalArgumentException("出牌时必须指定牌");
                }
                engine.discard(playerSeat, tile);
                // 保存最后打出的牌
                round.setLastTile(tile.toCode());
                round.setLastAction("DISCARD");
                round.setLastActionSeat(playerSeat);

                // 检查其他玩家是否可以碰/杠/胡
                List<Map<String, Object>> pendingActions = checkPendingActions(engine, tile, playerSeat, game.getPlayerCount());
                if (!pendingActions.isEmpty()) {
                    // 有玩家可以响应，设置等待状态
                    round.setPendingActions(pendingActions);
                } else {
                    // 没有人可以响应，直接进入下一个玩家回合
                    round.setPendingActions(null);
                    int nextSeat = engine.getNextSeat(playerSeat, game.getPlayerCount());
                    round.setCurrentTurn(nextSeat);
                    // 自动摸牌
                    if (engine.canDraw(nextSeat)) {
                        engine.draw(nextSeat);
                    }
                }
                break;
            case PONG:
                // 获取被碰的牌（从最后出牌信息）
                MahjongTile pongTile = tile != null ? tile : MahjongTile.fromCode(round.getLastTile());
                engine.pong(playerSeat, pongTile, round.getLastActionSeat());
                round.setCurrentTurn(playerSeat); // 碰牌后由碰牌者出牌
                round.setPendingActions(null); // 清除等待状态
                round.setLastAction("PONG");
                round.setLastActionSeat(playerSeat);
                break;
            case MING_KONG:
                MahjongTile kongTile = tile != null ? tile : MahjongTile.fromCode(round.getLastTile());
                engine.mingKong(playerSeat, kongTile, round.getLastActionSeat());
                round.setCurrentTurn(playerSeat);
                round.setPendingActions(null);
                round.setLastAction("MING_KONG");
                round.setLastActionSeat(playerSeat);
                break;
            case AN_KONG:
                engine.anKong(playerSeat, tile);
                round.setLastAction("AN_KONG");
                round.setLastActionSeat(playerSeat);
                break;
            case BU_KONG:
                engine.buKong(playerSeat, tile);
                round.setLastAction("BU_KONG");
                round.setLastActionSeat(playerSeat);
                break;
            case HU:
                IMahjongEngine.HuResult result = engine.hu(playerSeat);
                handleHu(game, round, result, engine);
                break;
            case PASS:
                // 过 - 处理等待队列
                handlePass(engine, round, playerSeat, game);
                break;
            default:
                throw new IllegalArgumentException("未知操作类型: " + actionType);
        }
    }

    /**
     * 检查其他玩家是否可以碰/杠/胡
     */
    private List<Map<String, Object>> checkPendingActions(IMahjongEngine engine, MahjongTile discardedTile,
                                                          int discardSeat, int playerCount) {
        List<Map<String, Object>> pendingActions = new ArrayList<>();

        for (int seat = 1; seat <= playerCount; seat++) {
            if (seat == discardSeat) continue;

            List<String> availableActions = new ArrayList<>();

            // 检查是否可以胡（敲麻规则下不能点炮，所以这里不检查胡）
            // 如果是百搭模式且允许点炮，则需要检查

            // 检查是否可以杠
            if (engine.canMingKong(seat, discardedTile)) {
                availableActions.add("MING_KONG");
            }

            // 检查是否可以碰
            if (engine.canPong(seat, discardedTile)) {
                availableActions.add("PONG");
            }

            if (!availableActions.isEmpty()) {
                availableActions.add("PASS"); // 添加过选项
                Map<String, Object> action = new HashMap<>();
                action.put("seat", seat);
                action.put("actions", availableActions);
                pendingActions.add(action);
            }
        }

        return pendingActions;
    }

    /**
     * 处理过操作
     */
    private void handlePass(IMahjongEngine engine, MahjongRound round, int playerSeat, MahjongGame game) {
        List<Map<String, Object>> pendingActions = round.getPendingActions();
        if (pendingActions == null || pendingActions.isEmpty()) {
            return;
        }

        // 移除该玩家的等待操作
        pendingActions.removeIf(action -> {
            Object seatObj = action.get("seat");
            int seat = seatObj instanceof Integer ? (Integer) seatObj : Integer.parseInt(seatObj.toString());
            return seat == playerSeat;
        });

        if (pendingActions.isEmpty()) {
            // 所有玩家都过了，进入下一个玩家回合
            round.setPendingActions(null);
            int nextSeat = engine.getNextSeat(round.getLastActionSeat(), game.getPlayerCount());
            round.setCurrentTurn(nextSeat);
            // 自动摸牌
            if (engine.canDraw(nextSeat)) {
                engine.draw(nextSeat);
            }
        } else {
            round.setPendingActions(pendingActions);
        }
    }

    private void handleHu(MahjongGame game, MahjongRound round,
                          IMahjongEngine.HuResult result, IMahjongEngine engine) {
        // 计算得分
        Map<Integer, Integer> scoreChangesInt = engine.calculateScore(
                result, game.getFlyCount(), game.getBaseScore(), game.getMaxScore());

        // 更新分数
        game.setPlayer1Score(game.getPlayer1Score() + scoreChangesInt.getOrDefault(1, 0));
        game.setPlayer2Score(game.getPlayer2Score() + scoreChangesInt.getOrDefault(2, 0));
        game.setPlayer3Score(game.getPlayer3Score() + scoreChangesInt.getOrDefault(3, 0));
        game.setPlayer4Score(game.getPlayer4Score() + scoreChangesInt.getOrDefault(4, 0));

        // 转换为 Map<String, Integer> 用于存储
        Map<String, Integer> scoreChanges = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : scoreChangesInt.entrySet()) {
            scoreChanges.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // 更新局状态
        round.setRoundStatus(MahjongRoundStatus.HU.getCode());
        round.setWinnerSeat(result.getWinnerSeat());
        round.setHuType(result.getHuTypes().stream()
                .map(MahjongHuType::getDescription)
                .collect(Collectors.joining(",")));
        round.setFanCount(result.getFanCount());
        round.setScoreChanges(scoreChanges);
        round.setEndedAt(LocalDateTime.now());

        // 下一局庄家 = 胡牌者
        game.setDealerSeat(result.getWinnerSeat());

        gameMapper.updateById(game);
        roundMapper.updateById(round);
    }

    private void saveAction(Long roundId, int playerSeat, MahjongActionType actionType, MahjongTile tile) {
        MahjongAction action = new MahjongAction();
        action.setRoundId(roundId);
        action.setPlayerSeat(playerSeat);
        action.setActionType(actionType.name());
        action.setTile(tile != null ? tile.toCode() : null);
        action.setCreatedAt(LocalDateTime.now());
        actionMapper.insert(action);
    }

    private void updateRoundState(MahjongRound round, IMahjongEngine engine) {
        // 将引擎状态保存到round的JSON字段
        round.setWallTiles(tilesToCodes(getWallTilesFromEngine(engine)));
        round.setPlayer1Hand(tilesToCodes(engine.getPlayerHand(1)));
        round.setPlayer2Hand(tilesToCodes(engine.getPlayerHand(2)));
        round.setPlayer3Hand(tilesToCodes(engine.getPlayerHand(3)));
        round.setPlayer4Hand(tilesToCodes(engine.getPlayerHand(4)));
        round.setPlayer1Discards(tilesToCodes(engine.getPlayerDiscards(1)));
        round.setPlayer2Discards(tilesToCodes(engine.getPlayerDiscards(2)));
        round.setPlayer3Discards(tilesToCodes(engine.getPlayerDiscards(3)));
        round.setPlayer4Discards(tilesToCodes(engine.getPlayerDiscards(4)));
        round.setPlayer1Melds(meldsToList(engine.getPlayerMelds(1)));
        round.setPlayer2Melds(meldsToList(engine.getPlayerMelds(2)));
        round.setPlayer3Melds(meldsToList(engine.getPlayerMelds(3)));
        round.setPlayer4Melds(meldsToList(engine.getPlayerMelds(4)));
        // 保存花牌信息
        round.setPlayer1Flowers(tilesToCodes(engine.getPlayerFlowers(1)));
        round.setPlayer2Flowers(tilesToCodes(engine.getPlayerFlowers(2)));
        round.setPlayer3Flowers(tilesToCodes(engine.getPlayerFlowers(3)));
        round.setPlayer4Flowers(tilesToCodes(engine.getPlayerFlowers(4)));

        roundMapper.updateById(round);
    }

    private List<MahjongTile> getWallTilesFromEngine(IMahjongEngine engine) {
        // 这个方法需要在引擎接口中添加
        // 目前简化处理，返回空列表
        return new ArrayList<>();
    }

    private List<String> tilesToCodes(List<MahjongTile> tiles) {
        if (tiles == null) return new ArrayList<>();
        return tiles.stream()
                .map(MahjongTile::toCode)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> meldsToList(List<IMahjongEngine.Meld> melds) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (melds == null || melds.isEmpty()) {
            return result;
        }
        for (IMahjongEngine.Meld meld : melds) {
            Map<String, Object> meldMap = new HashMap<>();
            meldMap.put("type", meld.getType().name());
            meldMap.put("tiles", meld.getTiles().stream()
                    .map(MahjongTile::toCode)
                    .collect(Collectors.toList()));
            meldMap.put("concealed", meld.isConcealed());
            result.add(meldMap);
        }
        return result;
    }

    private void checkGameEnd(MahjongGame game, MahjongRound round, IMahjongEngine engine) {
        // 检查是否流局
        if (engine.isRoundDraw() && round.getRoundStatus() == MahjongRoundStatus.PLAYING.getCode()) {
            round.setRoundStatus(MahjongRoundStatus.DRAW.getCode());
            round.setEndedAt(LocalDateTime.now());
            roundMapper.updateById(round);

            // 流局后随机选新庄家
            game.setDealerSeat(new Random().nextInt(game.getPlayerCount()) + 1);
            gameMapper.updateById(game);
        }

        // 检查游戏是否结束
        if (game.getCurrentRound() >= game.getTotalRounds() &&
            round.getRoundStatus() != MahjongRoundStatus.PLAYING.getCode()) {
            game.setGameStatus(MahjongGameStatus.FINISHED.getCode());
            game.setEndedAt(LocalDateTime.now());
            gameMapper.updateById(game);

            // 更新用户统计
            updateUserStats(game);

            // 清理引擎缓存
            engineCache.remove(game.getId());

            log.info("麻将游戏结束: gameId={}", game.getId());
        }
    }

    private void broadcastGameState(MahjongGame game) {
        // 向每个玩家发送其可见的游戏状态
        String destination = "/topic/mahjong/game/" + game.getId();

        // 发送游戏基础信息给所有玩家
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_STATE");
        message.put("gameId", game.getId());
        message.put("gameCode", game.getGameCode());
        message.put("gameStatus", game.getGameStatus());
        message.put("currentRound", game.getCurrentRound());

        messagingTemplate.convertAndSend(destination, message);
    }

    private MahjongGameResponse buildGameResponse(MahjongGame game, Long userId) {
        MahjongGameResponse response = new MahjongGameResponse();

        // 基础信息
        response.setId(game.getId());
        response.setGameCode(game.getGameCode());

        // 游戏配置
        response.setRuleType(game.getRuleType());
        response.setRuleTypeName(MahjongRuleType.fromCode(game.getRuleType()).getDescription());
        response.setFlowerMode(game.getFlowerMode());
        response.setPlayerCount(game.getPlayerCount());
        response.setTotalRounds(game.getTotalRounds());
        response.setBaseScore(game.getBaseScore());
        response.setMaxScore(game.getMaxScore());
        response.setFlyCount(game.getFlyCount());

        // 百搭配置
        response.setWildTile(game.getWildTile());
        response.setGuideTile(game.getGuideTile());
        response.setDice1(game.getDice1());
        response.setDice2(game.getDice2());

        // 玩家信息
        fillPlayerInfo(response, game);

        // 游戏状态
        response.setGameStatus(game.getGameStatus());
        response.setGameStatusName(MahjongGameStatus.fromCode(game.getGameStatus()).getName());
        response.setCurrentRound(game.getCurrentRound());
        response.setDealerSeat(game.getDealerSeat());

        // 积分
        response.setPlayer1Score(game.getPlayer1Score());
        response.setPlayer2Score(game.getPlayer2Score());
        response.setPlayer3Score(game.getPlayer3Score());
        response.setPlayer4Score(game.getPlayer4Score());

        // 当前局状态
        if (game.getGameStatus() == MahjongGameStatus.PLAYING.getCode()) {
            MahjongRound round = roundMapper.selectCurrentRound(game.getId());
            if (round != null) {
                response.setCurrentRoundData(buildRoundResponse(round, game, userId));
            }
        }

        // 时间戳
        response.setCreatedAt(game.getCreatedAt());
        response.setStartedAt(game.getStartedAt());
        response.setEndedAt(game.getEndedAt());

        return response;
    }

    private void fillPlayerInfo(MahjongGameResponse response, MahjongGame game) {
        if (game.getPlayer1Id() != null) {
            User user = userMapper.selectById(game.getPlayer1Id());
            if (user != null) {
                response.setPlayer1Id(user.getId());
                response.setPlayer1Name(user.getRealName() != null ? user.getRealName() : user.getUsername());
                response.setPlayer1Avatar(user.getAvatar());
            }
        }
        if (game.getPlayer2Id() != null) {
            User user = userMapper.selectById(game.getPlayer2Id());
            if (user != null) {
                response.setPlayer2Id(user.getId());
                response.setPlayer2Name(user.getRealName() != null ? user.getRealName() : user.getUsername());
                response.setPlayer2Avatar(user.getAvatar());
            }
        }
        if (game.getPlayer3Id() != null) {
            User user = userMapper.selectById(game.getPlayer3Id());
            if (user != null) {
                response.setPlayer3Id(user.getId());
                response.setPlayer3Name(user.getRealName() != null ? user.getRealName() : user.getUsername());
                response.setPlayer3Avatar(user.getAvatar());
            }
        }
        if (game.getPlayer4Id() != null) {
            User user = userMapper.selectById(game.getPlayer4Id());
            if (user != null) {
                response.setPlayer4Id(user.getId());
                response.setPlayer4Name(user.getRealName() != null ? user.getRealName() : user.getUsername());
                response.setPlayer4Avatar(user.getAvatar());
            }
        }
    }

    private MahjongGameResponse.MahjongRoundResponse buildRoundResponse(
            MahjongRound round, MahjongGame game, Long userId) {
        MahjongGameResponse.MahjongRoundResponse response = new MahjongGameResponse.MahjongRoundResponse();

        response.setId(round.getId());
        response.setRoundNumber(round.getRoundNumber());
        response.setRoundStatus(round.getRoundStatus());
        response.setDealerSeat(round.getDealerSeat());
        response.setCurrentTurn(round.getCurrentTurn());

        int mySeat = getPlayerSeat(game, userId);
        response.setMySeat(mySeat);

        // 设置我的手牌（只有自己能看到）
        if (mySeat > 0) {
            List<String> myHand = getHandBySeat(round, mySeat);
            response.setMyHand(myHand);
        }

        // 设置所有玩家的明牌和弃牌（公开信息）
        response.setPlayer1Melds(parseMelds(round.getPlayer1Melds()));
        response.setPlayer2Melds(parseMelds(round.getPlayer2Melds()));
        response.setPlayer3Melds(parseMelds(round.getPlayer3Melds()));
        response.setPlayer4Melds(parseMelds(round.getPlayer4Melds()));

        response.setPlayer1Discards(round.getPlayer1Discards());
        response.setPlayer2Discards(round.getPlayer2Discards());
        response.setPlayer3Discards(round.getPlayer3Discards());
        response.setPlayer4Discards(round.getPlayer4Discards());

        // 手牌数量
        response.setPlayer1HandCount(getHandCount(round.getPlayer1Hand()));
        response.setPlayer2HandCount(getHandCount(round.getPlayer2Hand()));
        response.setPlayer3HandCount(getHandCount(round.getPlayer3Hand()));
        response.setPlayer4HandCount(getHandCount(round.getPlayer4Hand()));

        // 牌墙剩余
        response.setWallRemaining(getHandCount(round.getWallTiles()));

        // 可执行操作
        if (mySeat > 0) {
            IMahjongEngine engine = engineCache.get(game.getId());
            if (engine != null) {
                boolean isMyTurn = round.getCurrentTurn() == mySeat;
                List<MahjongActionType> actions = engine.getAvailableActions(mySeat, null, isMyTurn);
                response.setAvailableActions(actions.stream()
                        .map(MahjongActionType::name)
                        .collect(Collectors.toList()));
            }
        }

        // 结算信息
        response.setWinnerSeat(round.getWinnerSeat());
        response.setHuType(round.getHuType());
        response.setFanCount(round.getFanCount());

        return response;
    }

    private List<String> getHandBySeat(MahjongRound round, int seat) {
        switch (seat) {
            case 1: return round.getPlayer1Hand();
            case 2: return round.getPlayer2Hand();
            case 3: return round.getPlayer3Hand();
            case 4: return round.getPlayer4Hand();
            default: return new ArrayList<>();
        }
    }

    private int getHandCount(List<String> tiles) {
        return tiles != null ? tiles.size() : 0;
    }

    @SuppressWarnings("unchecked")
    private List<MahjongGameResponse.MeldInfo> parseMelds(List<Map<String, Object>> melds) {
        List<MahjongGameResponse.MeldInfo> result = new ArrayList<>();
        if (melds == null || melds.isEmpty()) {
            return result;
        }
        for (Map<String, Object> meld : melds) {
            MahjongGameResponse.MeldInfo info = new MahjongGameResponse.MeldInfo();
            info.setType((String) meld.get("type"));
            Object tilesObj = meld.get("tiles");
            if (tilesObj instanceof List) {
                info.setTiles((List<String>) tilesObj);
            }
            Object concealedObj = meld.get("concealed");
            if (concealedObj instanceof Boolean) {
                info.setConcealed((Boolean) concealedObj);
            }
            result.add(info);
        }
        return result;
    }

    /**
     * 更新用户统计数据
     */
    private void updateUserStats(MahjongGame game) {
        try {
            // 获取游戏中所有局的统计
            List<MahjongRound> rounds = roundMapper.selectByGameId(game.getId());

            // 统计每个玩家的数据
            Map<Long, PlayerStatsData> statsMap = new HashMap<>();

            // 初始化玩家统计
            List<Long> playerIds = Arrays.asList(
                    game.getPlayer1Id(), game.getPlayer2Id(),
                    game.getPlayer3Id(), game.getPlayer4Id()
            );

            for (int i = 0; i < playerIds.size(); i++) {
                Long playerId = playerIds.get(i);
                if (playerId != null) {
                    statsMap.put(playerId, new PlayerStatsData());
                }
            }

            // 遍历每一局统计数据
            for (MahjongRound round : rounds) {
                if (round.getRoundStatus() == MahjongRoundStatus.HU.getCode()) {
                    Integer winnerSeat = round.getWinnerSeat();
                    if (winnerSeat != null && winnerSeat >= 1 && winnerSeat <= 4) {
                        Long winnerId = getPlayerIdBySeat(game, winnerSeat);
                        if (winnerId != null && statsMap.containsKey(winnerId)) {
                            PlayerStatsData stats = statsMap.get(winnerId);
                            stats.winCount++;

                            // 检查是否自摸（敲麻和百搭都只能自摸）
                            stats.selfDrawCount++;

                            // 更新最高番数
                            if (round.getFanCount() != null && round.getFanCount() > stats.maxFan) {
                                stats.maxFan = round.getFanCount();
                            }

                            // 检查特殊胡型
                            String huType = round.getHuType();
                            if (huType != null) {
                                if (huType.contains("四百搭")) {
                                    stats.fourWildCount++;
                                }
                                if (huType.contains("无百搭")) {
                                    stats.noWildCount++;
                                }
                            }
                        }
                    }
                }

                // 所有玩家增加局数
                for (Long playerId : statsMap.keySet()) {
                    statsMap.get(playerId).totalRounds++;
                }
            }

            // 更新分数
            updatePlayerScore(statsMap, game.getPlayer1Id(), game.getPlayer1Score());
            updatePlayerScore(statsMap, game.getPlayer2Id(), game.getPlayer2Score());
            updatePlayerScore(statsMap, game.getPlayer3Id(), game.getPlayer3Score());
            updatePlayerScore(statsMap, game.getPlayer4Id(), game.getPlayer4Score());

            // 保存统计数据
            for (Map.Entry<Long, PlayerStatsData> entry : statsMap.entrySet()) {
                Long playerId = entry.getKey();
                PlayerStatsData data = entry.getValue();

                MahjongUserStats userStats = userStatsMapper.selectByUserId(playerId);
                if (userStats == null) {
                    userStats = MahjongUserStats.createDefault(playerId);
                }

                userStats.setTotalGames(userStats.getTotalGames() + 1);
                userStats.setTotalRounds(userStats.getTotalRounds() + data.totalRounds);
                userStats.setWinCount(userStats.getWinCount() + data.winCount);
                userStats.setSelfDrawCount(userStats.getSelfDrawCount() + data.selfDrawCount);
                userStats.setTotalScore(userStats.getTotalScore() + data.scoreChange);
                if (data.maxFan > userStats.getMaxFan()) {
                    userStats.setMaxFan(data.maxFan);
                }
                userStats.setFourWildCount(userStats.getFourWildCount() + data.fourWildCount);
                userStats.setNoWildCount(userStats.getNoWildCount() + data.noWildCount);
                userStats.setUpdatedAt(LocalDateTime.now());

                if (userStats.getId() == null) {
                    userStatsMapper.insert(userStats);
                } else {
                    userStatsMapper.updateById(userStats);
                }
            }

            log.info("更新用户统计完成: gameId={}", game.getId());
        } catch (Exception e) {
            log.error("更新用户统计失败: gameId={}", game.getId(), e);
        }
    }

    private Long getPlayerIdBySeat(MahjongGame game, int seat) {
        switch (seat) {
            case 1: return game.getPlayer1Id();
            case 2: return game.getPlayer2Id();
            case 3: return game.getPlayer3Id();
            case 4: return game.getPlayer4Id();
            default: return null;
        }
    }

    private void updatePlayerScore(Map<Long, PlayerStatsData> statsMap, Long playerId, Integer score) {
        if (playerId != null && statsMap.containsKey(playerId) && score != null) {
            statsMap.get(playerId).scoreChange = score;
        }
    }

    /**
     * 玩家统计数据临时类
     */
    private static class PlayerStatsData {
        int totalRounds = 0;
        int winCount = 0;
        int selfDrawCount = 0;
        int scoreChange = 0;
        int maxFan = 0;
        int fourWildCount = 0;
        int noWildCount = 0;
    }
}
