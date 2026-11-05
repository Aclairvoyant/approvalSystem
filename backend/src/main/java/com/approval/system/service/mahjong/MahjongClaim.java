package com.approval.system.service.mahjong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 别人出牌后，某个座位能做的选择和他的回应。只存在服务端状态里，不能整条发给其他人
 * （能碰就说明手里有一对）。
 */
@Data
@NoArgsConstructor
public class MahjongClaim {

    private int seat;
    private boolean hu;
    private boolean pong;
    private boolean kong;
    /** 能吃的顺子，记最小那张 */
    private List<Integer> chiStarts = new ArrayList<>();
    /** null 表示还没回应 */
    private MahjongActionType response;
    private Integer responseChiStart;

    public MahjongClaim(int seat) {
        this.seat = seat;
    }

    @JsonIgnore
    public boolean hasOption() {
        return hu || pong || kong || !chiStarts.isEmpty();
    }

    @JsonIgnore
    public boolean isPending() {
        return response == null;
    }
}
