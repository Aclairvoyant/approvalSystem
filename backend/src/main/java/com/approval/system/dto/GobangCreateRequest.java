package com.approval.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GobangCreateRequest {

    @NotNull(message = "对方用户ID不能为空")
    private Long opponentUserId;
}
