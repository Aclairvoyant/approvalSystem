package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 麻将操作请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongActionRequest {

    /** 操作类型: DISCARD/PONG/MING_KONG/AN_KONG/BU_KONG/HU/PASS */
    @NotBlank(message = "操作类型不能为空")
    private String actionType;

    /** 相关的牌（出牌时必填） */
    private String tile;

    /** 杠牌时的额外参数 */
    private String extraData;
}
