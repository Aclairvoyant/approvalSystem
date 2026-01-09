package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.*;
import com.approval.system.service.IMahjongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 麻将游戏控制器
 */
@Tag(name = "麻将游戏", description = "麻将游戏相关接口")
@RestController
@RequestMapping("/api/mahjong")
@RequiredArgsConstructor
public class MahjongController {

    private final IMahjongService mahjongService;

    @Operation(summary = "创建游戏房间")
    @PostMapping("/create")
    public ApiResponse<MahjongGameResponse> createGame(
            @Valid @RequestBody MahjongCreateRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.createGame(request, userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "加入游戏")
    @PostMapping("/join")
    public ApiResponse<MahjongGameResponse> joinGame(
            @Valid @RequestBody MahjongJoinRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.joinGame(request, userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "离开游戏")
    @PostMapping("/{gameId}/leave")
    public ApiResponse<Void> leaveGame(
            @PathVariable Long gameId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        mahjongService.leaveGame(gameId, userId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "开始游戏")
    @PostMapping("/{gameId}/start")
    public ApiResponse<MahjongGameResponse> startGame(
            @PathVariable Long gameId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.startGame(gameId, userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "获取游戏状态")
    @GetMapping("/{gameId}")
    public ApiResponse<MahjongGameResponse> getGameState(
            @PathVariable Long gameId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.getGameState(gameId, userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "通过房间号获取游戏")
    @GetMapping("/code/{gameCode}")
    public ApiResponse<MahjongGameResponse> getGameByCode(
            @PathVariable String gameCode,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.getGameByCode(gameCode, userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "执行玩家操作")
    @PostMapping("/{gameId}/action")
    public ApiResponse<MahjongGameResponse> executeAction(
            @PathVariable Long gameId,
            @Valid @RequestBody MahjongActionRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.executeAction(gameId, userId, request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "获取我的游戏列表")
    @GetMapping("/my-games")
    public ApiResponse<List<MahjongGameResponse>> getMyGames(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<MahjongGameResponse> games = mahjongService.getUserGames(userId);
        return ApiResponse.success(games);
    }

    @Operation(summary = "获取当前进行中的游戏")
    @GetMapping("/active")
    public ApiResponse<MahjongGameResponse> getActiveGame(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.getActiveGame(userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "开始下一局")
    @PostMapping("/{gameId}/next-round")
    public ApiResponse<MahjongGameResponse> nextRound(
            @PathVariable Long gameId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        MahjongGameResponse response = mahjongService.nextRound(gameId, userId);
        return ApiResponse.success(response);
    }
}
