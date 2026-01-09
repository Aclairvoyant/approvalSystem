package com.approval.system.common.enums;

import lombok.Getter;

/**
 * 麻将胡牌类型枚举
 * 包含基础胡型和特殊胡型
 */
@Getter
public enum MahjongHuType {
    // ========== 基础胡型 ==========
    NORMAL("普通胡", 1, "基本胡牌牌型"),

    // ========== 特殊胡型（上海百搭规则）==========
    DA_DIAO_CHE("大吊车", 2, "吃碰杠共4次后单吊自摸"),
    GANG_KAI("杠开", 2, "杠牌/补花后补到的牌胡"),
    PAO_BAI_DA("跑百搭", 3, "任意牌都能胡（听全牌）"),
    WU_BAI_DA("无百搭", 2, "手中无百搭牌时胡"),
    MEN_QING("门清", 2, "无吃碰明杠情况下胡"),
    SI_BAI_DA("四百搭", 4, "抓到四个百搭直接胡"),

    // ========== 常见番型 ==========
    QING_YI_SE("清一色", 3, "全部是同一花色"),
    DUI_DUI_HU("对对胡", 2, "全部是刻子"),
    QI_DUI("七对", 3, "七个对子"),
    QUAN_QIU_REN("全求人", 2, "全部吃碰杠，单吊胡"),
    HUN_YI_SE("混一色", 2, "一种花色加字牌"),

    // ========== 大番型 ==========
    DA_SI_XI("大四喜", 8, "四种风牌都是刻子"),
    XIAO_SI_XI("小四喜", 6, "三种风牌刻子加一对风牌"),
    DA_SAN_YUAN("大三元", 8, "中发白都是刻子"),
    XIAO_SAN_YUAN("小三元", 5, "两种箭牌刻子加一对箭牌"),
    ZI_YI_SE("字一色", 8, "全部是字牌"),
    SHI_SAN_YAO("十三幺", 8, "13种幺九牌各一张加任意一张"),
    JING_HUA_DI("花一色", 1, "有花牌"),

    // ========== 自摸加番 ==========
    ZI_MO("自摸", 1, "自己摸牌胡");

    private final String name;
    private final int fan;
    private final String description;

    MahjongHuType(String name, int fan, String description) {
        this.name = name;
        this.fan = fan;
        this.description = description;
    }

    /**
     * 根据名称获取胡牌类型
     */
    public static MahjongHuType fromName(String name) {
        for (MahjongHuType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return null;
    }
}
