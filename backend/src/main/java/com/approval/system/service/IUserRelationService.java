package com.approval.system.service;

import com.approval.system.entity.UserRelation;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IUserRelationService extends IService<UserRelation> {

    /**
     * 发起对象关系申请
     */
    void initiateRelationRequest(Long userId, Long targetUserId);

    /**
     * 接受对象关系申请（建立互为对象的关系）
     */
    void acceptRelationRequest(Long userId, Long targetUserId);

    /**
     * 拒绝对象关系申请
     */
    void rejectRelationRequest(Long userId, Long targetUserId);

    /**
     * 获取用户的对象列表
     */
    Page<UserRelation> getUserRelations(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 获取待处理的关系申请（别人发给我的）
     */
    Page<UserRelation> getPendingRequests(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 检查两个用户是否互为对象
     */
    boolean isRelated(Long userId1, Long userId2);

    /**
     * 删除对象关系
     */
    void deleteRelation(Long userId, Long relatedUserId);
}
