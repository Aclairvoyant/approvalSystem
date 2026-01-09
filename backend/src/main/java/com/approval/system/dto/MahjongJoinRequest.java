package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加入麻将游戏请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongJoinRequest {

    /** 房间号 */
    @NotBlank(message = "房间号不能为空")
    private String gameCode;
}
