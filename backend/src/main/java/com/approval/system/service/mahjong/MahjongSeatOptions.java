package com.approval.system.service.mahjong;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 某个座位当前能做的操作，只放进这个座位自己的私有视角。
 */
@Data
@NoArgsConstructor
public class MahjongSeatOptions {

    /** 轮到自己出牌 */
    private boolean myTurn;
    private List<Integer> discardable = new ArrayList<>();
    /** 自己回合：能自摸；响应阶段：能接炮胡 */
    private boolean canHu;
    /** 自己回合能暗杠 / 加杠的牌 */
    private List<Integer> kongTiles = new ArrayList<>();
    /** 敲麻：打出哪张可以敲，以及敲后听哪些牌 */
    private List<QiaoOption> qiaoOptions = new ArrayList<>();

    /** 别人刚出的牌，等自己决定 */
    private boolean claimPending;
    private Integer claimTile;
    private boolean canPong;
    private boolean canKong;
    private List<Integer> chiStarts = new ArrayList<>();

    public record QiaoOption(int tile, List<Integer> waits) {
    }
}
