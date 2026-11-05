package com.approval.system.service.mahjong;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一局的结果。
 */
@Data
@NoArgsConstructor
public class MahjongRoundResult {

    /** 荒庄 */
    private boolean drawGame;
    private boolean selfDraw;
    /** 点炮者；自摸和荒庄为 null */
    private Integer discarder;
    private Integer winningTile;
    private List<MahjongWin> wins = new ArrayList<>();
    /** 每个座位本局得分 */
    private List<Integer> deltas = new ArrayList<>();
    /** 每个座位亮出来的花数（花牌 + 杠和风碰），结算页展示用 */
    private List<Integer> seatFlowers = new ArrayList<>();
}
