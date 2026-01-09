package com.approval.system.entity;

import com.approval.system.common.enums.MahjongTileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 麻将牌类（非数据库实体，用于游戏逻辑）
 * 牌的编码格式：{数字}{类型}，如 "1WAN", "5TONG", "DONG", "ZHONG", "CHUN"
 */
@Data
@NoArgsConstructor
@Builder
public class MahjongTile implements Comparable<MahjongTile> {

    /** 牌类型 */
    private MahjongTileType type;

    /** 牌的数值（万筒条: 1-9, 风牌: 1=东2=南3=西4=北, 箭牌: 1=中2=发3=白, 花牌: 1-8） */
    private int number;

    /** 是否是百搭牌 */
    private boolean isWild;

    /**
     * 全参构造函数
     */
    public MahjongTile(MahjongTileType type, int number, boolean isWild) {
        this.type = type;
        this.number = number;
        this.isWild = isWild;
    }

    /**
     * 简化构造函数（默认非百搭）
     */
    public MahjongTile(MahjongTileType type, int number) {
        this(type, number, false);
    }

    // ========== 静态常量：风牌 ==========
    public static final int FENG_DONG = 1;  // 东
    public static final int FENG_NAN = 2;   // 南
    public static final int FENG_XI = 3;    // 西
    public static final int FENG_BEI = 4;   // 北

    // ========== 静态常量：箭牌 ==========
    public static final int JIAN_ZHONG = 1; // 中
    public static final int JIAN_FA = 2;    // 发
    public static final int JIAN_BAI = 3;   // 白

    // ========== 静态常量：花牌 ==========
    public static final int HUA_CHUN = 1;   // 春
    public static final int HUA_XIA = 2;    // 夏
    public static final int HUA_QIU = 3;    // 秋
    public static final int HUA_DONG = 4;   // 冬
    public static final int HUA_MEI = 5;    // 梅
    public static final int HUA_LAN = 6;    // 兰
    public static final int HUA_ZHU = 7;    // 竹
    public static final int HUA_JU = 8;     // 菊

    /**
     * 从字符串编码解析麻将牌
     * 格式示例: "1WAN", "9TONG", "5TIAO", "DONG", "ZHONG", "CHUN"
     */
    public static MahjongTile fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }

        code = code.toUpperCase();

        // 万筒条数字牌
        if (code.matches("\\d(WAN|TONG|TIAO)")) {
            int num = Character.getNumericValue(code.charAt(0));
            String typeStr = code.substring(1);
            MahjongTileType type = null;
            switch (typeStr) {
                case "WAN": type = MahjongTileType.WAN; break;
                case "TONG": type = MahjongTileType.TONG; break;
                case "TIAO": type = MahjongTileType.TIAO; break;
            }
            return new MahjongTile(type, num, false);
        }

        // 风牌
        switch (code) {
            case "DONG": return new MahjongTile(MahjongTileType.FENG, FENG_DONG, false);
            case "NAN": return new MahjongTile(MahjongTileType.FENG, FENG_NAN, false);
            case "XI": return new MahjongTile(MahjongTileType.FENG, FENG_XI, false);
            case "BEI": return new MahjongTile(MahjongTileType.FENG, FENG_BEI, false);
        }

        // 箭牌
        switch (code) {
            case "ZHONG": return new MahjongTile(MahjongTileType.JIAN, JIAN_ZHONG, false);
            case "FA": return new MahjongTile(MahjongTileType.JIAN, JIAN_FA, false);
            case "BAI": return new MahjongTile(MahjongTileType.JIAN, JIAN_BAI, false);
        }

        // 花牌
        switch (code) {
            case "CHUN": return new MahjongTile(MahjongTileType.HUA, HUA_CHUN, false);
            case "XIA": return new MahjongTile(MahjongTileType.HUA, HUA_XIA, false);
            case "QIU": return new MahjongTile(MahjongTileType.HUA, HUA_QIU, false);
            case "DONGHUA": return new MahjongTile(MahjongTileType.HUA, HUA_DONG, false);
            case "MEI": return new MahjongTile(MahjongTileType.HUA, HUA_MEI, false);
            case "LAN": return new MahjongTile(MahjongTileType.HUA, HUA_LAN, false);
            case "ZHU": return new MahjongTile(MahjongTileType.HUA, HUA_ZHU, false);
            case "JU": return new MahjongTile(MahjongTileType.HUA, HUA_JU, false);
        }

        return null;
    }

    /**
     * 转换为字符串编码
     */
    public String toCode() {
        if (type == null) return "";

        switch (type) {
            case WAN:
                return number + "WAN";
            case TONG:
                return number + "TONG";
            case TIAO:
                return number + "TIAO";
            case FENG:
                switch (number) {
                    case FENG_DONG: return "DONG";
                    case FENG_NAN: return "NAN";
                    case FENG_XI: return "XI";
                    case FENG_BEI: return "BEI";
                }
                break;
            case JIAN:
                switch (number) {
                    case JIAN_ZHONG: return "ZHONG";
                    case JIAN_FA: return "FA";
                    case JIAN_BAI: return "BAI";
                }
                break;
            case HUA:
                switch (number) {
                    case HUA_CHUN: return "CHUN";
                    case HUA_XIA: return "XIA";
                    case HUA_QIU: return "QIU";
                    case HUA_DONG: return "DONGHUA";
                    case HUA_MEI: return "MEI";
                    case HUA_LAN: return "LAN";
                    case HUA_ZHU: return "ZHU";
                    case HUA_JU: return "JU";
                }
                break;
        }
        return "";
    }

    /**
     * 获取中文显示名称
     */
    public String getDisplayName() {
        if (type == null) return "";

        switch (type) {
            case WAN:
                return number + "万";
            case TONG:
                return number + "筒";
            case TIAO:
                return number + "条";
            case FENG:
                switch (number) {
                    case FENG_DONG: return "东";
                    case FENG_NAN: return "南";
                    case FENG_XI: return "西";
                    case FENG_BEI: return "北";
                }
                break;
            case JIAN:
                switch (number) {
                    case JIAN_ZHONG: return "中";
                    case JIAN_FA: return "发";
                    case JIAN_BAI: return "白";
                }
                break;
            case HUA:
                switch (number) {
                    case HUA_CHUN: return "春";
                    case HUA_XIA: return "夏";
                    case HUA_QIU: return "秋";
                    case HUA_DONG: return "冬";
                    case HUA_MEI: return "梅";
                    case HUA_LAN: return "兰";
                    case HUA_ZHU: return "竹";
                    case HUA_JU: return "菊";
                }
                break;
        }
        return "";
    }

    /**
     * 判断是否是数牌（万筒条）
     */
    public boolean isNumberTile() {
        return type != null && type.isNumberTile();
    }

    /**
     * 判断是否是字牌（风牌+箭牌）
     */
    public boolean isHonorTile() {
        return type != null && type.isHonorTile();
    }

    /**
     * 判断是否是花牌
     */
    public boolean isFlowerTile() {
        return type != null && type.isFlowerTile();
    }

    /**
     * 判断是否是幺九牌（1和9的数牌，以及所有字牌）
     */
    public boolean isTerminalOrHonor() {
        if (isHonorTile()) return true;
        if (isNumberTile()) return number == 1 || number == 9;
        return false;
    }

    /**
     * 获取下一张牌（用于确定百搭牌）
     * 数牌: 1-9循环
     * 风牌: 东-南-西-北循环
     * 箭牌/花牌: 返回东风
     */
    public MahjongTile getNextTile() {
        if (type == null) return null;

        switch (type) {
            case WAN:
            case TONG:
            case TIAO:
                int nextNum = (number % 9) + 1;
                return new MahjongTile(type, nextNum, false);
            case FENG:
                int nextFeng = (number % 4) + 1;
                return new MahjongTile(MahjongTileType.FENG, nextFeng, false);
            case JIAN:
            case HUA:
                // 箭牌和花牌的下一张是东风
                return new MahjongTile(MahjongTileType.FENG, FENG_DONG, false);
        }
        return null;
    }

    /**
     * 判断两张牌是否相同（不考虑百搭状态）
     */
    public boolean isSameTile(MahjongTile other) {
        if (other == null) return false;
        return this.type == other.type && this.number == other.number;
    }

    @Override
    public int compareTo(MahjongTile other) {
        if (other == null) return 1;
        // 先按类型排序
        int typeCompare = this.type.ordinal() - other.type.ordinal();
        if (typeCompare != 0) return typeCompare;
        // 再按数值排序
        return this.number - other.number;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MahjongTile)) return false;
        MahjongTile other = (MahjongTile) obj;
        return this.type == other.type && this.number == other.number;
    }

    @Override
    public int hashCode() {
        return type.ordinal() * 100 + number;
    }

    /**
     * 复制当前牌
     */
    public MahjongTile copy() {
        return new MahjongTile(this.type, this.number, this.isWild);
    }

    @Override
    public String toString() {
        return toCode() + (isWild ? "(百搭)" : "");
    }
}
