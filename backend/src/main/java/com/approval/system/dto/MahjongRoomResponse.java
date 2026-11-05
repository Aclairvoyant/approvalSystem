package com.approval.system.dto;

import com.approval.system.service.mahjong.MahjongViews;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 麻将房间视角。round 是公共信息；me 只在请求者坐在桌上时给出，而且只有他自己的手牌。
 * 推送到房间 topic 时 me 和 mySeat 为空，手牌另外推到每个人自己的队列。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongRoomResponse {

    private Long id;
    private String gameCode;
    /** BAIDA / QIAOMA */
    private String mode;
    private String modeName;
    private Integer seatCount;
    /** 每个付分人每局最多付多少分，0 表示不封顶 */
    private Integer capPoints;
    private Long ownerId;
    /** 1 等待开局，2 对局中，3 本局结束（可再来一局），4 已取消 */
    private Integer gameStatus;
    private Integer roundNo;
    private Integer dealerSeat;
    /** 下一局（或本局）带的荒番数 */
    private Integer huangCount;
    private Long invitedUserId;
    private String invitedUserName;
    private List<MahjongSeatResponse> seats;
    private Integer mySeat;
    private Integer version;
    /** 服务端当前时间（毫秒），客户端据此换算倒计时 */
    private Long serverTime;
    private MahjongViews.PublicRound round;
    private MahjongViews.PrivateHand me;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime updatedAt;
}
