package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.dto.MahjongCreateRequest;
import com.approval.system.dto.MahjongInviteRequest;
import com.approval.system.dto.MahjongJoinRequest;
import com.approval.system.dto.MahjongRoomResponse;
import com.approval.system.dto.MahjongSeatRequest;
import com.approval.system.service.IMahjongService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mahjong")
@Tag(name = "上海麻将", description = "百搭 / 敲麻房间与对局")
public class MahjongController {

    private final IMahjongService mahjongService;

    public MahjongController(IMahjongService mahjongService) {
        this.mahjongService = mahjongService;
    }

    @PostMapping("/create")
    @Operation(summary = "开房：选玩法、座位数和封顶")
    public ApiResponse<MahjongRoomResponse> createRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MahjongCreateRequest request) {
        return ApiResponse.success(mahjongService.createRoom(userId, request));
    }

    @PostMapping("/join")
    @Operation(summary = "凭房间号加入（不要求互为对象）")
    public ApiResponse<MahjongRoomResponse> joinRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MahjongJoinRequest request) {
        return ApiResponse.success(mahjongService.joinRoom(userId, request.getGameCode()));
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "房间详情（只含自己的手牌）")
    public ApiResponse<MahjongRoomResponse> getRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        return ApiResponse.success(mahjongService.getRoom(gameId, userId));
    }

    @GetMapping("/list")
    @Operation(summary = "我参与或被邀请的麻将房间")
    public ApiResponse<Page<MahjongRoomResponse>> listRooms(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "状态：1=等待，2=对局中，3=本局结束，4=已取消")
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(mahjongService.listRooms(userId, status, pageNum, pageSize));
    }

    @PostMapping("/{gameId}/seat")
    @Operation(summary = "房主把空位设成电脑或改回空位")
    public ApiResponse<MahjongRoomResponse> setSeat(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @Valid @RequestBody MahjongSeatRequest request) {
        return ApiResponse.success(mahjongService.setSeat(gameId, userId, request.getSeatIndex(), request.getBot()));
    }

    @PostMapping("/{gameId}/invite")
    @Operation(summary = "房主直接邀请自己的对象")
    public ApiResponse<MahjongRoomResponse> invite(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @Valid @RequestBody MahjongInviteRequest request) {
        return ApiResponse.success(mahjongService.invite(gameId, userId, request.getUserId()));
    }

    @PostMapping("/{gameId}/leave")
    @Operation(summary = "开局前离开座位")
    public ApiResponse<Void> leaveRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        mahjongService.leaveRoom(gameId, userId);
        return ApiResponse.success();
    }

    @PostMapping("/{gameId}/start")
    @Operation(summary = "房主开局（所有座位都要有人或电脑）")
    public ApiResponse<MahjongRoomResponse> startGame(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        return ApiResponse.success(mahjongService.startGame(gameId, userId));
    }

    @PostMapping("/{gameId}/next-round")
    @Operation(summary = "再来一局：庄家顺延，总分累计")
    public ApiResponse<MahjongRoomResponse> nextRound(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        return ApiResponse.success(mahjongService.nextRound(gameId, userId));
    }

    @PostMapping("/{gameId}/cancel")
    @Operation(summary = "房主取消还没开局的房间")
    public ApiResponse<Void> cancelRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId) {
        mahjongService.cancelRoom(gameId, userId);
        return ApiResponse.success();
    }

    @PostMapping("/{gameId}/action")
    @Operation(summary = "牌局操作（实时连接断开时的备用入口，平时走 STOMP）")
    public ApiResponse<MahjongRoomResponse> act(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gameId,
            @Valid @RequestBody MahjongActionRequest request) {
        return ApiResponse.success(mahjongService.act(gameId, userId, request));
    }
}
