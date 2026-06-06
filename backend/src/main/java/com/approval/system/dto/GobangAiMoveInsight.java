package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GobangAiMoveInsight {

    private Integer moveNumber;
    private Integer playerColor;
    private Integer rowIndex;
    private Integer colIndex;
    private Integer winRate;
    private Integer bestMoveWinRate;
    private String severity;
    private String summary;

    @Builder.Default
    private List<GobangAiPoint> suggestedPoints = new ArrayList<>();
}
