package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GobangAiPoint {

    private Integer row;
    private Integer col;
    private String label;
    private String type;
    private Integer score;
}
