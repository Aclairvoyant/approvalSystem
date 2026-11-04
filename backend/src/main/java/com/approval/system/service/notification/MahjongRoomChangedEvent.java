package com.approval.system.service.notification;

import com.approval.system.dto.MahjongRoomResponse;
import com.approval.system.service.mahjong.MahjongViews;

import java.util.Map;

/**
 * 麻将房间状态变了。事务提交后由 MahjongRealtimeNotifier 推送：room 发到房间 topic（公共信息），
 * hands 按用户分别发到各自的队列；nextRunAt &gt; 0 时安排下一次自动行动或超时检查。
 */
public record MahjongRoomChangedEvent(Long gameId,
                                      int version,
                                      MahjongRoomResponse room,
                                      Map<Long, MahjongViews.PrivateHand> hands,
                                      long nextRunAt) {
}
