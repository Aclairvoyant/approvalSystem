package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.GameCreateRequest;
import com.approval.system.dto.GameJoinRequest;
import com.approval.system.dto.GameResponse;
import com.approval.system.entity.User;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IGameService;
import com.approval.system.websocket.GameMessage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 游戏控制器
 * 提供游戏创建、加入、查询等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/game")
@Tag(name = "游戏管理", description = "飞行棋游戏相关接口")
public class GameController {

    @Autowired
    private IGameService gameService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserMapper userMapper;

    /**
     * 创建游戏房间
     */
    @PostMapping("/create")
    @Operation(summary = "创建游戏房间", description = "创建一个新的飞行棋游戏房间，需指定对手，可自定义任务位置")
    public ApiResponse<GameResponse> createGame(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GameCreateRequest request) {
        log.info("创建游戏房间: userId={}, opponentId={}, taskPositions={}",
                userId, request.getOpponentUserId(), request.getTaskPositions());
        GameResponse response = gameService.createGame(userId, request.getOpponentUserId(), request.getTaskPositions());
        return ApiResponse.success(response);
    }

    /**
     * 更新任务位置配置
     */
    @PutMapping("/{gameId}/task-positions")
    @Operation(summary = "更新任务位置", description = "房主在等待状态可以修改任务位置配置")
    public ApiResponse<GameResponse> updateTaskPositions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @RequestBody java.util.List<Integer> taskPositions) {
        log.info("更新任务位置: userId={}, gameId={}, taskPositions={}", userId, gameId, taskPositions);
        GameResponse response = gameService.updateTaskPositions(gameId, userId, taskPositions);
        return ApiResponse.success(response);
    }

    /**
     * 加入游戏房间
     */
    @PostMapping("/join")
    @Operation(summary = "加入游戏房间", description = "通过房间号加入一个等待中的游戏")
    public ApiResponse<GameResponse> joinGame(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GameJoinRequest request) {
        log.info("加入游戏房间: userId={}, gameCode={}", userId, request.getGameCode());
        GameResponse response = gameService.joinGame(userId, request.getGameCode());

        // 发送WebSocket通知给房主
        User joiningUser = userMapper.selectById(userId);
        String playerName = joiningUser != null ? joiningUser.getRealName() : "玩家";
        GameMessage joinMessage = GameMessage.playerJoined(response.getId(), userId, playerName);
        messagingTemplate.convertAndSend("/topic/game/" + response.getId(), joinMessage);
        log.info("已发送玩家加入通知: gameId={}, playerId={}", response.getId(), userId);

        return ApiResponse.success(response);
    }

    /**
     * 获取游戏详情
     */
    @GetMapping("/{gameId}")
    @Operation(summary = "获取游戏详情", description = "获取指定游戏的详细信息")
    public ApiResponse<GameResponse> getGameDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        log.info("获取游戏详情: userId={}, gameId={}", userId, gameId);
        GameResponse response = gameService.getGameDetail(gameId, userId);
        return ApiResponse.success(response);
    }

    /**
     * 获取用户的游戏列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取游戏列表", description = "获取当前用户的游戏列表（进行中+历史）")
    public ApiResponse<Page<GameResponse>> getUserGames(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "游戏状态：1=等待，2=进行中，3=已结束，4=已取消")
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("获取游戏列表: userId={}, status={}", userId, status);
        Page<GameResponse> response = gameService.getUserGames(userId, status, pageNum, pageSize);
        return ApiResponse.success(response);
    }

    /**
     * 取消游戏
     */
    @PostMapping("/{gameId}/cancel")
    @Operation(summary = "取消游戏", description = "房主可以取消等待中的游戏")
    public ApiResponse<Void> cancelGame(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        log.info("取消游戏: userId={}, gameId={}", userId, gameId);
        gameService.cancelGame(gameId, userId);
        return ApiResponse.success();
    }

    /**
     * 强制结束游戏
     */
    @PostMapping("/{gameId}/force-end")
    @Operation(summary = "强制结束游戏", description = "房主可以强制结束进行中的游戏")
    public ApiResponse<Void> forceEndGame(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        log.info("强制结束游戏: userId={}, gameId={}", userId, gameId);
        gameService.forceEndGame(gameId, userId);
        return ApiResponse.success();
    }

    /**
     * 检查是否轮到玩家
     */
    @GetMapping("/{gameId}/turn")
    @Operation(summary = "检查轮次", description = "检查是否轮到当前玩家操作")
    public ApiResponse<Boolean> checkTurn(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        boolean isMyTurn = gameService.isPlayerTurn(gameId, userId);
        return ApiResponse.success(isMyTurn);
    }

    /**
     * 更新心跳
     */
    @PostMapping("/{gameId}/heartbeat")
    @Operation(summary = "心跳", description = "更新游戏最后操作时间")
    public ApiResponse<Void> heartbeat(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        // 验证玩家权限
        if (!gameService.validateGamePlayer(gameId, userId)) {
            return ApiResponse.fail("您不是这场游戏的参与者");
        }
        gameService.updateLastMoveTime(gameId);
        return ApiResponse.success();
    }
}
