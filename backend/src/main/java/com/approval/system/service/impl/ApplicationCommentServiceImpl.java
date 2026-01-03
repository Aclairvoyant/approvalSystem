package com.approval.system.service.impl;

import com.approval.system.dto.ApplicationCommentDTO;
import com.approval.system.entity.ApplicationComment;
import com.approval.system.entity.User;
import com.approval.system.mapper.ApplicationCommentMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IApplicationCommentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApplicationCommentServiceImpl extends ServiceImpl<ApplicationCommentMapper, ApplicationComment>
        implements IApplicationCommentService {

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public ApplicationCommentDTO createComment(Long applicationId, Long userId, String content, Long parentId) {
        ApplicationComment comment = ApplicationComment.builder()
                .applicationId(applicationId)
                .userId(userId)
                .content(content)
                .parentId(parentId)
                .createdAt(LocalDateTime.now())
                .build();

        this.save(comment);
        log.info("创建评论成功，applicationId: {}, userId: {}, commentId: {}", applicationId, userId, comment.getId());

        return convertToDTO(comment);
    }

    @Override
    public List<ApplicationCommentDTO> getApplicationComments(Long applicationId) {
        LambdaQueryWrapper<ApplicationComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationComment::getApplicationId, applicationId);
        wrapper.orderByAsc(ApplicationComment::getCreatedAt);

        List<ApplicationComment> comments = this.list(wrapper);

        // 如果没有评论，直接返回空列表
        if (comments.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取所有评论者的用户信息
        List<Long> userIds = comments.stream()
                .map(ApplicationComment::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 转换为DTO并构建树形结构
        List<ApplicationCommentDTO> dtoList = comments.stream()
                .map(comment -> convertToDTO(comment, userMap.get(comment.getUserId())))
                .collect(Collectors.toList());

        // 构建树形结构
        return buildCommentTree(dtoList);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        ApplicationComment comment = this.getById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }

        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此评论");
        }

        this.removeById(commentId);
        log.info("删除评论成功，commentId: {}, userId: {}", commentId, userId);
    }

    @Override
    @Transactional
    public ApplicationCommentDTO updateComment(Long commentId, Long userId, String content) {
        ApplicationComment comment = this.getById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }

        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此评论");
        }

        comment.setContent(content);
        comment.setUpdatedAt(LocalDateTime.now());
        this.updateById(comment);

        log.info("更新评论成功，commentId: {}, userId: {}", commentId, userId);
        return convertToDTO(comment);
    }

    /**
     * 转换为DTO
     */
    private ApplicationCommentDTO convertToDTO(ApplicationComment comment) {
        User user = userMapper.selectById(comment.getUserId());
        return convertToDTO(comment, user);
    }

    /**
     * 转换为DTO（带用户信息）
     */
    private ApplicationCommentDTO convertToDTO(ApplicationComment comment, User user) {
        return ApplicationCommentDTO.builder()
                .id(comment.getId())
                .applicationId(comment.getApplicationId())
                .userId(comment.getUserId())
                .userName(user != null ? (user.getRealName() != null ? user.getRealName() : user.getUsername()) : "未知用户")
                .userAvatar(user != null ? user.getAvatar() : null)
                .content(comment.getContent())
                .parentId(comment.getParentId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .replies(new ArrayList<>())
                .build();
    }

    /**
     * 构建评论树形结构
     */
    private List<ApplicationCommentDTO> buildCommentTree(List<ApplicationCommentDTO> allComments) {
        Map<Long, ApplicationCommentDTO> commentMap = allComments.stream()
                .collect(Collectors.toMap(ApplicationCommentDTO::getId, dto -> dto));

        List<ApplicationCommentDTO> rootComments = new ArrayList<>();

        for (ApplicationCommentDTO comment : allComments) {
            if (comment.getParentId() == null) {
                // 顶级评论
                rootComments.add(comment);
            } else {
                // 子评论，添加到父评论的replies中
                ApplicationCommentDTO parent = commentMap.get(comment.getParentId());
                if (parent != null) {
                    parent.getReplies().add(comment);
                }
            }
        }

        return rootComments;
    }
}
