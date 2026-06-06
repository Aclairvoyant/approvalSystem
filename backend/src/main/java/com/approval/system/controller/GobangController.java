package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.GobangAiReviewResponse;
import com.approval.system.dto.GobangCreateRequest;
import com.approval.system.dto.GobangJoinRequest;
import com.approval.system.dto.GobangMoveResponse;
import com.approval.system.dto.GobangResponse;
import com.approval.system.service.IGobangService;
import com.approval.system.service.IGobangReviewService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gobang")
@Tag(name = "五子棋管理", description = "五子棋房间、对局和回放接口")
public class GobangController {

    private final IGobangService gobangService;
    private final IGobangReviewService gobangReviewService;

    public GobangController(IGobangService gobangService, IGobangReviewService gobangReviewService) {
        this.gobangService = gobangService;
        this.gobangReviewService = gobangReviewService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建五子棋房间")
    public ApiResponse<GobangResponse> createGame(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GobangCreateRequest request) {
        return ApiResponse.success(gobangService.createGame(userId, request.getOpponentUserId()));
    }

    @PostMapping("/join")
    @Operation(summary = "加入五子棋房间")
    public ApiResponse<GobangResponse> joinGame(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GobangJoinRequest request) {
        return ApiResponse.success(gobangService.joinGame(userId, request.getGameCode()));
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "获取五子棋详情")
    public ApiResponse<GobangResponse> getGameDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        return ApiResponse.success(gobangService.getGameDetail(gameId, userId));
    }

    @GetMapping("/list")
    @Operation(summary = "获取五子棋列表")
    public ApiResponse<Page<GobangResponse>> getUserGames(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "状态：1=等待，2=进行中，3=已结束，4=已取消")
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(gobangService.getUserGames(userId, status, pageNum, pageSize));
    }

    @GetMapping("/{gameId}/moves")
    @Operation(summary = "获取五子棋回放动作")
    public ApiResponse<List<GobangMoveResponse>> getMoves(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        return ApiResponse.success(gobangService.getMoves(gameId, userId));
    }

    @GetMapping("/{gameId}/ai-review")
    @Operation(summary = "获取五子棋 AI 复盘建议")
    public ApiResponse<GobangAiReviewResponse> getAiReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        return ApiResponse.success(gobangReviewService.reviewGame(gameId, userId));
    }

    @GetMapping("/{gameId}/ai-review/saved")
    @Operation(summary = "获取已保存的五子棋 AI 复盘建议")
    public ApiResponse<GobangAiReviewResponse> getSavedAiReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        return ApiResponse.success(gobangReviewService.getSavedReview(gameId, userId));
    }

    @PostMapping("/{gameId}/cancel")
    @Operation(summary = "取消等待中的五子棋房间")
    public ApiResponse<Void> cancelGame(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        gobangService.cancelGame(gameId, userId);
        return ApiResponse.success();
    }
}
