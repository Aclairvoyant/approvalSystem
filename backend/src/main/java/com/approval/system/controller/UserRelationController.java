package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.UserRelationDTO;
import com.approval.system.entity.User;
import com.approval.system.entity.UserRelation;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/relations")
public class UserRelationController {

    @Autowired
    private IUserRelationService userRelationService;

    @Autowired
    private IUserService userService;

    /**
     * 搜索用户（用于添加对象）
     */
    @GetMapping("/search-user")
    public ApiResponse<Map<String, Object>> searchUser(@RequestParam String keyword) {
        try {
            Long currentUserId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            // 先通过用户名搜索
            User user = userService.findByUsername(keyword);

            // 如果用户名找不到，尝试手机号
            if (user == null) {
                user = userService.findByPhone(keyword);
            }

            if (user == null) {
                return ApiResponse.fail(404, "未找到该用户");
            }

            // 不能添加自己
            if (user.getId().equals(currentUserId)) {
                return ApiResponse.fail(400, "不能添加自己为对象");
            }

            // 返回用户信息（不包含密码）
            Map<String, Object> result = new HashMap<>();
            result.put("id", user.getId());
            result.put("username", user.getUsername());
            result.put("realName", user.getRealName());
            result.put("phone", user.getPhone());
            result.put("email", user.getEmail());
            result.put("avatar", user.getAvatar());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("搜索用户失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取待处理的关系申请（别人发给我的）
     */
    @GetMapping("/pending-requests")
    public ApiResponse<Map<String, Object>> getPendingRequests(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Page<UserRelation> page = userRelationService.getPendingRequests(userId, pageNum, pageSize);

            // 转换为DTO并填充用户信息
            List<UserRelationDTO> dtos = page.getRecords().stream().map(relation -> {
                UserRelationDTO dto = convertToDTO(relation, userId);
                // 获取申请发起人信息
                User requester = userService.getUserById(relation.getRequesterId());
                if (requester != null) {
                    dto.setRequesterName(requester.getRealName());
                    dto.setRequesterUsername(requester.getUsername());
                    dto.setRequesterAvatar(requester.getAvatar());
                }
                return dto;
            }).collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("records", dtos);
            result.put("total", page.getTotal());
            result.put("pages", page.getPages());
            result.put("current", page.getCurrent());
            result.put("size", page.getSize());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取待处理申请失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 发起对象关系申请
     */
    @PostMapping("/request/{targetUserId}")
    public ApiResponse<Void> initiateRelation(@PathVariable Long targetUserId) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            userRelationService.initiateRelationRequest(userId, targetUserId);
            return ApiResponse.success("申请已发送");
        } catch (Exception e) {
            log.error("发起关系申请失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 接受对象关系申请
     */
    @PostMapping("/accept/{relatedUserId}")
    public ApiResponse<Void> acceptRelation(@PathVariable Long relatedUserId) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            userRelationService.acceptRelationRequest(userId, relatedUserId);
            return ApiResponse.success("已建立对象关系");
        } catch (Exception e) {
            log.error("接受关系申请失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 拒绝对象关系申请
     */
    @PostMapping("/reject/{relatedUserId}")
    public ApiResponse<Void> rejectRelation(@PathVariable Long relatedUserId) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            userRelationService.rejectRelationRequest(userId, relatedUserId);
            return ApiResponse.success("已拒绝申请");
        } catch (Exception e) {
            log.error("拒绝关系申请失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取用户的对象列表
     */
    @GetMapping("/my-relations")
    public ApiResponse<Map<String, Object>> getMyRelations(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Page<UserRelation> page = userRelationService.getUserRelations(userId, pageNum, pageSize);

            // 转换为DTO并填充用户信息
            List<UserRelationDTO> dtos = page.getRecords().stream().map(relation -> {
                return convertToDTO(relation, userId);
            }).collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("records", dtos);
            result.put("total", page.getTotal());
            result.put("pages", page.getPages());
            result.put("current", page.getCurrent());
            result.put("size", page.getSize());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取对象列表失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 删除对象关系
     */
    @DeleteMapping("/{relatedUserId}")
    public ApiResponse<Void> deleteRelation(@PathVariable Long relatedUserId) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            userRelationService.deleteRelation(userId, relatedUserId);
            return ApiResponse.success("对象关系已删除");
        } catch (Exception e) {
            log.error("删除关系失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 将UserRelation转换为UserRelationDTO，并填充对方用户信息
     */
    private UserRelationDTO convertToDTO(UserRelation relation, Long currentUserId) {
        UserRelationDTO dto = UserRelationDTO.builder()
                .id(relation.getId())
                .userId(relation.getUserId())
                .relatedUserId(relation.getRelatedUserId())
                .relationType(relation.getRelationType())
                .requesterId(relation.getRequesterId())
                .createdAt(relation.getCreatedAt())
                .updatedAt(relation.getUpdatedAt())
                .build();

        // 确定"对方"是谁：如果当前用户是userId，对方就是relatedUserId，反之亦然
        Long otherUserId = relation.getUserId().equals(currentUserId)
                ? relation.getRelatedUserId()
                : relation.getUserId();

        dto.setOtherUserId(otherUserId);

        // 获取对方用户信息
        User otherUser = userService.getUserById(otherUserId);
        if (otherUser != null) {
            dto.setOtherUserName(otherUser.getRealName());
            dto.setOtherUserUsername(otherUser.getUsername());
            dto.setOtherUserPhone(otherUser.getPhone());
            dto.setOtherUserEmail(otherUser.getEmail());
            dto.setOtherUserAvatar(otherUser.getAvatar());
        }

        return dto;
    }
}
