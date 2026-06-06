package com.approval.system.service;

import com.approval.system.dto.GobangAiReviewResponse;

public interface IGobangReviewService {

    GobangAiReviewResponse reviewGame(Long gameId, Long userId);

    GobangAiReviewResponse getSavedReview(Long gameId, Long userId);
}
