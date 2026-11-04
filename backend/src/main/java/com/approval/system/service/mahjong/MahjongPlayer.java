package com.approval.system.service.mahjong;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一局里某个座位的牌面。hand 是暗牌，只能发给座位本人。
 */
@Data
@NoArgsConstructor
public class MahjongPlayer {

    private int seat;
    private boolean bot;
    private List<Integer> hand = new ArrayList<>();
    private List<MahjongMeld> melds = new ArrayList<>();
    private List<Integer> flowers = new ArrayList<>();
    private List<Integer> discards = new ArrayList<>();
    /** 敲麻：已经敲（听牌声明），手牌锁定，由系统自动摸打 */
    private boolean qiao;
    /** 托管：真人超时后由系统代打，玩家任意操作即取消 */
    private boolean autoPlay;

    public MahjongPlayer(int seat, boolean bot) {
        this.seat = seat;
        this.bot = bot;
    }
}
