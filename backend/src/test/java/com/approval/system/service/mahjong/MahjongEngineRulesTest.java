package com.approval.system.service.mahjong;

import com.approval.system.common.enums.MahjongModeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.approval.system.service.mahjong.MahjongTestSupport.flowers;
import static com.approval.system.service.mahjong.MahjongTestSupport.hand;
import static com.approval.system.service.mahjong.MahjongTestSupport.meld;
import static com.approval.system.service.mahjong.MahjongTestSupport.round;
import static com.approval.system.service.mahjong.MahjongTestSupport.tile;
import static com.approval.system.service.mahjong.MahjongTestSupport.tiles;
import static com.approval.system.service.mahjong.MahjongTestSupport.turnWithDraw;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 胡牌条件、敲、百搭、出牌顺序、补花和杠的规则。
 */
class MahjongEngineRulesTest {

    private static final MahjongAction HU = MahjongAction.of(MahjongActionType.HU);
    private static final MahjongAction PASS = MahjongAction.of(MahjongActionType.PASS);
    private static final MahjongAction PONG = MahjongAction.of(MahjongActionType.PONG);
    /** 13 张互不相关的牌，不含 5 万（百搭测试里的百搭） */
    private static final String SEAT0_FILLER = "147m 147s 28p 234z 9s 9p";
    /** 13 张互不相关的牌，不含 9 筒和字牌 */
    private static final String OTHER_FILLER = "147m 147s 147p 89m 6s 6p";

    // ------------------------------------------------------------ 敲麻

    @Test
    void qiaomaCannotWinWithoutQiao() {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 4, "9s9s9s9s");
        turnWithDraw(round, 0, "123m 456m 789s 111z 5p", "5p");
        flowers(round, 0, "1f 2f");

        assertThat(MahjongEngine.options(round, 0).isCanHu()).isFalse();
        assertThatThrownBy(() -> MahjongEngine.apply(round, 0, HU))
                .isInstanceOf(MahjongRuleException.class)
                .hasMessage("现在不能胡");

        round.player(0).setQiao(true);
        assertThat(MahjongEngine.options(round, 0).isCanHu()).isTrue();
    }

    @Test
    void qiaoRequiresTenpaiAndLocksTheHand() {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 2, "6s 7s 8s 9s");
        turnWithDraw(round, 0, "123m 456m 789s 111z 5p", "9p");
        hand(round, 1, "147m 147s 258p 234z 9m");

        MahjongSeatOptions options = MahjongEngine.options(round, 0);
        assertThat(options.getQiaoOptions())
                .extracting(MahjongSeatOptions.QiaoOption::tile)
                .containsExactly(tile("5p"), tile("9p"));
        assertThatThrownBy(() -> MahjongEngine.apply(round, 0, MahjongAction.qiao(tile("1m"))))
                .hasMessage("打出这张后还没听牌，不能敲");

        MahjongEngine.apply(round, 0, MahjongAction.qiao(tile("9p")));

        assertThat(round.player(0).isQiao()).isTrue();
        // 对方摸 6 条后打出，敲了的一方自动摸 7 条，不能换牌
        MahjongEngine.apply(round, 1, MahjongAction.discard(tile("6s")));
        assertThat(round.getTurn()).isZero();
        assertThat(MahjongEngine.isAutomaticTurn(round)).isTrue();
        assertThatThrownBy(() -> MahjongEngine.apply(round, 0, MahjongAction.discard(tile("1m"))))
                .hasMessage("敲了之后不能换牌，只能打出摸到的牌");
        MahjongEngine.playAutomaticTurn(round);
        assertThat(round.player(0).getDiscards()).endsWith(tile("7s"));
        assertThat(round.player(0).getHand()).containsExactlyInAnyOrderElementsOf(tiles("123m 456m 789s 111z 5p"));
    }

    @Test
    void qiaomaSelfDrawNeedsTwoFlowersBesidesBase() {
        MahjongRound round = qiaoReadyRound("1f");
        assertThat(MahjongEngine.options(round, 0).isCanHu()).isFalse();

        MahjongRound enough = qiaoReadyRound("1f 2f");
        assertThat(MahjongEngine.options(enough, 0).isCanHu()).isTrue();
        MahjongEngine.apply(enough, 0, HU);
        MahjongWin win = enough.getResult().getWins().get(0);
        assertThat(win.getHandFlowers()).isEqualTo(2);
        assertThat(win.getFlowers()).isEqualTo(4);
    }

    @Test
    void qiaomaWuHuaGuoCountsTenFlowers() {
        MahjongRound round = qiaoReadyRound("");
        assertThat(MahjongEngine.options(round, 0).isCanHu()).isTrue();

        MahjongEngine.apply(round, 0, HU);

        MahjongWin win = round.getResult().getWins().get(0);
        assertThat(win.isWuHuaGuo()).isTrue();
        assertThat(win.getFlowers()).isEqualTo(10);
        // 门清 1 番：10 × 2 = 20，三家各付
        assertThat(win.getMultiple()).isEqualTo(20);
        assertThat(round.getResult().getDeltas()).containsExactly(60, -20, -20, -20);
    }

    @Test
    void qiaomaDiscardWinNeedsThreeFlowers() {
        MahjongRound short2 = discardToQiaoPlayer("1f 2f");
        assertThat(short2.getPhase()).isNotEqualTo(MahjongPhase.FINISHED);
        assertThat(short2.getTurn()).isEqualTo(2);

        MahjongRound enough = discardToQiaoPlayer("1f 2f 3f");
        // 敲后有胡必胡：自动接炮
        assertThat(enough.isFinished()).isTrue();
        MahjongRoundResult result = enough.getResult();
        assertThat(result.isSelfDraw()).isFalse();
        assertThat(result.getDiscarder()).isZero();
        assertThat(result.getWins()).extracting(MahjongWin::getSeat).containsExactly(1);
    }

    /** 0 号已敲，听 5 筒，手里是门清平胡；摸到 5 筒 */
    private static MahjongRound qiaoReadyRound(String flowerTiles) {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 4, "9s9s9s9s");
        turnWithDraw(round, 0, "123m 456m 789s 234p 5p", "5p");
        if (!flowerTiles.isBlank()) {
            flowers(round, 0, flowerTiles);
        }
        round.player(0).setQiao(true);
        return round;
    }

    /** 1 号已敲听 5 筒，0 号打出 5 筒 */
    private static MahjongRound discardToQiaoPlayer(String flowerTiles) {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 4, "1z 2z 3z 4z");
        turnWithDraw(round, 0, "147m 147s 28p 369m 9s 4z", "5p");
        hand(round, 1, "123m 456m 789s 234p 5p");
        flowers(round, 1, flowerTiles);
        round.player(1).setQiao(true);
        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("5p")));
        if (!round.isFinished() && MahjongEngine.isAutomaticTurn(round)) {
            // 1 号没胡：自动摸打
            MahjongEngine.playAutomaticTurn(round);
        }
        return round;
    }

    // ------------------------------------------------------------ 百搭

    @Test
    void baidaWildSubstitutesForSelfDraw() {
        MahjongRound round = baidaRound(4);
        // 两张 5 万是百搭：一张补成 456 筒
        turnWithDraw(round, 0, "123m 789s 111z 46p 99p", "5m");

        assertThat(MahjongEngine.options(round, 0).isCanHu()).isTrue();
        MahjongEngine.apply(round, 0, HU);

        MahjongWin win = round.getResult().getWins().get(0);
        // 底花 1 + 东风暗刻 1 + 百搭 1 = 3 花；门清 1 番
        assertThat(win.getFlowers()).isEqualTo(3);
        assertThat(win.getFanItems()).extracting(MahjongScoreItem::getName).containsExactly("门清");
        assertThat(win.getMultiple()).isEqualTo(6);
    }

    @Test
    void baidaHoldingWildCanOnlyWinBySelfDraw() {
        MahjongRound withWild = baidaRound(4);
        turnWithDraw(withWild, 0, SEAT0_FILLER, "6p");
        hand(withWild, 1, "123m 789s 111z 99p 4p 5m");
        MahjongEngine.apply(withWild, 0, MahjongAction.discard(tile("6p")));
        // 4 筒 + 百搭 + 6 筒本来能成顺子，但手里有百搭不能接炮：没人要这张牌，轮到 1 号摸牌
        assertThat(withWild.getPhase()).isEqualTo(MahjongPhase.ACTION);
        assertThat(withWild.getTurn()).isEqualTo(1);
        assertThat(MahjongEngine.options(withWild, 1).isCanHu()).isFalse();

        MahjongRound noWild = baidaRound(4);
        turnWithDraw(noWild, 0, SEAT0_FILLER, "6p");
        hand(noWild, 1, "123m 789s 111z 99p 45p");
        MahjongEngine.apply(noWild, 0, MahjongAction.discard(tile("6p")));
        assertThat(MahjongEngine.options(noWild, 1).isCanHu()).isTrue();
    }

    @Test
    void fourWildsWinImmediately() {
        MahjongRound round = baidaRound(4);
        round.setWall(tiles("5m 9s 9s"));
        hand(round, 0, "555m 123s 456s 789s 1p");
        turnWithDraw(round, 3, "147m 258s 369p 234z 9m", "1z");

        MahjongEngine.apply(round, 3, MahjongAction.discard(tile("1z")));

        assertThat(round.isFinished()).isTrue();
        MahjongWin win = round.getResult().getWins().get(0);
        assertThat(win.getSeat()).isZero();
        assertThat(win.isFourWild()).isTrue();
        assertThat(win.getFan()).isEqualTo(2);
        // 底花 1 + 四张百搭 4 = 5 花，2 番：5 × 4 = 20，三家各付
        assertThat(win.getFlowers()).isEqualTo(5);
        assertThat(round.getResult().getDeltas()).containsExactly(60, -20, -20, -20);
    }

    @Test
    void baidaIndicatorDecidesWild() {
        assertThat(MahjongTiles.wildAfter(tile("3m"))).isEqualTo(tile("4m"));
        assertThat(MahjongTiles.wildAfter(tile("9p"))).isEqualTo(tile("1p"));
        assertThat(MahjongTiles.wildAfter(tile("4z"))).isEqualTo(tile("1z"));
        assertThat(MahjongTiles.wildAfter(tile("2z"))).isEqualTo(tile("3z"));
        // 翻到花或中发白时东风为百搭
        assertThat(MahjongTiles.wildAfter(tile("5z"))).isEqualTo(tile("1z"));
        assertThat(MahjongTiles.wildAfter(tile("3f"))).isEqualTo(tile("1z"));
    }

    // ------------------------------------------------------------ 发牌、顺序、补花、杠

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void dealGivesDealerFourteenAndPlaysInSeatOrder(int seatCount) {
        MahjongRound round = MahjongEngine.deal(MahjongModeEnum.QIAOMA, seatCount, 1 % seatCount, 1, 0,
                MahjongRules.DEFAULT_CAP_POINTS, new boolean[seatCount], new java.util.Random(7));
        int dealer = 1 % seatCount;
        assertThat(round.getTurn()).isEqualTo(dealer);
        for (MahjongPlayer player : round.getPlayers()) {
            int expected = player.getSeat() == dealer ? 14 : 13;
            assertThat(player.getHand()).hasSize(expected).noneMatch(MahjongTiles::isFlower);
        }
        assertThat(MahjongTestSupport.totalTiles(round)).isEqualTo(MahjongTiles.TOTAL_TILES);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void turnPassesToNextSeatWhenNobodyClaims(int seatCount) {
        // 每人摸一张东风打一张东风，谁都碰不了（手里没有东风），轮转一圈
        MahjongRound round = round(MahjongModeEnum.QIAOMA, seatCount, "1z 1z 1z 1z 2z 2z 2z 2z");
        String filler = "147m 147s 147p 89m 9s 9p";
        for (int seat = 0; seat < seatCount; seat++) {
            hand(round, seat, filler);
        }
        turnWithDraw(round, 0, filler, "3z");
        List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < seatCount + 1; i++) {
            order.add(round.getTurn());
            MahjongEngine.apply(round, round.getTurn(), MahjongAction.discard(round.getDrawnTile()));
        }
        List<Integer> expected = new java.util.ArrayList<>();
        for (int i = 0; i < seatCount + 1; i++) {
            expected.add(i % seatCount);
        }
        assertThat(order).containsExactlyElementsOf(expected);
    }

    @Test
    void drawnFlowerIsShownAndReplacedFromTheTail() {
        MahjongRound round = round(MahjongModeEnum.BAIDA, 2, "1f 2z 3z 9s");
        round.setWild(tile("9p"));
        turnWithDraw(round, 0, OTHER_FILLER, "1z");
        hand(round, 1, "258m 258s 258p 36m 36s");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("1z")));

        MahjongPlayer next = round.player(1);
        assertThat(round.getTurn()).isEqualTo(1);
        assertThat(next.getFlowers()).containsExactly(tile("1f"));
        assertThat(round.getDrawnTile()).isEqualTo(tile("9s"));
        assertThat(round.isReplacementDraw()).isTrue();
        assertThat(round.getWall()).containsExactlyElementsOf(tiles("2z 3z"));
        List<MahjongEvent> events = round.drainEvents();
        assertThat(events).extracting(MahjongEvent::getType)
                .containsSubsequence(MahjongEvent.DISCARD, MahjongEvent.DRAW, MahjongEvent.FLOWER, MahjongEvent.DRAW);
        assertThat(events).filteredOn(event -> event.getType().equals(MahjongEvent.DRAW))
                .allMatch(MahjongEvent::isHidden);
    }

    @Test
    void cannotDiscardTheTileJustPonged() {
        MahjongRound round = baidaRound(4);
        turnWithDraw(round, 0, SEAT0_FILLER, "6p");
        // 手里有三张 6 筒：碰了还剩一张，也不能马上打
        hand(round, 2, "666p 123m 789s 1z 3z 4z 9m");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("6p")));
        MahjongEngine.apply(round, 2, PONG);

        assertThat(round.getTurn()).isEqualTo(2);
        assertThat(round.isJustClaimed()).isTrue();
        assertThat(MahjongEngine.options(round, 2).getKongTiles()).isEmpty();
        assertThatThrownBy(() -> MahjongEngine.apply(round, 2, MahjongAction.discard(tile("6p"))))
                .hasMessage("吃碰后不能马上打出同一种牌");
        MahjongEngine.apply(round, 2, MahjongAction.discard(tile("9m")));
        assertThat(round.getTurn()).isEqualTo(3);
    }

    @Test
    void qiaoPlayerAutoAddsKongWithDrawnTile() {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 2, "3s 8m 9p");
        turnWithDraw(round, 0, OTHER_FILLER, "2z");
        hand(round, 1, "123m 456m 99p 78p");
        meld(round, 1, MahjongMeld.Type.PONG, "333s", 0);
        round.player(1).setQiao(true);

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("2z")));
        assertThat(round.getTurn()).isEqualTo(1);
        MahjongEngine.playAutomaticTurn(round);

        // 摸到 3 条自动加杠，从牌尾补 9 筒，然后自动打出
        assertThat(round.player(1).getMelds().get(0).getType()).isEqualTo(MahjongMeld.Type.JIA_GANG);
        assertThat(round.getDrawnTile()).isEqualTo(tile("9p"));
        assertThat(round.isReplacementDraw()).isTrue();
        MahjongEngine.playAutomaticTurn(round);
        assertThat(round.player(1).getDiscards()).containsExactly(tile("9p"));
    }

    @Test
    void concealedKongDrawsReplacement() {
        MahjongRound round = baidaRound(2);
        round.setWall(tiles("1s 2s 7p"));
        turnWithDraw(round, 0, "1111m 456s 789s 24p 9p", "3p");

        assertThat(MahjongEngine.options(round, 0).getKongTiles()).containsExactly(tile("1m"));
        MahjongEngine.apply(round, 0, MahjongAction.kong(tile("1m")));

        assertThat(round.player(0).getMelds()).hasSize(1);
        assertThat(round.player(0).getMelds().get(0).getType()).isEqualTo(MahjongMeld.Type.AN_GANG);
        assertThat(round.getDrawnTile()).isEqualTo(tile("7p"));
        // 7 筒补进来，234 筒缺 4，不能胡；暗杠不破门清
        assertThat(round.player(0).getMelds().get(0).isOpen()).isFalse();
        assertThat(round.getPhase()).isEqualTo(MahjongPhase.ACTION);
    }

    @Test
    void wallRunningOutIsDrawGame() {
        MahjongRound round = round(MahjongModeEnum.QIAOMA, 2, "");
        turnWithDraw(round, 0, OTHER_FILLER, "2z");
        hand(round, 1, "258m 258s 258p 36m 36s");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("2z")));

        assertThat(round.isFinished()).isTrue();
        assertThat(round.getResult().isDrawGame()).isTrue();
        assertThat(round.getResult().getDeltas()).containsExactly(0, 0);
    }

    @Test
    void humanTimeoutTurnsOnAutoPlay() {
        MahjongRound round = round(MahjongModeEnum.BAIDA, 2, "9s 9s");
        round.setWild(tile("9p"));
        turnWithDraw(round, 0, OTHER_FILLER, "1z");
        // 1 号只有字牌和 9 条、8 筒：0 号打什么都吃碰不了
        hand(round, 1, "222z 333z 444z 99s 88p");

        assertThat(MahjongEngine.isWaitingForHuman(round)).isTrue();
        MahjongEngine.timeout(round);

        assertThat(round.player(0).isAutoPlay()).isTrue();
        assertThat(round.getTurn()).isEqualTo(1);
        // 托管的人再手动操作就取消托管
        MahjongEngine.apply(round, 0, MahjongAction.of(MahjongActionType.CANCEL_AUTO));
        assertThat(round.player(0).isAutoPlay()).isFalse();
    }

    @Test
    void passingClaimMovesToNextSeat() {
        MahjongRound round = baidaRound(3);
        turnWithDraw(round, 0, SEAT0_FILLER, "6p");
        hand(round, 2, "66p 123m 789s 1z 3z 4z 9m 9p");

        MahjongEngine.apply(round, 0, MahjongAction.discard(tile("6p")));
        assertThat(round.getPhase()).isEqualTo(MahjongPhase.CLAIM);
        assertThatThrownBy(() -> MahjongEngine.apply(round, 1, PASS))
                .hasMessage("现在没有需要你选择的牌");
        MahjongEngine.apply(round, 2, PASS);

        assertThat(round.getPhase()).isEqualTo(MahjongPhase.ACTION);
        assertThat(round.getTurn()).isEqualTo(1);
    }

    private static MahjongRound baidaRound(int seatCount) {
        MahjongRound round = round(MahjongModeEnum.BAIDA, seatCount, "1s 2s 3s 4s 5s 6s 7s 8s");
        round.setIndicator(tile("4m"));
        round.setWild(tile("5m"));
        return round;
    }
}
