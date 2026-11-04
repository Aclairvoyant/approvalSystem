package com.approval.system.websocket;

import com.approval.system.common.security.ActiveUserChecker;
import com.approval.system.common.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketHandshakeInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ActiveUserChecker activeUserChecker;

    private WebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(interceptor, "activeUserChecker", activeUserChecker);
        when(jwtUtils.validateToken("t")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("t")).thenReturn(10L);
        when(jwtUtils.getUsernameFromToken("t")).thenReturn("user10");
    }

    @Test
    void rejectsHandshakeOfDisabledUser() throws Exception {
        when(activeUserChecker.isActive(10L)).thenReturn(false);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request(), response(), null, attributes);

        assertThat(accepted).isFalse();
        assertThat(attributes).doesNotContainKey("userId");
    }

    @Test
    void acceptsHandshakeOfActiveUser() throws Exception {
        when(activeUserChecker.isActive(10L)).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request(), response(), null, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("userId", 10L);
    }

    private ServletServerHttpRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setParameter("token", "t");
        return new ServletServerHttpRequest(request);
    }

    private ServletServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }
}
