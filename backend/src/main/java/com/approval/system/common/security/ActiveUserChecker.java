package com.approval.system.common.security;

import com.approval.system.common.enums.UserRoleEnum;
import com.approval.system.common.enums.UserStatusEnum;
import com.approval.system.config.CacheConfig;
import com.approval.system.entity.User;
import com.approval.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 查询用户当前的账号状态和角色（users.status / users.role），供 JWT 过滤器和 WebSocket 握手使用。
 * 结果短暂缓存，避免每个请求都查库；管理员修改状态或角色后调用 evict 立即在本实例生效。
 * 这里直接用 UserMapper 而不是 IUserService，避免和 SecurityConfig 形成循环依赖。
 */
@Component
public class ActiveUserChecker {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 认证所需的用户快照。用户不存在时视为未启用。
     */
    public record UserAuthState(boolean active, boolean admin) {
        public static final UserAuthState INACTIVE = new UserAuthState(false, false);
    }

    public UserAuthState lookup(Long userId) {
        if (userId == null) {
            return UserAuthState.INACTIVE;
        }
        Cache cache = cacheManager.getCache(CacheConfig.ACTIVE_USERS_CACHE);
        if (cache == null) {
            return load(userId);
        }
        UserAuthState state = cache.get(userId, () -> load(userId));
        return state == null ? UserAuthState.INACTIVE : state;
    }

    public boolean isActive(Long userId) {
        return lookup(userId).active();
    }

    public void evict(Long userId) {
        Cache cache = cacheManager.getCache(CacheConfig.ACTIVE_USERS_CACHE);
        if (cache != null && userId != null) {
            cache.evict(userId);
        }
    }

    private UserAuthState load(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return UserAuthState.INACTIVE;
        }
        return new UserAuthState(UserStatusEnum.ENABLED.is(user.getStatus()), UserRoleEnum.ADMIN.is(user.getRole()));
    }
}
