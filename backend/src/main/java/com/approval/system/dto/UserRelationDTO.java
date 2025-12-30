package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRelationDTO {
    private Long id;
    private Long userId;
    private Long relatedUserId;
    private Integer relationType;
    private Long requesterId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 对象用户信息（对方）
    private Long otherUserId;
    private String otherUserName;
    private String otherUserUsername;
    private String otherUserPhone;
    private String otherUserEmail;
    private String otherUserAvatar;

    // 申请发起人信息（用于待处理列表）
    private String requesterName;
    private String requesterUsername;
    private String requesterAvatar;
}
