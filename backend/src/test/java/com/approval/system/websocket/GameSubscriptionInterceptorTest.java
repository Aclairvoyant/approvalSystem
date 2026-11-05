package com.approval.system.websocket;

import com.approval.system.service.IGobangService;
import com.approval.system.service.IMahjongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订阅校验：麻将房间的公共 topic 和个人队列只给坐在这桌的人；CONNECT 时绑定用户，供按用户推送手牌。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameSubscriptionInterceptorTest {

    private static final long PLAYER = 10L;
    private static final long OUTSIDER = 99L;

    @Mock
    private IGobangService gobangService;
    @Mock
    private IMahjongService mahjongService;

    private final MessageChannel channel = mock(MessageChannel.class);
    private GameSubscriptionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new GameSubscriptionInterceptor(gobangService, mahjongService);
        when(mahjongService.isSeated(5L, PLAYER)).thenReturn(true);
        when(mahjongService.isSeated(5L, OUTSIDER)).thenReturn(false);
    }

    @Test
    void seatedPlayerCanSubscribeToRoomAndOwnQueue() {
        assertThatCode(() -> interceptor.preSend(subscribe("/topic/mahjong/5", PLAYER), channel))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(subscribe("/user/queue/mahjong/5", PLAYER), channel))
                .doesNotThrowAnyException();
    }

    @Test
    void outsiderCannotSubscribe() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/mahjong/5", OUTSIDER), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/user/queue/mahjong/5", OUTSIDER), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/mahjong/5", null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rawOrMalformedMahjongDestinationsAreRejected() {
        // 绕过 /user 前缀直接订阅别人解析后的队列
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/queue/mahjong/5-userabc123", PLAYER), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/mahjong/5/seat/1", PLAYER), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/mahjong/abc", PLAYER), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void gobangCheckStillApplies() {
        when(gobangService.validateGamePlayer(1L, OUTSIDER)).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/gobang/1", OUTSIDER), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> interceptor.preSend(subscribe("/topic/game/1", OUTSIDER), channel))
                .doesNotThrowAnyException();
    }

    @Test
    void connectBindsUserForUserDestinations() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>(Map.of("userId", PLAYER)));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("10");
    }

    private static Message<byte[]> subscribe(String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-1");
        Map<String, Object> attributes = new HashMap<>();
        if (userId != null) {
            attributes.put("userId", userId);
        }
        accessor.setSessionAttributes(attributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
