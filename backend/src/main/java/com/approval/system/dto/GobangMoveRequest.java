package com.approval.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GobangMoveRequest {

    private Long gameId;

    @NotNull(message = "行坐标不能为空")
    @Min(value = 0, message = "行坐标需在0-14之间")
    @Max(value = 14, message = "行坐标需在0-14之间")
    private Integer row;

    @NotNull(message = "列坐标不能为空")
    @Min(value = 0, message = "列坐标需在0-14之间")
    @Max(value = 14, message = "列坐标需在0-14之间")
    private Integer col;
}
