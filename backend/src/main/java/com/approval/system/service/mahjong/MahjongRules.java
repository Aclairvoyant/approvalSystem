package com.approval.system.service.mahjong;

import com.approval.system.common.enums.MahjongModeEnum;
import lombok.Builder;
import lombok.Getter;

/**
 * 规则参数。默认值见 docs/superpowers/specs/2026-11-05-shanghai-mahjong-rules.md，
 * 要调整规则只改这里的常量即可；每桌唯一可由房主选的是封顶分数。
 * <p>
 * 番值为 0 表示该玩法不算这个番种。
 */
@Getter
@Builder(toBuilder = true)
public final class MahjongRules {

    /** 单个付分人每局最多付的分数，房主没选时用这个；0 表示不封顶 */
    public static final int DEFAULT_CAP_POINTS = 128;
    public static final int MAX_CAP_POINTS = 100000;

    /** 真人轮到自己出牌的限时，超时由系统代打并进入托管 */
    public static final long TURN_TIMEOUT_MS = 20_000L;
    /** 吃碰杠胡的响应限时，超时视为「过」 */
    public static final long CLAIM_TIMEOUT_MS = 10_000L;
    /** 电脑、托管、敲后自动出牌的间隔 */
    public static final long AUTO_ACTION_DELAY_MS = 1_000L;

    private final MahjongModeEnum mode;

    /** 底花 */
    private final int baseFlowers;
    /** 百搭：手里的百搭可以替代任意非花牌 */
    private final boolean wildEnabled;
    /** 敲麻：必须先敲（听牌声明）才能胡 */
    private final boolean qiaoRequired;
    /** 起胡花数（不含底花）：自摸 / 点炮；0 表示不限 */
    private final int selfDrawMinFlowers;
    private final int discardWinMinFlowers;
    /** 无花果：除底花外 0 花时按这么多花算（已含底花），并视为满足起胡；0 表示不启用 */
    private final int wuHuaGuoFlowers;
    /** 一炮多响：几家同时能胡都算胡 */
    private final boolean multipleWinners;

    // 花数
    private final int flowerTileValue;
    private final int suitedPongValue;
    private final int suitedExposedKongValue;
    private final int suitedConcealedKongValue;
    private final int windPongValue;
    private final int windExposedKongValue;
    private final int windConcealedKongValue;
    /** 百搭：胡牌时手里每张百搭算几花 */
    private final int wildInHandValue;

    // 番
    private final int fanQingYiSe;
    private final int fanHunYiSe;
    private final int fanPengPengHu;
    private final int fanMenQing;
    private final int fanGangKai;
    private final int fanDaDiao;
    private final int fanNoWild;
    private final int fanFourWild;
    private final int fanPerHuang;

    /** 本桌封顶，0 表示不封顶 */
    private final int capPoints;

    private static final MahjongRules QIAOMA = MahjongRules.builder()
            .mode(MahjongModeEnum.QIAOMA)
            .baseFlowers(2)
            .wildEnabled(false)
            .qiaoRequired(true)
            .selfDrawMinFlowers(2)
            .discardWinMinFlowers(3)
            .wuHuaGuoFlowers(10)
            .multipleWinners(true)
            .flowerTileValue(1)
            .suitedPongValue(0)
            .suitedExposedKongValue(1)
            .suitedConcealedKongValue(2)
            .windPongValue(1)
            .windExposedKongValue(2)
            .windConcealedKongValue(3)
            .wildInHandValue(0)
            .fanQingYiSe(2)
            .fanHunYiSe(1)
            .fanPengPengHu(1)
            .fanMenQing(1)
            .fanGangKai(1)
            .fanDaDiao(1)
            .fanNoWild(0)
            .fanFourWild(0)
            .fanPerHuang(1)
            .capPoints(DEFAULT_CAP_POINTS)
            .build();

    private static final MahjongRules BAIDA = MahjongRules.builder()
            .mode(MahjongModeEnum.BAIDA)
            .baseFlowers(1)
            .wildEnabled(true)
            .qiaoRequired(false)
            .selfDrawMinFlowers(0)
            .discardWinMinFlowers(0)
            .wuHuaGuoFlowers(0)
            .multipleWinners(true)
            .flowerTileValue(1)
            .suitedPongValue(0)
            .suitedExposedKongValue(1)
            .suitedConcealedKongValue(2)
            .windPongValue(1)
            .windExposedKongValue(2)
            .windConcealedKongValue(3)
            .wildInHandValue(1)
            .fanQingYiSe(0)
            .fanHunYiSe(0)
            .fanPengPengHu(0)
            .fanMenQing(1)
            .fanGangKai(1)
            .fanDaDiao(1)
            .fanNoWild(1)
            .fanFourWild(2)
            .fanPerHuang(1)
            .capPoints(DEFAULT_CAP_POINTS)
            .build();

    public static MahjongRules of(MahjongModeEnum mode, int capPoints) {
        MahjongRules base = mode == MahjongModeEnum.QIAOMA ? QIAOMA : BAIDA;
        return capPoints == base.capPoints ? base : base.toBuilder().capPoints(capPoints).build();
    }

    public static MahjongRules of(MahjongRound round) {
        return of(round.getMode(), round.getCapPoints());
    }

    /** 单个付分人实际付的分数 */
    public int capped(long multiple) {
        if (capPoints > 0 && multiple > capPoints) {
            return capPoints;
        }
        return (int) Math.min(multiple, Integer.MAX_VALUE);
    }
}
