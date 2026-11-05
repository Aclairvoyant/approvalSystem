package com.approval.system.service.mahjong;

import com.approval.system.common.enums.MahjongModeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一局的完整状态，整体序列化成 JSON 存在 mahjong_games.state_data。
 * <p>
 * 包含牌墙和所有人的暗牌，<b>不能直接发给客户端</b>，对外只能经过 {@link MahjongViews}。
 */
@Data
@NoArgsConstructor
public class MahjongRound {

    private MahjongModeEnum mode;
    private int seatCount;
    private int roundNo;
    private int dealer;
    /** 本局带的荒番数 */
    private int huangCount;
    private int capPoints;

    /** 下标 0 是下一张要摸的牌；补花、杠后从末尾补 */
    private List<Integer> wall = new ArrayList<>();
    /** 百搭：引导牌（亮在一边，不再摸）和百搭牌 */
    private Integer indicator;
    private Integer wild;

    private List<MahjongPlayer> players = new ArrayList<>();

    private MahjongPhase phase;
    /** ACTION：该谁出牌；CLAIM：刚出牌的人 */
    private int turn;
    /** turn 座位刚摸到的牌（吃碰后为 null） */
    private Integer drawnTile;
    /** 刚摸到的牌是补花或杠后从牌尾补的（杠开） */
    private boolean replacementDraw;
    /** 刚吃 / 碰，必须出牌 */
    private boolean justClaimed;
    /** 吃碰后这一手不能打出的牌 */
    private Integer restrictedTile;

    private Integer lastDiscard;
    private Integer lastDiscardSeat;
    private List<MahjongClaim> claims = new ArrayList<>();

    /** 状态版本，每次变化 +1，跨局递增；调度任务和客户端都靠它判断新旧 */
    private int step;
    /** 动作日志序号，跨局递增 */
    private int moveSeq;
    /** 当前等待的截止时间（epoch 毫秒），由服务层设置；0 表示不等待 */
    private long deadline;

    private MahjongRoundResult result;

    /** 本次操作产生、尚未写日志的事件，不序列化 */
    @JsonIgnore
    private List<MahjongEvent> events = new ArrayList<>();

    @JsonIgnore
    public MahjongPlayer player(int seat) {
        return players.get(seat);
    }

    @JsonIgnore
    public int nextSeat(int seat) {
        return (seat + 1) % seatCount;
    }

    @JsonIgnore
    public boolean isFinished() {
        return phase == MahjongPhase.FINISHED;
    }

    /** 取出并清空待记录的事件 */
    public List<MahjongEvent> drainEvents() {
        List<MahjongEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    void emit(MahjongEvent event) {
        events.add(event);
    }
}
