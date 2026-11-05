package com.approval.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 房主把空位设成电脑（bot=true），或把电脑改回空位（bot=false）。0 号位是房主，不能改。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongSeatRequest {

    @NotNull(message = "请选择座位")
    @Min(value = 1, message = "座位不存在")
    @Max(value = 3, message = "座位不存在")
    private Integer seatIndex;

    @NotNull(message = "请选择设为电脑还是空位")
    private Boolean bot;
}
