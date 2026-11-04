package com.approval.system.common.security;

import com.approval.system.config.CacheConfig;
import com.approval.system.entity.User;
import com.approval.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveUserCheckerTest {

    @Mock
    private UserMapper userMapper;

    private ActiveUserChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ActiveUserChecker();
        ReflectionTestUtils.setField(checker, "userMapper", userMapper);
        ReflectionTestUtils.setField(checker, "cacheManager", new CacheConfig().cacheManager());
    }

    @Test
    void onlyStatusOneCountsAsActive() {
        when(userMapper.selectById(1L)).thenReturn(User.builder().id(1L).status(1).build());
        when(userMapper.selectById(2L)).thenReturn(User.builder().id(2L).status(0).build());
        when(userMapper.selectById(3L)).thenReturn(null);

        assertThat(checker.isActive(1L)).isTrue();
        assertThat(checker.isActive(2L)).isFalse();
        assertThat(checker.isActive(3L)).isFalse();
    }

    @Test
    void cachesStatusUntilEvicted() {
        when(userMapper.selectById(1L))
                .thenReturn(User.builder().id(1L).status(1).build())
                .thenReturn(User.builder().id(1L).status(0).build());

        assertThat(checker.isActive(1L)).isTrue();
        assertThat(checker.isActive(1L)).isTrue();
        verify(userMapper, times(1)).selectById(1L);

        checker.evict(1L);

        assertThat(checker.isActive(1L)).isFalse();
        verify(userMapper, times(2)).selectById(1L);
    }

    @Test
    void lookupReportsAdminRoleAlongsideStatus() {
        when(userMapper.selectById(1L)).thenReturn(User.builder().id(1L).status(1).role(1).build());
        when(userMapper.selectById(2L)).thenReturn(User.builder().id(2L).status(1).role(0).build());
        when(userMapper.selectById(3L)).thenReturn(User.builder().id(3L).status(0).role(1).build());

        assertThat(checker.lookup(1L)).isEqualTo(new ActiveUserChecker.UserAuthState(true, true));
        assertThat(checker.lookup(2L)).isEqualTo(new ActiveUserChecker.UserAuthState(true, false));
        assertThat(checker.lookup(3L).active()).isFalse();
        assertThat(checker.lookup(null)).isEqualTo(ActiveUserChecker.UserAuthState.INACTIVE);
    }
}
