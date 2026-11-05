package com.approval.system.common.security;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.common.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCOUNT_DISABLED_MESSAGE = "账号已被禁用，请联系管理员";

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ActiveUserChecker activeUserChecker;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // ✅ 跳过 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 跳过不需要认证的路径（仅登录和注册）
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") ||
            path.startsWith("/api/health") ||
            path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") ||
            path.startsWith("/swagger-resources") || path.startsWith("/webjars") ||
            path.startsWith("/uploads/") || path.startsWith("/api/auth/send-email-code")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 对其他请求提取并验证 JWT Token
        String token = extractToken(request);

        if (token != null && jwtUtils.validateToken(token)) {
            Long userId = jwtUtils.getUserIdFromToken(token);
            String username = jwtUtils.getUsernameFromToken(token);

            if (userId != null && username != null) {
                // 账号被禁用或删除后，已签发的 token 立即失效；角色同样以数据库为准而不是 token 里的声明，
                // 这样降级/禁用在缓存过期（或本实例 evict）后马上生效
                ActiveUserChecker.UserAuthState state = activeUserChecker.lookup(userId);
                if (!state.active()) {
                    log.warn("拒绝已禁用用户的请求: userId={}, path={}", userId, path);
                    writeAccountDisabled(response);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authoritiesOf(state));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static List<GrantedAuthority> authoritiesOf(ActiveUserChecker.UserAuthState state) {
        if (state.admin()) {
            return List.of(new SimpleGrantedAuthority(ROLE_USER), new SimpleGrantedAuthority(ROLE_ADMIN));
        }
        return List.of(new SimpleGrantedAuthority(ROLE_USER));
    }

    private void writeAccountDisabled(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(401, ACCOUNT_DISABLED_MESSAGE));
    }

    /**
     * 从请求头提取JWT Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
