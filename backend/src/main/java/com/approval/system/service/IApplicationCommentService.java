package com.approval.system.service;

import com.approval.system.dto.ApplicationCommentDTO;
import com.approval.system.entity.ApplicationComment;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IApplicationCommentService extends IService<ApplicationComment> {

    /**
     * 创建评论
     *
     * @param applicationId 申请ID
     * @param userId 用户ID
     * @param content 评论内容
     * @param parentId 父评论ID（可选）
     * @return 评论DTO
     */
    ApplicationCommentDTO createComment(Long applicationId, Long userId, String content, Long parentId);

    /**
     * 获取申请的所有评论
     *
     * @param applicationId 申请ID
     * @return 评论列表（树形结构）
     */
    List<ApplicationCommentDTO> getApplicationComments(Long applicationId);

    /**
     * 删除评论
     *
     * @param commentId 评论ID
     * @param userId 用户ID（验证权限）
     */
    void deleteComment(Long commentId, Long userId);

    /**
     * 更新评论
     *
     * @param commentId 评论ID
     * @param userId 用户ID（验证权限）
     * @param content 新内容
     * @return 更新后的评论DTO
     */
    ApplicationCommentDTO updateComment(Long commentId, Long userId, String content);
}
