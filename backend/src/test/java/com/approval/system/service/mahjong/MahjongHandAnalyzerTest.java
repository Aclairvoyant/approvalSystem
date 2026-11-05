package com.approval.system.service.mahjong;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.approval.system.service.mahjong.MahjongTestSupport.counts;
import static com.approval.system.service.mahjong.MahjongTestSupport.tiles;
import static org.assertj.core.api.Assertions.assertThat;

class MahjongHandAnalyzerTest {

    @Test
    void standardHandWithFourMeldsAndPair() {
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("123m 456m 789s 111z 55p"), 0, 4)).isTrue();
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("111m 222m 333m 444s 99p"), 0, 4)).isTrue();
    }

    @Test
    void rejectsIncompleteHands() {
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("123m 456m 789s 111z 56p"), 0, 4)).isFalse();
        // 七对不算胡
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("11m 22m 33s 44s 55p 66p 11z"), 0, 4)).isFalse();
        // 风牌不能组顺子
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("123z 456m 789m 123s 55p"), 0, 4)).isFalse();
        // 跨花色不能连成顺子（9 万和 1 条之间隔着不存在的牌）
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("89m 1s 456m 789p 111z 55p"), 0, 4)).isFalse();
    }

    @Test
    void countsExposedMelds() {
        // 已经亮了三副，手里只剩一副加一对
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("456m 55p"), 0, 1)).isTrue();
        // 大吊车：四副都亮了，只剩一对
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("33z"), 0, 0)).isTrue();
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("34z"), 0, 0)).isFalse();
    }

    @Test
    void wildTilesSubstituteAnyTile() {
        // 13 万缺 2 万、5 筒缺一张做将，两张百搭补上
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("13m 456s 789p 111z 5p"), 2, 4)).isTrue();
        // 两张百搭直接做将
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("123m 456m 789m 111z"), 2, 4)).isTrue();
        // 三张百搭自成一副
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("123m 456m 789m 22z"), 3, 4)).isTrue();
        // 边张、嵌张都能补
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("89m 13s 456p 777p 44z"), 2, 4)).isTrue();
        // 百搭再多也补不出张数不对的牌
        assertThat(MahjongHandAnalyzer.isWinningShape(counts("123m 456m 789m 22z"), 2, 4)).isFalse();
    }

    @Test
    void analyzeFindsBestDecomposition() {
        MahjongHandAnalyzer.ShapeInfo shape = MahjongHandAnalyzer.analyze(counts("111222333m 444z 55p"), 0, 4);
        assertThat(shape).isNotNull();
        // 111222333 既能拆成三刻也能拆成三顺，碰碰胡取有利的拆法
        assertThat(shape.allTripletsPossible()).isTrue();
        assertThat(shape.maxWindTriplets()).isEqualTo(1);

        MahjongHandAnalyzer.ShapeInfo sequences = MahjongHandAnalyzer.analyze(counts("123m 456m 789s 111z 55p"), 0, 4);
        assertThat(sequences.allTripletsPossible()).isFalse();

        // 两张北风 + 一张百搭凑成风刻
        MahjongHandAnalyzer.ShapeInfo withWild = MahjongHandAnalyzer.analyze(counts("123m 456m 789s 44z 55p"), 1, 4);
        assertThat(withWild).isNotNull();
        assertThat(withWild.maxWindTriplets()).isEqualTo(1);

        assertThat(MahjongHandAnalyzer.analyze(counts("123m 456m 789s 111z 56p"), 0, 4)).isNull();
    }

    @Test
    void waitsListsEveryCompletingTile() {
        // 九莲宝灯听一到九万
        List<Integer> waits = MahjongHandAnalyzer.waits(counts("1112345678999m"), 0, 4);
        assertThat(waits).containsExactlyElementsOf(tiles("123456789m"));

        assertThat(MahjongHandAnalyzer.waits(counts("123m 456m 789s 111z 5p"), 0, 4))
                .containsExactly(MahjongTestSupport.tile("5p"));
        assertThat(MahjongHandAnalyzer.waits(counts("123m 456m 789s 11z 45p"), 0, 4))
                .containsExactlyElementsOf(tiles("36p"));
    }

    @Test
    void shantenEstimates() {
        assertThat(MahjongHandAnalyzer.shanten(counts("123m 456m 789s 111z 55p"), 0, 4)).isEqualTo(-1);
        assertThat(MahjongHandAnalyzer.shanten(counts("123m 456m 789s 111z 5p"), 0, 4)).isZero();
        assertThat(MahjongHandAnalyzer.shanten(counts("123m 456m 789s 11z 59p"), 0, 4)).isEqualTo(1);
    }
}
