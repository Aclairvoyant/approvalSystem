package com.approval.system.websocket;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.dto.MahjongRoomResponse;
import com.approval.system.schedule.MahjongAutoPlayScheduler;
import com.approval.system.service.IMahjongService;
import com.approval.system.service.mahjong.MahjongActionType;
import com.approval.system.service.mahjong.MahjongViews;
import com.approval.system.service.notification.MahjongRoomChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 推送路由：公共视角发房间 topic，每个人的手牌只发给他自己；错误只回给操作者。
 */
@ExtendWith(MockitoExtension.class)
class MahjongRealtimeTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MahjongAutoPlayScheduler scheduler;
    @Mock
    private IMahjongService mahjongService;

    @Test
    void roomGoesToTopicAndHandsGoToEachOwner() {
        MahjongRealtimeNotifier notifier = new MahjongRealtimeNotifier(messagingTemplate, scheduler);
        MahjongRoomResponse room = MahjongRoomResponse.builder().id(5L).version(8).build();
        Map<Long, MahjongViews.PrivateHand> hands = new LinkedHashMap<>();
        hands.put(10L, hand(0, List.of(11, 12, 13)));
        hands.put(20L, hand(1, List.of(21, 22, 23)));

        notifier.onRoomChanged(new MahjongRoomChangedEvent(5L, 8, room, hands, 123456L));

        ArgumentCaptor<MahjongMessage> topicMessage = ArgumentCaptor.forClass(MahjongMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/mahjong/5"), topicMessage.capture());
        assertThat(topicMessage.getValue().getType()).isEqualTo(MahjongMessage.MessageType.MAHJONG_ROOM);
        assertThat(topicMessage.getValue().getData()).containsOnlyKeys("room");

        ArgumentCaptor<MahjongMessage> first = ArgumentCaptor.forClass(MahjongMessage.class);
        ArgumentCaptor<MahjongMessage> second = ArgumentCaptor.forClass(MahjongMessage.class);
        verify(messagingTemplate).convertAndSendToUser(eq("10"), eq("/queue/mahjong/5"), first.capture());
        verify(messagingTemplate).convertAndSendToUser(eq("20"), eq("/queue/mahjong/5"), second.capture());
        assertThat(((MahjongViews.PrivateHand) first.getValue().getData().get("hand")).getSeat()).isZero();
        assertThat(((MahjongViews.PrivateHand) second.getValue().getData().get("hand")).getSeat()).isEqualTo(1);
        verify(scheduler).schedule(5L, 8, 123456L);
    }

    @Test
    void finishedRoundIsNotScheduled() {
        MahjongRealtimeNotifier notifier = new MahjongRealtimeNotifier(messagingTemplate, scheduler);
        notifier.onRoomChanged(new MahjongRoomChangedEvent(5L, 9, MahjongRoomResponse.builder().id(5L).build(),
                Map.of(), 0));
        verifyNoInteractions(scheduler);
    }

    @Test
    void actionUsesSessionUserAndErrorsOnlyReachTheActor() {
        MahjongWebSocketController controller = new MahjongWebSocketController(mahjongService, messagingTemplate);
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(new HashMap<>(Map.of("userId", 10L)));
        accessor.setDestination("/app/mahjong/5/action");
        MahjongActionRequest request = new MahjongActionRequest(MahjongActionType.PASS, null, null);

        controller.action(5L, request, accessor);
        verify(mahjongService).act(5L, 10L, request);

        controller.handleException(BusinessException.badRequest("还没轮到你"), accessor);
        ArgumentCaptor<MahjongMessage> error = ArgumentCaptor.forClass(MahjongMessage.class);
        verify(messagingTemplate).convertAndSendToUser(eq("10"), eq("/queue/mahjong/5"), error.capture());
        assertThat(error.getValue().getType()).isEqualTo(MahjongMessage.MessageType.MAHJONG_ERROR);
        assertThat(error.getValue().getData()).containsEntry("error", "还没轮到你");
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        // 未预期的异常不把内部信息发给客户端
        controller.handleException(new IllegalStateException("db down"), accessor);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSendToUser(eq("10"), eq("/queue/mahjong/5"), error.capture());
        assertThat(error.getValue().getData()).containsEntry("error", "操作失败，请稍后重试");
    }

    private static MahjongViews.PrivateHand hand(int seat, List<Integer> tiles) {
        MahjongViews.PrivateHand hand = new MahjongViews.PrivateHand();
        hand.setSeat(seat);
        hand.setHand(tiles);
        return hand;
    }
}
