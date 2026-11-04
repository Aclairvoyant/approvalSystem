package com.approval.system.service.impl;

import com.approval.system.common.enums.GameStatusEnum;
import com.approval.system.common.enums.MahjongModeEnum;
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
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.mahjong.MahjongActionType;
import com.approval.system.service.mahjong.MahjongPhase;
import com.approval.system.service.mahjong.MahjongPlayer;
import com.approval.system.service.mahjong.MahjongRound;
import com.approval.system.service.mahjong.MahjongRules;
import com.approval.system.service.notification.MahjongRoomChangedEvent;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 麻将房间服务：凭房间号加入、座位与电脑、开局与再来一局、只把自己的手牌发给自己、电脑把整局打完。
 * 数据库用一个内存里的「房间表」代替：每次读写都复制一份，模拟事务回滚后不留半截修改。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MahjongServiceImplTest {

    private static final long OWNER = 10L;
    private static final long PARTNER = 20L;
    private static final long STRANGER = 30L;
    private static final long OUTSIDER = 99L;

    @Mock
    private MahjongGameMapper gameMapper;
    @Mock
    private MahjongMoveMapper moveMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private IUserRelationService userRelationService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final List<Object> published = new ArrayList<>();
    private final List<MahjongMove> moves = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private MahjongGame stored;
    private MahjongServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MahjongGame.class);
        TableInfoHelper.initTableInfo(assistant, MahjongMove.class);
    }

    @BeforeEach
    void setUp() {
        service = new MahjongServiceImpl(gameMapper, moveMapper, userMapper, userRelationService,
                published::add, objectMapper);
        service.setClock(clock);
        service.setRandom(new Random(42));

        when(gameMapper.insert(any(MahjongGame.class))).thenAnswer(invocation -> {
            MahjongGame game = invocation.getArgument(0);
            game.setId(1L);
            stored = copy(game);
            return 1;
        });
        when(gameMapper.updateById(any(MahjongGame.class))).thenAnswer(invocation -> {
            stored = copy(invocation.getArgument(0));
            return 1;
        });
        when(gameMapper.selectOne(any())).thenAnswer(invocation -> stored == null ? null : copy(stored));
        when(gameMapper.selectById(anyLong())).thenAnswer(invocation -> stored == null ? null : copy(stored));
        when(gameMapper.selectCount(any())).thenReturn(0L);
        when(moveMapper.insert(any(MahjongMove.class))).thenAnswer(invocation -> {
            moves.add(invocation.getArgument(0));
            return 1;
        });
        when(userMapper.selectBatchIds(any())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            return ids.stream().map(id -> user((Long) id)).toList();
        });
    }

    // ------------------------------------------------------------ 开房与加入

    @Test
    void createRoomSeatsOwnerAtSeatZero() {
        MahjongRoomResponse room = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 4, null));

        assertThat(room.getGameCode()).hasSize(6).containsOnlyDigits();
        assertThat(room.getMode()).isEqualTo("BAIDA");
        assertThat(room.getCapPoints()).isEqualTo(MahjongRules.DEFAULT_CAP_POINTS);
        assertThat(room.getGameStatus()).isEqualTo(GameStatusEnum.WAITING.getCode());
        assertThat(room.getMySeat()).isZero();
        assertThat(room.getSeats()).extracting(MahjongSeatResponse::getType)
                .containsExactly("HUMAN", "EMPTY", "EMPTY", "EMPTY");
        assertThat(room.getSeats().get(0).getUserId()).isEqualTo(OWNER);
    }

    @Test
    void ownerCanChooseCapOrNoCap() {
        MahjongCreateRequest request = create(MahjongModeEnum.QIAOMA, 3, null);
        request.setCapPoints(0);
        assertThat(service.createRoom(OWNER, request).getCapPoints()).isZero();
    }

    @Test
    void invitingDirectlyRequiresMutualRelation() {
        when(userRelationService.isRelated(OWNER, STRANGER)).thenReturn(false);
        assertThatThrownBy(() -> service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 4, STRANGER)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);

        when(userRelationService.isRelated(OWNER, PARTNER)).thenReturn(true);
        MahjongRoomResponse room = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 4, PARTNER));
        assertThat(room.getInvitedUserId()).isEqualTo(PARTNER);
        // 被邀请的对象还没入座也能看到房间
        assertThat(service.getRoom(1L, PARTNER).getMySeat()).isNull();
    }

    @Test
    void anyoneCanJoinByCodeWithoutRelation() {
        String code = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 4, null)).getGameCode();

        MahjongRoomResponse room = service.joinRoom(STRANGER, code);

        assertThat(room.getMySeat()).isEqualTo(1);
        assertThat(room.getSeats().get(1).getUserId()).isEqualTo(STRANGER);
        verify(userRelationService, never()).isRelated(any(), any());
        assertThat(lastEvent().room().getSeats().get(1).getType()).isEqualTo("HUMAN");

        // 已经在房间里再加入，直接回到原来的座位
        assertThat(service.joinRoom(STRANGER, code).getMySeat()).isEqualTo(1);
        assertThat(stored.getSeat2UserId()).isNull();
    }

    @Test
    void joinIsRejectedWhenRoomIsFull() {
        String code = service.createRoom(OWNER, create(MahjongModeEnum.QIAOMA, 3, null)).getGameCode();
        service.setSeat(1L, OWNER, 1, true);
        service.joinRoom(PARTNER, code);

        assertThatThrownBy(() -> service.joinRoom(STRANGER, code))
                .isInstanceOf(BusinessException.class)
                .hasMessage("房间已满");
    }

    @Test
    void joinIsRejectedAfterStartAndForUnknownCode() {
        String code = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 2, null)).getGameCode();
        service.setSeat(1L, OWNER, 1, true);
        service.startGame(1L, OWNER);

        assertThatThrownBy(() -> service.joinRoom(STRANGER, code)).hasMessage("这桌已经开局，不能再加入");
        stored = null;
        assertThatThrownBy(() -> service.joinRoom(STRANGER, "000000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessException.NOT_FOUND);
    }

    @Test
    void onlyOwnerManagesSeatsAndStart() {
        String code = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 3, null)).getGameCode();
        service.joinRoom(PARTNER, code);

        assertThatThrownBy(() -> service.setSeat(1L, PARTNER, 2, true))
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
        assertThatThrownBy(() -> service.startGame(1L, PARTNER))
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
        assertThatThrownBy(() -> service.startGame(1L, OWNER))
                .hasMessage("还有空位，等人加入或设成电脑后再开局");
        assertThatThrownBy(() -> service.setSeat(1L, OWNER, 1, true)).hasMessage("这个座位已经有人了");

        MahjongRoomResponse room = service.setSeat(1L, OWNER, 2, true);
        assertThat(room.getSeats()).extracting(MahjongSeatResponse::getType).containsExactly("HUMAN", "HUMAN", "BOT");

        MahjongRoomResponse started = service.startGame(1L, OWNER);
        assertThat(started.getGameStatus()).isEqualTo(GameStatusEnum.PLAYING.getCode());
        assertThat(started.getRoundNo()).isEqualTo(1);
        assertThat(started.getDealerSeat()).isZero();
        assertThat(started.getRound().getTurn()).isZero();
        assertThat(started.getMe().getSeat()).isZero();
    }

    @Test
    void outsiderCannotViewOrAct() {
        String code = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 2, null)).getGameCode();
        service.joinRoom(PARTNER, code);
        service.startGame(1L, OWNER);

        assertThatThrownBy(() -> service.getRoom(1L, OUTSIDER))
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
        MahjongActionRequest pass = new MahjongActionRequest(MahjongActionType.PASS, null, null);
        assertThatThrownBy(() -> service.act(1L, OUTSIDER, pass))
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
        assertThat(service.isSeated(1L, OUTSIDER)).isFalse();
        assertThat(service.isSeated(1L, PARTNER)).isTrue();

        // 不是自己的回合：规则错误转成 400
        assertThatThrownBy(() -> service.act(1L, PARTNER, new MahjongActionRequest(MahjongActionType.DISCARD,
                11, null)))
                .extracting("code").isEqualTo(BusinessException.BAD_REQUEST);
    }

    // ------------------------------------------------------------ 推送内容

    @Test
    void eachPlayerReceivesOnlyTheirOwnHand() throws Exception {
        String code = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 4, null)).getGameCode();
        service.joinRoom(PARTNER, code);
        service.joinRoom(STRANGER, code);
        service.setSeat(1L, OWNER, 3, true);
        service.startGame(1L, OWNER);

        MahjongRoomChangedEvent event = lastEvent();
        MahjongRound round = storedRound();

        JsonNode room = objectMapper.valueToTree(event.room());
        assertAbsent(room, "me");
        assertAbsent(room, "mySeat");
        assertThat(room.get("round").has("wall")).isFalse();
        for (JsonNode seat : room.get("round").get("seats")) {
            assertThat(seat.get("hand").isNull()).isTrue();
        }

        // 电脑座位不推送；每个真人只拿到自己座位的手牌
        assertThat(event.hands()).containsOnlyKeys(OWNER, PARTNER, STRANGER);
        Map<Long, Integer> seats = Map.of(OWNER, 0, PARTNER, 1, STRANGER, 2);
        event.hands().forEach((userId, hand) -> {
            int seat = seats.get(userId);
            assertThat(hand.getSeat()).isEqualTo(seat);
            List<Integer> shown = new ArrayList<>(hand.getHand());
            if (hand.getDrawnTile() != null) {
                shown.add(hand.getDrawnTile());
            }
            assertThat(shown).containsExactlyInAnyOrderElementsOf(round.player(seat).getHand());
        });
        // 一个人的私有消息里只有一个座位的信息
        JsonNode partnerHand = objectMapper.valueToTree(event.hands().get(PARTNER));
        assertAbsent(partnerHand, "seats");
        assertThat(partnerHand.toString()).doesNotContain("\"wall\"");

        // REST 返回的个人视角同样只有自己的牌
        MahjongRoomResponse ownerView = service.getRoom(1L, OWNER);
        assertThat(ownerView.getMe().getSeat()).isZero();
        assertThat(objectMapper.valueToTree(ownerView).toString()).doesNotContain("\"wall\"");
    }

    // ------------------------------------------------------------ 电脑与轮转

    @Test
    void botsFinishTheRoundAfterOwnerTimesOut() {
        service.createRoom(OWNER, create(MahjongModeEnum.QIAOMA, 4, null));
        for (int seat = 1; seat < 4; seat++) {
            service.setSeat(1L, OWNER, seat, true);
        }
        service.startGame(1L, OWNER);

        playUntilRoundEnds();

        assertThat(stored.getGameStatus()).isEqualTo(GameStatusEnum.FINISHED.getCode());
        MahjongRound round = storedRound();
        assertThat(round.getPhase()).isEqualTo(MahjongPhase.FINISHED);
        assertThat(round.player(0).isAutoPlay()).as("房主超时后进入托管").isTrue();
        List<Integer> totals = service.getRoom(1L, OWNER).getSeats().stream()
                .map(MahjongSeatResponse::getTotalScore).toList();
        assertThat(totals.stream().mapToInt(Integer::intValue).sum()).isZero();
        assertThat(totals).containsExactlyElementsOf(round.getResult().getDeltas());
        assertThat(moves).isNotEmpty();
        assertThat(moves).filteredOn(move -> move.getActionType().equals("DRAW"))
                .allMatch(move -> "{\"hidden\":true}".equals(move.getMoveData()));
        assertThat(service.getRoom(1L, OWNER).getRound().getResult()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void dealerRotatesAndScoresAccumulate(int seatCount) {
        service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, seatCount, null));
        for (int seat = 1; seat < seatCount; seat++) {
            service.setSeat(1L, OWNER, seat, true);
        }
        service.startGame(1L, OWNER);

        List<Integer> dealers = new ArrayList<>();
        List<Integer> firstTurns = new ArrayList<>();
        int[] expectedTotals = new int[seatCount];
        for (int round = 1; round <= seatCount + 1; round++) {
            if (round > 1) {
                service.nextRound(1L, OWNER);
            }
            MahjongRound current = storedRound();
            dealers.add(current.getDealer());
            firstTurns.add(current.getTurn());
            playUntilRoundEnds();
            List<Integer> deltas = storedRound().getResult().getDeltas();
            for (int seat = 0; seat < seatCount; seat++) {
                expectedTotals[seat] += deltas.get(seat);
            }
        }

        List<Integer> expected = new ArrayList<>();
        for (int round = 0; round <= seatCount; round++) {
            expected.add(round % seatCount);
        }
        assertThat(dealers).containsExactlyElementsOf(expected);
        assertThat(firstTurns).containsExactlyElementsOf(expected);
        assertThat(stored.getRoundNo()).isEqualTo(seatCount + 1);
        assertThat(service.getRoom(1L, OWNER).getSeats()).extracting(MahjongSeatResponse::getTotalScore)
                .containsExactlyElementsOf(java.util.Arrays.stream(expectedTotals).boxed().toList());
    }

    @Test
    void drawGameAddsHuangFanToNextRound() throws Exception {
        service.createRoom(OWNER, create(MahjongModeEnum.QIAOMA, 2, null));
        service.setSeat(1L, OWNER, 1, true);
        service.startGame(1L, OWNER);

        // 摆一手：牌墙已空，轮到电脑出牌，谁都要不了 → 荒庄
        MahjongRound round = storedRound();
        round.setWall(new ArrayList<>());
        round.setPhase(MahjongPhase.ACTION);
        round.setTurn(1);
        round.getClaims().clear();
        MahjongPlayer owner = round.player(0);
        owner.setHand(new ArrayList<>(List.of(11, 14, 17, 21, 24, 27, 31, 34, 37, 12, 25, 38, 19)));
        owner.getMelds().clear();
        owner.setQiao(false);
        MahjongPlayer bot = round.player(1);
        bot.setHand(new ArrayList<>(List.of(41, 41, 41, 42, 42, 42, 43, 43, 43, 44, 44, 44, 15, 29)));
        bot.getMelds().clear();
        bot.setQiao(false);
        round.setDrawnTile(29);
        round.setJustClaimed(false);
        round.setDeadline(0);
        stored.setStateData(objectMapper.writeValueAsString(round));

        service.runScheduledStep(1L, stored.getStateVersion());

        assertThat(storedRound().getResult().isDrawGame()).isTrue();
        assertThat(stored.getGameStatus()).isEqualTo(GameStatusEnum.FINISHED.getCode());
        assertThat(stored.getHuangCount()).isEqualTo(1);

        service.nextRound(1L, OWNER);
        assertThat(storedRound().getHuangCount()).isEqualTo(1);
        assertThat(storedRound().getDealer()).isEqualTo(1);
    }

    @Test
    void scheduledStepIgnoresStaleVersionAndEarlyCalls() {
        service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 2, null));
        service.setSeat(1L, OWNER, 1, true);
        service.startGame(1L, OWNER);
        int version = stored.getStateVersion();

        // 还没到房主的出牌限时
        service.runScheduledStep(1L, version);
        assertThat(stored.getStateVersion()).isEqualTo(version);
        // 版本不对
        clock.advance(Duration.ofSeconds(30));
        service.runScheduledStep(1L, version - 1);
        assertThat(stored.getStateVersion()).isEqualTo(version);
        // 到点了：房主超时代打
        service.runScheduledStep(1L, version);
        assertThat(stored.getStateVersion()).isGreaterThan(version);
    }

    @Test
    void cancelAndLeaveOnlyBeforeStart() {
        String code = service.createRoom(OWNER, create(MahjongModeEnum.BAIDA, 3, null)).getGameCode();
        service.joinRoom(PARTNER, code);
        assertThatThrownBy(() -> service.leaveRoom(1L, OWNER)).hasMessage("房主不能离开，可以取消房间");
        service.leaveRoom(1L, PARTNER);
        assertThat(stored.getSeat1UserId()).isNull();
        assertThatThrownBy(() -> service.cancelRoom(1L, PARTNER))
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
        service.cancelRoom(1L, OWNER);
        assertThat(stored.getGameStatus()).isEqualTo(GameStatusEnum.CANCELLED.getCode());
        assertThatThrownBy(() -> service.joinRoom(STRANGER, code)).hasMessage("房间已经取消");
    }

    // ------------------------------------------------------------ 工具

    /** 像调度器一样推进：每次把时钟拨到截止时间之后，执行一步 */
    private void playUntilRoundEnds() {
        for (int i = 0; i < 2000 && GameStatusEnum.PLAYING.getCode().equals(stored.getGameStatus()); i++) {
            clock.advance(Duration.ofSeconds(25));
            service.runScheduledStep(1L, stored.getStateVersion());
        }
        assertThat(stored.getGameStatus()).isEqualTo(GameStatusEnum.FINISHED.getCode());
    }

    private MahjongRound storedRound() {
        try {
            return objectMapper.readValue(stored.getStateData(), MahjongRound.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MahjongRoomChangedEvent lastEvent() {
        for (int i = published.size() - 1; i >= 0; i--) {
            if (published.get(i) instanceof MahjongRoomChangedEvent event) {
                return event;
            }
        }
        throw new AssertionError("没有推送房间事件");
    }

    private static MahjongCreateRequest create(MahjongModeEnum mode, int seats, Long invitee) {
        return MahjongCreateRequest.builder().mode(mode).seatCount(seats).inviteUserId(invitee).build();
    }

    private static MahjongGame copy(MahjongGame source) {
        MahjongGame target = new MahjongGame();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRealName("玩家" + id);
        return user;
    }

    /** 可以手动拨动的时钟 */
    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-05T08:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Shanghai");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static void assertAbsent(JsonNode node, String field) {
        JsonNode value = node.get(field);
        assertThat(value == null || value.isNull()).as(field + " 不应出现").isTrue();
    }
}
