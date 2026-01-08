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
public class GameTaskResponse {
    private Long id;
    private Integer taskType;      // 1=预设，2=自定义
    private Long creatorId;        // 创建者ID（自定义任务）
    private String title;
    private String description;
    private String requirement;
    private String category;
    private Integer difficulty;
    private Integer timeLimit;
    private Integer points;
    private Integer usageCount;    // 使用次数
    private LocalDateTime createdAt;
}
