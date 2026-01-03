package com.approval.system.common.config;

import com.approval.system.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ✅ 1. 禁用 CSRF
                .csrf(csrf -> csrf.disable())
                // ✅ 2. 配置 CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // ✅ 3. 会话管理 - 无状态
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ✅ 4. 授权配置 - 重要！
                .authorizeHttpRequests(authz -> authz
                        // ✅ 允许 OPTIONS 预检请求
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // ✅ 允许公开路由 - 只允许登录和注册
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/send-email-code").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        // ✅ 允许 OpenAPI/Swagger 文档
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/swagger-resources/**", "/webjars/**").permitAll()
                        // ✅ 允许静态资源（头像上传）
                        .requestMatchers("/uploads/**").permitAll()
                        // ✅ 其他请求需要认证
                        .anyRequest().authenticated()
                )
                // ✅ 5. JWT 过滤器 - 放在 UsernamePasswordAuthenticationFilter 之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ✅ 允许所有源（在生产环境中应该指定具体的源）
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));

        // ✅ 允许所有 HTTP 方法
        configuration.setAllowedMethods(Arrays.asList(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.HEAD.name()
        ));

        // ✅ 允许所有请求头
        configuration.setAllowedHeaders(Collections.singletonList("*"));

        // ✅ 允许暴露的响应头
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept"
        ));

        // ✅ 不允许凭证（因为使用 * 作为源时不能设置 allowCredentials = true）
        configuration.setAllowCredentials(false);

        // ✅ 预检请求缓存时间（1小时）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

