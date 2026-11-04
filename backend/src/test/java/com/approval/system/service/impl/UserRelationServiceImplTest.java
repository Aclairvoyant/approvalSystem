package com.approval.system.service.impl;

import com.approval.system.common.enums.RelationTypeEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.UserRelation;
import com.approval.system.mapper.UserRelationMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关系申请的接受 / 拒绝只能作用于申请中的那一行，并且接受只能由被邀请的一方完成。
 * 用户 10 向用户 20 发起申请：user_id=10, related_user_id=20, requester_id=10。
 */
@ExtendWith(MockitoExtension.class)
class UserRelationServiceImplTest {

    private static final long REQUESTER = 10L;
    private static final long INVITEE = 20L;

    @Mock
    private UserRelationMapper userRelationMapper;

    private UserRelationServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 和 removeById 需要实体的 TableInfo，不启动 Spring 时手动注册
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserRelation.class);
    }

    @BeforeEach
    void setUp() {
        service = new UserRelationServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", userRelationMapper);
    }

    @Test
    void inviteeAcceptsPendingRequest() {
        UserRelation pending = relation(RelationTypeEnum.PENDING);
        // 第一次查询：两人之间的关系行；第二次：被邀请方已有的对象关系（没有）
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(pending, (UserRelation) null);
        when(userRelationMapper.updateById(any(UserRelation.class))).thenReturn(1);

        service.acceptRelationRequest(INVITEE, REQUESTER);

        ArgumentCaptor<UserRelation> updated = ArgumentCaptor.forClass(UserRelation.class);
        verify(userRelationMapper).updateById(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(1L);
        assertThat(updated.getValue().getRelationType()).isEqualTo(RelationTypeEnum.ESTABLISHED.getCode());
    }

    @Test
    void requesterCannotAcceptOwnRequest() {
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(relation(RelationTypeEnum.PENDING));

        assertNotFound(() -> service.acceptRelationRequest(REQUESTER, INVITEE));
        verify(userRelationMapper, never()).updateById(any(UserRelation.class));
    }

    @Test
    void acceptRequiresPendingRow() {
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(relation(RelationTypeEnum.ESTABLISHED));

        assertNotFound(() -> service.acceptRelationRequest(INVITEE, REQUESTER));
        verify(userRelationMapper, never()).updateById(any(UserRelation.class));
    }

    @Test
    void acceptRequiresTargetToBeTheRequester() {
        // 行里记录的发起人不是路径里的 targetUserId（例如脏数据），不能接受
        UserRelation pending = relation(RelationTypeEnum.PENDING);
        pending.setRequesterId(INVITEE);
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(pending);

        assertNotFound(() -> service.acceptRelationRequest(INVITEE, REQUESTER));
        verify(userRelationMapper, never()).updateById(any(UserRelation.class));
    }

    @Test
    void acceptWithoutRequestIsNotFound() {
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        assertNotFound(() -> service.acceptRelationRequest(INVITEE, REQUESTER));
        verify(userRelationMapper, never()).updateById(any(UserRelation.class));
    }

    @Test
    void inviteeWithExistingPartnerCannotAccept() {
        UserRelation otherPartner = UserRelation.builder()
                .id(2L).userId(INVITEE).relatedUserId(30L)
                .relationType(RelationTypeEnum.ESTABLISHED.getCode()).requesterId(INVITEE)
                .build();
        when(userRelationMapper.selectOne(any(), anyBoolean()))
                .thenReturn(relation(RelationTypeEnum.PENDING), otherPartner);

        assertThatThrownBy(() -> service.acceptRelationRequest(INVITEE, REQUESTER))
                .isInstanceOf(BusinessException.class)
                .hasMessage("您已经有对象了，无法接受新的对象关系")
                .extracting("code").isEqualTo(BusinessException.CONFLICT);
        verify(userRelationMapper, never()).updateById(any(UserRelation.class));
    }

    @Test
    void inviteeRejectsPendingRequest() {
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(relation(RelationTypeEnum.PENDING));
        when(userRelationMapper.deleteById(1L)).thenReturn(1);

        service.rejectRelationRequest(INVITEE, REQUESTER);

        verify(userRelationMapper).deleteById(1L);
    }

    @Test
    void requesterCanWithdrawOwnPendingRequest() {
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(relation(RelationTypeEnum.PENDING));
        when(userRelationMapper.deleteById(1L)).thenReturn(1);

        service.rejectRelationRequest(REQUESTER, INVITEE);

        verify(userRelationMapper).deleteById(1L);
    }

    @Test
    void rejectDoesNotDeleteEstablishedRelation() {
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(relation(RelationTypeEnum.ESTABLISHED));

        assertNotFound(() -> service.rejectRelationRequest(INVITEE, REQUESTER));
        verify(userRelationMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void rejectWithoutRequestIsNotFound() {
        when(userRelationMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        assertNotFound(() -> service.rejectRelationRequest(INVITEE, REQUESTER));
        verify(userRelationMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    private static UserRelation relation(RelationTypeEnum type) {
        return UserRelation.builder()
                .id(1L)
                .userId(REQUESTER)
                .relatedUserId(INVITEE)
                .relationType(type.getCode())
                .requesterId(REQUESTER)
                .build();
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasMessage("申请不存在")
                .extracting("code").isEqualTo(BusinessException.NOT_FOUND);
    }
}
