package com.approval.system.service.impl;

import com.approval.system.common.enums.RelationTypeEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.UserRelation;
import com.approval.system.mapper.UserRelationMapper;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserRelationServiceImpl extends ServiceImpl<UserRelationMapper, UserRelation> implements IUserRelationService {

    @Override
    @Transactional
    public void initiateRelationRequest(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw BusinessException.badRequest("不能添加自己为对象");
        }

        // 检查当前用户是否已有活跃的对象关系
        if (this.getOne(establishedRelationOf(userId)) != null) {
            throw BusinessException.conflict("您已经有对象了，无法添加新的对象关系");
        }

        // 检查目标用户是否已有活跃的对象关系
        if (this.getOne(establishedRelationOf(targetUserId)) != null) {
            throw BusinessException.conflict("对方已经有对象了，无法添加新的对象关系");
        }

        // 检查是否已存在关系
        if (this.getOne(relationBetween(userId, targetUserId)) != null) {
            throw BusinessException.conflict("关系已存在或申请已提交");
        }

        UserRelation relation = UserRelation.builder()
                .userId(userId)
                .relatedUserId(targetUserId)
                .relationType(RelationTypeEnum.PENDING.getCode())
                .requesterId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        this.save(relation);
    }

    @Override
    @Transactional
    public void acceptRelationRequest(Long userId, Long targetUserId) {
        // 只有被邀请的一方能接受：申请必须由 targetUserId 发起、还在申请中，且当前用户不是发起人。
        // 发起人拿对方 ID 调用时也查得到这一行，所以必须校验 requesterId，否则可以自己接受自己的申请。
        UserRelation relation = this.getOne(relationBetween(targetUserId, userId));
        if (relation == null
                || !RelationTypeEnum.PENDING.is(relation.getRelationType())
                || !targetUserId.equals(relation.getRequesterId())
                || userId.equals(relation.getRequesterId())) {
            throw BusinessException.notFound("申请不存在");
        }

        // 检查接受者（当前用户）是否已有活跃的对象关系
        if (this.getOne(establishedRelationOf(userId)) != null) {
            throw BusinessException.conflict("您已经有对象了，无法接受新的对象关系");
        }

        // 一段关系只存一行，接受后把这一行标记为已互为对象
        relation.setRelationType(RelationTypeEnum.ESTABLISHED.getCode());
        relation.setUpdatedAt(LocalDateTime.now());
        this.updateById(relation);
    }

    @Override
    @Transactional
    public void rejectRelationRequest(Long userId, Long targetUserId) {
        // 只处理申请中的行：被邀请方拒绝，或发起人撤回自己的申请（两种都只影响当前用户参与的这一行）。
        // 已建立的关系不能通过"拒绝"删掉，解除关系走 deleteRelation。
        UserRelation relation = this.getOne(relationBetween(targetUserId, userId));
        if (relation == null || !RelationTypeEnum.PENDING.is(relation.getRelationType())) {
            throw BusinessException.notFound("申请不存在");
        }
        this.removeById(relation.getId());
    }

    @Override
    public Page<UserRelation> getUserRelations(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<UserRelation> queryWrapper = establishedRelationOf(userId)
                .orderByDesc(UserRelation::getCreatedAt);
        return this.page(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public Page<UserRelation> getPendingRequests(Long userId, Integer pageNum, Integer pageSize) {
        // 获取别人发给我的申请（我是 related_user_id，且不是申请发起人）
        LambdaQueryWrapper<UserRelation> queryWrapper = new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getRelatedUserId, userId)
                .ne(UserRelation::getRequesterId, userId)
                .eq(UserRelation::getRelationType, RelationTypeEnum.PENDING.getCode())
                .orderByDesc(UserRelation::getCreatedAt);
        return this.page(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public boolean isRelated(Long userId1, Long userId2) {
        LambdaQueryWrapper<UserRelation> queryWrapper = relationBetween(userId1, userId2)
                .eq(UserRelation::getRelationType, RelationTypeEnum.ESTABLISHED.getCode());
        return this.getOne(queryWrapper) != null;
    }

    @Override
    public Long getActivePartnerId(Long userId) {
        LambdaQueryWrapper<UserRelation> queryWrapper = establishedRelationOf(userId)
                .orderByDesc(UserRelation::getUpdatedAt)
                .last("limit 1");

        UserRelation relation = this.getOne(queryWrapper);
        if (relation == null) {
            return null;
        }
        return relation.getUserId().equals(userId) ? relation.getRelatedUserId() : relation.getUserId();
    }

    @Override
    @Transactional
    public void deleteRelation(Long userId, Long relatedUserId) {
        this.remove(relationBetween(userId, relatedUserId));
    }

    /**
     * 某个用户已建立的对象关系（不论他在 user_id 还是 related_user_id 一侧）
     */
    private LambdaQueryWrapper<UserRelation> establishedRelationOf(Long userId) {
        return new LambdaQueryWrapper<UserRelation>()
                .and(w -> w.eq(UserRelation::getUserId, userId).or().eq(UserRelation::getRelatedUserId, userId))
                .eq(UserRelation::getRelationType, RelationTypeEnum.ESTABLISHED.getCode());
    }

    /**
     * 两个用户之间的关系行（任意方向、任意状态）
     */
    private LambdaQueryWrapper<UserRelation> relationBetween(Long userA, Long userB) {
        return new LambdaQueryWrapper<UserRelation>()
                .and(w -> w.eq(UserRelation::getUserId, userA).eq(UserRelation::getRelatedUserId, userB)
                        .or().eq(UserRelation::getUserId, userB).eq(UserRelation::getRelatedUserId, userA));
    }
}
