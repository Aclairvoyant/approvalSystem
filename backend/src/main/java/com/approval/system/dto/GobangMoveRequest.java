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

    @NotNull(message = "Row cannot be null")
    @Min(value = 0, message = "Row must be at least 0")
    @Max(value = 14, message = "Row must be at most 14")
    private Integer row;

    @NotNull(message = "Column cannot be null")
    @Min(value = 0, message = "Column must be at least 0")
    @Max(value = 14, message = "Column must be at most 14")
    private Integer col;
}
