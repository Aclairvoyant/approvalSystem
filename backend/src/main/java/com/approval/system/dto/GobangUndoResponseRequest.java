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
public class GobangUndoResponseRequest {

    @NotNull(message = "请选择是否同意悔棋")
    private Boolean accepted;
}
