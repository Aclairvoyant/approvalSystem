package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GobangAiReviewResponse {

    private Long gameId;
    private String source;
    private String overview;

    @Builder.Default
    private List<GobangAiMoveInsight> insights = new ArrayList<>();

    private LocalDateTime generatedAt;
}
