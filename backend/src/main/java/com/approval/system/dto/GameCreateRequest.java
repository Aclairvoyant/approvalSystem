package com.approval.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameCreateRequest {
    @NotNull(message = "对方用户ID不能为空")
    private Long opponentUserId;

    /**
     * 自定义任务位置（可选），如 [5, 10, 15, 20, 25]
     * 如果不传，则使用默认位置
     */
    private List<Integer> taskPositions;
}
