package com.approval.system.service.mahjong;

import com.approval.system.common.enums.MahjongModeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.approval.system.service.mahjong.MahjongTestSupport.tile;
import static com.approval.system.service.mahjong.MahjongTestSupport.turnWithDraw;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对外视角不泄露隐藏信息；电脑能把整局打完。
 */
class MahjongViewsAndSimulationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 公共视角里允许出现的字段（白名单：以后加字段要先想清楚会不会泄露） */
    private static final Set<String> PUBLIC_ROUND_FIELDS = Set.of("mode", "roundNo", "dealer", "seatCount", "phase",
            "turn", "wallCount", "indicator", "wild", "huangCount", "capPoints", "lastDiscard", "lastDiscardSeat",
            "waitingClaims", "step", "deadline", "seats", "result", "events");
    private static final Set<String> PUBLIC_SEAT_FIELDS = Set.of("seat", "handCount", "holdingDrawn", "melds",
            "flowers", "discards", "qiao", "autoPlay", "hand");

    @Test
    void publicViewHidesWallHandsAndDrawnTiles() throws Exception {
        MahjongRound round = MahjongEngine.deal(MahjongModeEnum.BAIDA, 4, 0, 1, 0,
                MahjongRules.DEFAULT_CAP_POINTS, new boolean[]{false, true, true, true}, new Random(11));
        List<MahjongEvent> events = round.drainEvents();
        // 再让庄家出一张、电脑接着行动，产生摸牌事件
        int discard = MahjongEngine.options(round, 0).getDiscardable().get(0);
        MahjongEngine.apply(round, 0, MahjongAction.discard(discard));
        events.addAll(round.drainEvents());

        JsonNode view = MAPPER.valueToTree(MahjongViews.publicView(round, events));

        assertFieldsWithin(view, PUBLIC_ROUND_FIELDS);
        assertThat(view.get("wallCount").asInt()).isEqualTo(round.getWall().size());
        for (JsonNode seat : view.get("seats")) {
            assertFieldsWithin(seat, PUBLIC_SEAT_FIELDS);
            assertThat(seat.get("hand").isNull()).isTrue();
        }
        for (JsonNode event : view.get("events")) {
            if (event.get("type").asText().equals(MahjongEvent.DRAW)) {
                assertThat(event.get("tile").isNull()).isTrue();
            }
        }
        assertThat(view.toString()).doesNotContain("\"wall\"", "drawnTile", "claims", "chiStarts");
    }

    @Test
    void privateViewContainsOnlyOwnHand() throws Exception {
        MahjongRound round = MahjongEngine.deal(MahjongModeEnum.QIAOMA, 3, 0, 1, 0,
                MahjongRules.DEFAULT_CAP_POINTS, new boolean[]{false, false, false}, new Random(5));

        for (int seat = 0; seat < 3; seat++) {
            JsonNode view = MAPPER.valueToTree(MahjongViews.privateView(round, seat, round.getEvents()));
            List<Integer> shown = new ArrayList<>();
            view.get("hand").forEach(node -> shown.add(node.asInt()));
            if (view.hasNonNull("drawnTile")) {
                shown.add(view.get("drawnTile").asInt());
            }
            assertThat(shown).containsExactlyInAnyOrderElementsOf(round.player(seat).getHand());
            assertThat(view.get("seat").asInt()).isEqualTo(seat);
            assertThat(view.toString()).doesNotContain("\"wall\"", "\"seats\"", "\"players\"");
            for (JsonNode event : view.get("events")) {
                assertThat(event.get("seat").asInt()).isEqualTo(seat);
            }
        }
    }

    @Test
    void afterTheRoundOnlyWinnersShowTheirHands() {
        MahjongRound round = MahjongTestSupport.round(MahjongModeEnum.BAIDA, 3, "1s 2s 3s 4s");
        round.setWild(tile("5m"));
        MahjongTestSupport.meld(round, 2, MahjongMeld.Type.AN_GANG, "9999p", null);
        MahjongTestSupport.hand(round, 2, "123m 456s 1z");
        MahjongTestSupport.hand(round, 1, "123m 789s 111z 99p 46p");
        turnWithDraw(round, 0, "147m 147s 28p 234z 9s 8p", "5p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));
        MahjongEngine.apply(round, 1, MahjongAction.of(MahjongActionType.HU));

        MahjongViews.PublicRound view = MahjongViews.publicView(round, round.drainEvents());
        assertThat(view.getSeats().get(1).getHand()).as("胡家亮牌").isNotEmpty();
        assertThat(view.getSeats().get(0).getHand()).as("放炮者不亮牌").isNull();
        assertThat(view.getSeats().get(2).getHand()).isNull();
        assertThat(view.getSeats().get(2).getMelds().get(0).getTiles()).as("没胡的人暗杠不亮").isNull();
    }

    @Test
    void drawGameRevealsNobody() {
        MahjongRound round = MahjongTestSupport.round(MahjongModeEnum.QIAOMA, 2, "");
        turnWithDraw(round, 0, "147m 147s 147p 89m 6s 6p", "2z");
        MahjongTestSupport.hand(round, 1, "258m 258s 258p 36m 36s");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("2z")));

        assertThat(round.getResult().isDrawGame()).isTrue();
        MahjongViews.publicView(round, List.of()).getSeats()
                .forEach(seat -> assertThat(seat.getHand()).isNull());
    }

    @Test
    void concealedKongIsRevealedOnlyAfterRoundEnds() {
        MahjongRound round = MahjongTestSupport.round(MahjongModeEnum.BAIDA, 2, "1s 2s 9p");
        round.setWild(tile("5m"));
        turnWithDraw(round, 0, "1111m 456s 789s 23p 9p", "4p");
        MahjongEngine.apply(round, 0, MahjongAction.kong(tile("1m")));

        MahjongViews.PublicMeld meld = MahjongViews.publicView(round, round.drainEvents()).getSeats().get(0).getMelds().get(0);
        assertThat(meld.isConcealed()).isTrue();
        assertThat(meld.getTiles()).isNull();
        // 自己能在私有视角里看到暗杠的是哪张，别人看不到
        assertThat(MahjongViews.privateView(round, 0, List.of()).getConcealedKongs()).containsExactly(tile("1m"));
        assertThat(MahjongViews.privateView(round, 1, List.of()).getConcealedKongs()).isEmpty();

        MahjongEngine.apply(round, 0, MahjongAction.of(MahjongActionType.HU));
        MahjongViews.PublicRound finished = MahjongViews.publicView(round, round.drainEvents());
        assertThat(finished.getSeats().get(0).getMelds().get(0).getTiles()).hasSize(4);
        assertThat(finished.getSeats().get(0).getHand()).isNotEmpty();
        assertThat(finished.getResult()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"BAIDA,2", "BAIDA,3", "BAIDA,4", "QIAOMA,2", "QIAOMA,3", "QIAOMA,4"})
    void botsPlayRoundsToTheEnd(MahjongModeEnum mode, int seatCount) {
        int wins = 0;
        for (int seed = 0; seed < 12; seed++) {
            boolean[] bots = new boolean[seatCount];
            java.util.Arrays.fill(bots, true);
            MahjongRound round = MahjongEngine.deal(mode, seatCount, seed % seatCount, 1, seed % 2,
                    MahjongRules.DEFAULT_CAP_POINTS, bots, new Random(seed));
            playToEnd(round);
            MahjongRoundResult result = round.getResult();
            assertThat(result.getDeltas().stream().mapToInt(Integer::intValue).sum()).isZero();
            if (!result.isDrawGame()) {
                wins++;
                assertThat(result.getWins()).isNotEmpty().allSatisfy(win -> {
                    assertThat(win.getMultiple()).isPositive();
                    assertThat(win.getPayers()).isNotEmpty();
                });
            }
        }
        assertThat(wins).as("12 局里电脑至少胡过一次").isPositive();
    }

    @Test
    void humanSeatsThatTimeOutAreAutoPlayedToTheEnd() {
        for (MahjongModeEnum mode : MahjongModeEnum.values()) {
            MahjongRound round = MahjongEngine.deal(mode, 4, 0, 1, 0, MahjongRules.DEFAULT_CAP_POINTS,
                    new boolean[]{false, false, true, true}, new Random(3));
            playToEnd(round);
            assertThat(round.isFinished()).isTrue();
        }
    }

    /** 和服务端调度一样推进：轮到系统就自动行动，否则当作真人超时 */
    private static void playToEnd(MahjongRound round) {
        int steps = 0;
        while (!round.isFinished()) {
            if (MahjongEngine.isAutomaticTurn(round)) {
                MahjongEngine.playAutomaticTurn(round);
            } else {
                assertThat(MahjongEngine.isWaitingForHuman(round)).isTrue();
                MahjongEngine.timeout(round);
            }
            assertThat(MahjongTestSupport.totalTiles(round)).isEqualTo(MahjongTiles.TOTAL_TILES);
            assertThat(++steps).as("一局应在有限步内结束").isLessThan(600);
            round.drainEvents();
        }
    }

    private static void assertFieldsWithin(JsonNode node, Set<String> allowed) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            assertThat(allowed).contains(names.next());
        }
    }
}
