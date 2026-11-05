package com.approval.system.common.security;

import com.approval.system.common.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "token-of-user-10";

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ActiveUserChecker activeUserChecker;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(filter, "activeUserChecker", activeUserChecker);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTokenOfDisabledUserWith401() throws Exception {
        givenValidTokenForUser(10L);
        when(activeUserChecker.lookup(10L)).thenReturn(ActiveUserChecker.UserAuthState.INACTIVE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(authorizedRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":401").contains("账号已被禁用");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticatesTokenOfActiveUser() throws Exception {
        givenValidTokenForUser(10L);
        when(activeUserChecker.lookup(10L)).thenReturn(new ActiveUserChecker.UserAuthState(true, false));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(authorizedRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(10L);
        assertThat(authorities()).containsExactly(JwtAuthenticationFilter.ROLE_USER);
    }

    @Test
    void grantsAdminRoleFromCurrentDatabaseRole() throws Exception {
        givenValidTokenForUser(10L);
        when(activeUserChecker.lookup(10L)).thenReturn(new ActiveUserChecker.UserAuthState(true, true));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(authorizedRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(authorities()).containsExactlyInAnyOrder(
                JwtAuthenticationFilter.ROLE_USER, JwtAuthenticationFilter.ROLE_ADMIN);
    }

    private java.util.List<String> authorities() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .toList();
    }

    private void givenValidTokenForUser(Long userId) {
        when(jwtUtils.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtils.getUserIdFromToken(TOKEN)).thenReturn(userId);
        when(jwtUtils.getUsernameFromToken(TOKEN)).thenReturn("user" + userId);
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications/99");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }
}
