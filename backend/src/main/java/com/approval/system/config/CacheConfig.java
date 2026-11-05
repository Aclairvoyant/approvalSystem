package com.approval.system.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 用户是否启用（JWT 校验时用），单独设置较短的有效期
     */
    public static final String ACTIVE_USERS_CACHE = "activeUsers";

    /**
     * 验证码缓存配置
     * 有效期5分钟，最多存储1000个验证码
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "emailVerificationCodes",  // 邮箱验证码缓存
                "emailSendRecords"          // 邮件发送记录（用于限流）
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());

        // 管理员禁用账号后最多 30 秒失效（本实例内会立即清掉缓存）
        cacheManager.registerCustomCache(ACTIVE_USERS_CACHE, Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build());

        return cacheManager;
    }
}
