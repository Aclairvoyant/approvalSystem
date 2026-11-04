package com.approval.system.websocket;

import com.approval.system.schedule.MahjongAutoPlayScheduler;
import com.approval.system.service.notification.MahjongRoomChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 麻将状态在事务提交后才推送和调度，避免客户端或电脑读到还没提交的状态。
 * 公共视角发到房间 topic；每个真人的手牌只发到他自己的用户队列（按登录用户路由，别人收不到）。
 */
@Slf4j
@Component
public class MahjongRealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;
    private final MahjongAutoPlayScheduler scheduler;

    public MahjongRealtimeNotifier(SimpMessagingTemplate messagingTemplate, MahjongAutoPlayScheduler scheduler) {
        this.messagingTemplate = messagingTemplate;
        this.scheduler = scheduler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoomChanged(MahjongRoomChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(MahjongMessage.topic(event.gameId()),
                    MahjongMessage.room(event.gameId(), event.version(), event.room()));
            event.hands().forEach((userId, hand) -> messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId), MahjongMessage.userQueue(event.gameId()),
                    MahjongMessage.hand(event.gameId(), event.version(), hand)));
        } catch (Exception e) {
            // 推送失败不影响牌局：客户端重连或刷新时会重新拉取
            log.warn("麻将状态推送失败: gameId={}, version={}", event.gameId(), event.version(), e);
        }
        if (event.nextRunAt() > 0) {
            scheduler.schedule(event.gameId(), event.version(), event.nextRunAt());
        }
    }
}
