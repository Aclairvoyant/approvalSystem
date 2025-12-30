package com.approval.system.service.impl;

import com.approval.system.entity.UserRelation;
import com.approval.system.mapper.UserRelationMapper;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
            throw new RuntimeException("不能添加自己为对象");
        }

        // 检查当前用户是否已有活跃的对象关系
        QueryWrapper<UserRelation> userActiveRelation = new QueryWrapper<>();
        userActiveRelation.and(w -> w.eq("user_id", userId).or().eq("related_user_id", userId));
        userActiveRelation.eq("relation_type", 2); // 2=已互为对象
        UserRelation existingUserRelation = this.getOne(userActiveRelation);
        if (existingUserRelation != null) {
            throw new RuntimeException("您已经有对象了，无法添加新的对象关系");
        }

        // 检查目标用户是否已有活跃的对象关系
        QueryWrapper<UserRelation> targetActiveRelation = new QueryWrapper<>();
        targetActiveRelation.and(w -> w.eq("user_id", targetUserId).or().eq("related_user_id", targetUserId));
        targetActiveRelation.eq("relation_type", 2); // 2=已互为对象
        UserRelation existingTargetRelation = this.getOne(targetActiveRelation);
        if (existingTargetRelation != null) {
            throw new RuntimeException("对方已经有对象了，无法添加新的对象关系");
        }

        // 检查是否已存在关系
        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_id", userId).eq("related_user_id", targetUserId)
                .or().eq("user_id", targetUserId).eq("related_user_id", userId));

        UserRelation existing = this.getOne(queryWrapper);
        if (existing != null) {
            throw new RuntimeException("关系已存在或申请已提交");
        }

        UserRelation relation = UserRelation.builder()
                .userId(userId)
                .relatedUserId(targetUserId)
                .relationType(1) // 1=申请中
                .requesterId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        this.save(relation);
    }

    @Override
    @Transactional
    public void acceptRelationRequest(Long userId, Long targetUserId) {
        // 检查接受者（当前用户）是否已有活跃的对象关系
        QueryWrapper<UserRelation> userActiveRelation = new QueryWrapper<>();
        userActiveRelation.and(w -> w.eq("user_id", userId).or().eq("related_user_id", userId));
        userActiveRelation.eq("relation_type", 2); // 2=已互为对象
        UserRelation existingUserRelation = this.getOne(userActiveRelation);
        if (existingUserRelation != null) {
            throw new RuntimeException("您已经有对象了，无法接受新的对象关系");
        }

        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_id", targetUserId).eq("related_user_id", userId)
                .or().eq("user_id", userId).eq("related_user_id", targetUserId));

        UserRelation relation = this.getOne(queryWrapper);
        if (relation == null) {
            throw new RuntimeException("申请不存在");
        }

        // 更新双向关系为已建立
        relation.setRelationType(2); // 2=已互为对象
        relation.setUpdatedAt(LocalDateTime.now());
        this.updateById(relation);

        // 如果原始关系是单向的，需要创建反向关系
        if ((relation.getUserId().equals(targetUserId) && relation.getRelatedUserId().equals(userId)) ||
                (relation.getUserId().equals(userId) && relation.getRelatedUserId().equals(targetUserId))) {
            // 关系已存在，只需更新状态
        }
    }

    @Override
    @Transactional
    public void rejectRelationRequest(Long userId, Long targetUserId) {
        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_id", targetUserId).eq("related_user_id", userId)
                .or().eq("user_id", userId).eq("related_user_id", targetUserId));

        UserRelation relation = this.getOne(queryWrapper);
        if (relation != null) {
            this.removeById(relation.getId());
        }
    }

    @Override
    public Page<UserRelation> getUserRelations(Long userId, Integer pageNum, Integer pageSize) {
        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_id", userId).or().eq("related_user_id", userId));
        queryWrapper.eq("relation_type", 2); // 只获取已建立的关系
        queryWrapper.orderByDesc("created_at");

        Page<UserRelation> page = new Page<>(pageNum, pageSize);
        return this.page(page, queryWrapper);
    }

    @Override
    public Page<UserRelation> getPendingRequests(Long userId, Integer pageNum, Integer pageSize) {
        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        // 获取别人发给我的申请（我是 related_user_id，且不是申请发起人）
        queryWrapper.eq("related_user_id", userId);
        queryWrapper.ne("requester_id", userId);
        queryWrapper.eq("relation_type", 1); // 1=申请中
        queryWrapper.orderByDesc("created_at");

        Page<UserRelation> page = new Page<>(pageNum, pageSize);
        return this.page(page, queryWrapper);
    }

    @Override
    public boolean isRelated(Long userId1, Long userId2) {
        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_id", userId1).eq("related_user_id", userId2)
                .or().eq("user_id", userId2).eq("related_user_id", userId1));
        queryWrapper.eq("relation_type", 2);

        UserRelation relation = this.getOne(queryWrapper);
        return relation != null;
    }

    @Override
    @Transactional
    public void deleteRelation(Long userId, Long relatedUserId) {
        QueryWrapper<UserRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_id", userId).eq("related_user_id", relatedUserId)
                .or().eq("user_id", relatedUserId).eq("related_user_id", userId));

        this.remove(queryWrapper);
    }
}
