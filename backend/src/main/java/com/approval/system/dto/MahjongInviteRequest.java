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
public class MahjongInviteRequest {

    @NotNull(message = "请选择要邀请的对象")
    private Long userId;
}
