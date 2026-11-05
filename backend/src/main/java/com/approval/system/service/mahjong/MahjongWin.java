package com.approval.system.service.mahjong;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个胡家的计分明细。
 */
@Data
@NoArgsConstructor
public class MahjongWin {

    private int seat;
    private boolean selfDraw;
    private int winningTile;
    /** 除底花外的花数（起胡看这个） */
    private int handFlowers;
    /** 计分用的总花数（含底花；无花果时为固定值） */
    private int flowers;
    private List<MahjongScoreItem> flowerItems = new ArrayList<>();
    private int fan;
    private List<MahjongScoreItem> fanItems = new ArrayList<>();
    /** 倍数 = 花数 × 2^番 */
    private long multiple;
    /** 每个付分人付的分数（已封顶） */
    private int payEach;
    private List<Integer> payers = new ArrayList<>();
    private boolean fourWild;
    private boolean wuHuaGuo;
}
