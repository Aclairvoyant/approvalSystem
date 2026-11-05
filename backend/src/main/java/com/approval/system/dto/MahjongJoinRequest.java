package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongJoinRequest {

    @NotBlank(message = "房间号不能为空")
    @Size(max = 20, message = "房间号格式不正确")
    private String gameCode;
}
