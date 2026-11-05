package com.approval.system.service.mahjong;

import com.approval.system.common.enums.MahjongModeEnum;
import org.junit.jupiter.api.Test;

import static com.approval.system.service.mahjong.MahjongTestSupport.flowers;
import static com.approval.system.service.mahjong.MahjongTestSupport.hand;
import static com.approval.system.service.mahjong.MahjongTestSupport.meld;
import static com.approval.system.service.mahjong.MahjongTestSupport.round;
import static com.approval.system.service.mahjong.MahjongTestSupport.tile;
import static com.approval.system.service.mahjong.MahjongTestSupport.turnWithDraw;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 抢牌优先级（胡 > 杠/碰 > 吃，一炮多响）和计分示例（与规则文档里的例子一致）。
 */
class MahjongClaimAndScoringTest {

    private static final MahjongAction HU = MahjongAction.of(MahjongActionType.HU);
    private static final MahjongAction PASS = MahjongAction.of(MahjongActionType.PASS);
    private static final MahjongAction PONG = MahjongAction.of(MahjongActionType.PONG);
    /** 13 张互不相关的牌，不含 5 万（百搭）和 5 筒 */
    private static final String DISCARDER = "147m 147s 28p 234z 9s 9p";

    // ------------------------------------------------------------ 抢牌

    @Test
    void huBeatsPong() {
        MahjongRound round = baidaRound();
        turnWithDraw(round, 0, DISCARDER, "5p");
        hand(round, 1, "55p 1368s 268m 134z 9m");
        hand(round, 2, "123m 789s 111z 99p 46p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));
        MahjongEngine.apply(round, 1, PONG);
        // 能胡的 2 号还没表态，碰要等
        assertThat(round.getPhase()).isEqualTo(MahjongPhase.CLAIM);

        MahjongEngine.apply(round, 2, HU);

        assertThat(round.isFinished()).isTrue();
        assertThat(round.getResult().getWins()).extracting(MahjongWin::getSeat).containsExactly(2);
        assertThat(round.getResult().getDiscarder()).isZero();
        assertThat(round.player(1).getMelds()).isEmpty();
    }

    @Test
    void pongBeatsChi() {
        MahjongRound round = baidaRound();
        turnWithDraw(round, 0, DISCARDER, "5p");
        hand(round, 1, "46p 1368s 268m 134z 9m");
        hand(round, 2, "55p 123m 789s 134z 89p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));
        assertThat(MahjongEngine.options(round, 1).getChiStarts()).containsExactly(tile("4p"));
        MahjongEngine.apply(round, 1, MahjongAction.chi(tile("4p")));
        assertThat(round.getPhase()).isEqualTo(MahjongPhase.CLAIM);

        MahjongEngine.apply(round, 2, PONG);

        assertThat(round.getTurn()).isEqualTo(2);
        assertThat(round.player(2).getMelds()).extracting(MahjongMeld::getType).containsExactly(MahjongMeld.Type.PONG);
        assertThat(round.player(1).getMelds()).isEmpty();
    }

    @Test
    void chiHappensWhenPongIsPassed() {
        MahjongRound round = baidaRound();
        turnWithDraw(round, 0, DISCARDER, "5p");
        hand(round, 1, "46p 1368s 268m 134z 9m");
        hand(round, 2, "55p 123m 789s 134z 89p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));
        MahjongEngine.apply(round, 1, MahjongAction.chi(tile("4p")));
        MahjongEngine.apply(round, 2, PASS);

        assertThat(round.getTurn()).isEqualTo(1);
        MahjongMeld chi = round.player(1).getMelds().get(0);
        assertThat(chi.getType()).isEqualTo(MahjongMeld.Type.CHI);
        assertThat(chi.getTiles()).containsExactly(tile("4p"), tile("5p"), tile("6p"));
        assertThat(chi.getFromSeat()).isZero();
        // 被吃的牌离开牌河
        assertThat(round.player(0).getDiscards()).isEmpty();
    }

    @Test
    void onlyNextSeatCanChi() {
        MahjongRound round = baidaRound();
        turnWithDraw(round, 0, DISCARDER, "5p");
        hand(round, 2, "46p 1368s 268m 134z 9m");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));

        // 2 号不是下家，不能吃；没人要，轮到 1 号摸牌
        assertThat(round.getPhase()).isEqualTo(MahjongPhase.ACTION);
        assertThat(round.getTurn()).isEqualTo(1);
    }

    @Test
    void oneDiscardCanPayMultipleWinners() {
        MahjongRound round = baidaRound();
        turnWithDraw(round, 0, DISCARDER, "5p");
        hand(round, 1, "123m 789s 111z 99p 46p");
        hand(round, 3, "678m 789s 222z 33p 46p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));
        MahjongEngine.apply(round, 1, HU);
        // 一炮多响：要等所有能胡的人表态
        assertThat(round.getPhase()).isEqualTo(MahjongPhase.CLAIM);
        MahjongEngine.apply(round, 3, HU);

        MahjongRoundResult result = round.getResult();
        assertThat(result.getWins()).extracting(MahjongWin::getSeat).containsExactly(1, 3);
        // 两家都是底花 1 + 风暗刻 1 = 2 花，门清 + 无百搭 2 番：2 × 4 = 8，放炮者分别付
        assertThat(result.getWins()).extracting(MahjongWin::getPayEach).containsExactly(8, 8);
        assertThat(result.getDeltas()).containsExactly(-16, 8, 0, 8);
    }

    @Test
    void winnerMayDeclineAndOtherWinnerStillWins() {
        MahjongRound round = baidaRound();
        turnWithDraw(round, 0, DISCARDER, "5p");
        hand(round, 1, "123m 789s 111z 99p 46p");
        hand(round, 3, "678m 789s 222z 33p 46p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));
        MahjongEngine.apply(round, 3, HU);
        MahjongEngine.apply(round, 1, PASS);

        assertThat(round.getResult().getWins()).extracting(MahjongWin::getSeat).containsExactly(3);
        assertThat(round.getResult().getDeltas()).containsExactly(-8, 0, 0, 8);
    }

    // ------------------------------------------------------------ 计分示例

    @Test
    void qiaomaSelfDrawExample() {
        // 3 张花牌 + 东风碰 1 = 4 花，底花 2，共 6 花；混一色 1 番 → 12，三家各付
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 4, "9s9s9s9s");
        meld(round, 0, MahjongMeld.Type.PONG, "111z", 2);
        flowers(round, 0, "1f 2f 3f");
        turnWithDraw(round, 0, "123m 456m 789m 2z", "2z");
        round.player(0).setQiao(true);

        MahjongEngine.apply(round, 0, HU);

        MahjongWin win = round.getResult().getWins().get(0);
        assertThat(win.getFlowers()).isEqualTo(6);
        assertThat(win.getFanItems()).extracting(MahjongScoreItem::getName).containsExactly("混一色");
        assertThat(win.getMultiple()).isEqualTo(12);
        assertThat(round.getResult().getDeltas()).containsExactly(36, -12, -12, -12);
    }

    @Test
    void qiaomaPureAllTripletsStacksFan() {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 4, "9s9s9s9s");
        flowers(round, 0, "1f 2f 3f");
        turnWithDraw(round, 0, "111m 222m 333m 444m 5m", "5m");
        round.player(0).setQiao(true);

        MahjongEngine.apply(round, 0, HU);

        MahjongWin win = round.getResult().getWins().get(0);
        // 清一色 2 + 碰碰胡 1 + 门清 1 = 4 番；3 + 底花 2 = 5 花 → 80
        assertThat(win.getFanItems()).extracting(MahjongScoreItem::getName)
                .containsExactly("清一色", "碰碰胡", "门清");
        assertThat(win.getMultiple()).isEqualTo(80);
        assertThat(round.getResult().getDeltas()).containsExactly(240, -80, -80, -80);
    }

    @Test
    void qiaomaBigSingleWait() {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 2, "9s9s9s9s");
        meld(round, 0, MahjongMeld.Type.PONG, "222z", 1);
        meld(round, 0, MahjongMeld.Type.PONG, "555s", 1);
        meld(round, 0, MahjongMeld.Type.CHI, "123p", 1);
        meld(round, 0, MahjongMeld.Type.PONG, "999m", 1);
        flowers(round, 0, "1f");
        turnWithDraw(round, 0, "7s", "7s");
        round.player(0).setQiao(true);

        MahjongEngine.apply(round, 0, HU);

        MahjongWin win = round.getResult().getWins().get(0);
        // 花牌 1 + 南风碰 1 = 2 花，加底花共 4；大吊车 1 番
        assertThat(win.getFlowers()).isEqualTo(4);
        assertThat(win.getFanItems()).extracting(MahjongScoreItem::getName).containsExactly("大吊车");
        assertThat(round.getResult().getDeltas()).containsExactly(8, -8);
    }

    @Test
    void baidaDiscardWinExample() {
        // 2 张花牌 + 万字暗杠 2 + 底花 1 = 5 花；无百搭 1 + 门清 1 = 2 番 → 20，放炮者付
        MahjongRound round = baidaDiscardWin(MahjongRules.DEFAULT_CAP_POINTS, 0);

        MahjongWin win = round.getResult().getWins().get(0);
        assertThat(win.getFlowers()).isEqualTo(5);
        assertThat(win.getFanItems()).extracting(MahjongScoreItem::getName).containsExactly("门清", "无百搭");
        assertThat(win.getMultiple()).isEqualTo(20);
        assertThat(round.getResult().getDeltas()).containsExactly(-20, 20, 0, 0);
    }

    @Test
    void capLimitsEachPayer() {
        MahjongRound round = baidaDiscardWin(16, 0);
        assertThat(round.getResult().getWins().get(0).getPayEach()).isEqualTo(16);
        assertThat(round.getResult().getDeltas()).containsExactly(-16, 16, 0, 0);

        MahjongRound uncapped = baidaDiscardWin(0, 3);
        // 不封顶，荒番 3：5 × 2^5 = 160
        assertThat(uncapped.getResult().getWins().get(0).getPayEach()).isEqualTo(160);
    }

    @Test
    void huangFanAddsToFan() {
        MahjongRound round = baidaDiscardWin(MahjongRules.DEFAULT_CAP_POINTS, 1);
        MahjongWin win = round.getResult().getWins().get(0);
        assertThat(win.getFan()).isEqualTo(3);
        assertThat(win.getMultiple()).isEqualTo(40);
    }

    @Test
    void kongReplacementWinIsGangKai() {
        MahjongRound round = baidaRound();
        round.setWall(new java.util.ArrayList<>(MahjongTestSupport.tiles("1s 2s 9p")));
        turnWithDraw(round, 0, "1111m 456s 789s 23p 9p", "4p");

        MahjongEngine.apply(round, 0, MahjongAction.kong(tile("1m")));
        assertThat(MahjongEngine.options(round, 0).isCanHu()).isTrue();
        MahjongEngine.apply(round, 0, HU);

        MahjongWin win = round.getResult().getWins().get(0);
        assertThat(win.getFanItems()).extracting(MahjongScoreItem::getName).containsExactly("门清", "杠开", "无百搭");
        // 万字暗杠 2 + 底花 1 = 3 花，3 番 → 24
        assertThat(win.getMultiple()).isEqualTo(24);
    }

    private static MahjongRound baidaDiscardWin(int capPoints, int huangCount) {
        MahjongRound round = round(MahjongModeEnum.BAIDA, 4, "1s 2s 3s 4s");
        round.setIndicator(tile("8p"));
        round.setWild(tile("9p"));
        round.setCapPoints(capPoints);
        round.setHuangCount(huangCount);
        meld(round, 1, MahjongMeld.Type.AN_GANG, "7777m", null);
        flowers(round, 1, "1f 2f");
        hand(round, 1, "123s 456s 789s 1p");
        turnWithDraw(round, 0, "258m 258s 258p 36m 36s", "1p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("1p")));
        MahjongEngine.apply(round, 1, HU);
        return round;
    }

    private static MahjongRound baidaRound() {
        MahjongRound round = round(MahjongModeEnum.BAIDA, 4, "1s 2s 3s 4s 5s 6s 7s 8s");
        round.setIndicator(tile("4m"));
        round.setWild(tile("5m"));
        return round;
    }
}
