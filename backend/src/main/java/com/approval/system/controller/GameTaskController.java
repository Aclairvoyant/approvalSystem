package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.GameTaskResponse;
import com.approval.system.dto.TaskCompleteRequest;
import com.approval.system.dto.TaskCreateRequest;
import com.approval.system.entity.GameTaskRecord;
import com.approval.system.service.IGameTaskRecordService;
import com.approval.system.service.IGameTaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 游戏任务控制器
 * 提供任务管理相关REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/game/task")
@Tag(name = "游戏任务管理", description = "飞行棋游戏任务相关接口")
public class GameTaskController {

    @Autowired
    private IGameTaskService gameTaskService;

    @Autowired
    private IGameTaskRecordService gameTaskRecordService;

    /**
     * 创建自定义任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建自定义任务", description = "创建用户自定义的游戏任务")
    public ApiResponse<GameTaskResponse> createCustomTask(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TaskCreateRequest request) {
        log.info("创建自定义任务: userId={}, title={}", userId, request.getTitle());
        GameTaskResponse response = gameTaskService.createCustomTask(userId, request);
        return ApiResponse.success(response);
    }

    /**
     * 获取预设任务列表
     */
    @GetMapping("/preset")
    @Operation(summary = "获取预设任务", description = "获取系统预设的游戏任务列表")
    public ApiResponse<Page<GameTaskResponse>> getPresetTasks(
            @Parameter(description = "任务类别：romantic, fun, challenge, intimate")
            @RequestParam(required = false) String category,
            @Parameter(description = "难度等级：1-3")
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("获取预设任务: category={}, difficulty={}", category, difficulty);
        Page<GameTaskResponse> response = gameTaskService.getPresetTasks(category, difficulty, pageNum, pageSize);
        return ApiResponse.success(response);
    }

    /**
     * 获取用户自定义任务列表
     */
    @GetMapping("/custom")
    @Operation(summary = "获取自定义任务", description = "获取当前用户创建的自定义任务列表")
    public ApiResponse<Page<GameTaskResponse>> getUserCustomTasks(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("获取用户自定义任务: userId={}", userId);
        Page<GameTaskResponse> response = gameTaskService.getUserCustomTasks(userId, pageNum, pageSize);
        return ApiResponse.success(response);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取任务详情", description = "获取指定任务的详细信息")
    public ApiResponse<GameTaskResponse> getTaskById(@PathVariable Long taskId) {
        log.info("获取任务详情: taskId={}", taskId);
        GameTaskResponse response = gameTaskService.getTaskById(taskId);
        return ApiResponse.success(response);
    }

    /**
     * 删除自定义任务
     */
    @DeleteMapping("/{taskId}")
    @Operation(summary = "删除自定义任务", description = "删除用户创建的自定义任务")
    public ApiResponse<Void> deleteCustomTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long taskId) {
        log.info("删除自定义任务: userId={}, taskId={}", userId, taskId);
        gameTaskService.deleteCustomTask(userId, taskId);
        return ApiResponse.success();
    }

    /**
     * 获取随机任务
     */
    @GetMapping("/random")
    @Operation(summary = "获取随机任务", description = "随机获取一个任务（用于游戏中触发）")
    public ApiResponse<GameTaskResponse> getRandomTask(
            @Parameter(description = "任务类别（可选）")
            @RequestParam(required = false) String category) {
        log.info("获取随机任务: category={}", category);
        GameTaskResponse response = gameTaskService.getRandomTask(category);
        return ApiResponse.success(response);
    }

    /**
     * 完成任务
     */
    @PostMapping("/record/{recordId}/complete")
    @Operation(summary = "完成任务", description = "标记任务为已完成")
    public ApiResponse<Void> completeTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId,
            @RequestBody(required = false) TaskCompleteRequest request) {
        log.info("完成任务: userId={}, recordId={}", userId, recordId);
        String completionNote = request != null ? request.getCompletionNote() : null;
        gameTaskRecordService.completeTask(recordId, userId, completionNote);
        return ApiResponse.success();
    }

    /**
     * 放弃任务
     */
    @PostMapping("/record/{recordId}/abandon")
    @Operation(summary = "放弃任务", description = "放弃当前任务")
    public ApiResponse<Void> abandonTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId) {
        log.info("放弃任务: userId={}, recordId={}", userId, recordId);
        gameTaskRecordService.abandonTask(recordId, userId);
        return ApiResponse.success();
    }

    /**
     * 获取游戏的任务记录
     */
    @GetMapping("/record/game/{gameId}")
    @Operation(summary = "获取游戏任务记录", description = "获取指定游戏的所有任务记录")
    public ApiResponse<List<GameTaskRecord>> getGameTaskRecords(@PathVariable Long gameId) {
        log.info("获取游戏任务记录: gameId={}", gameId);
        List<GameTaskRecord> records = gameTaskRecordService.getGameTaskRecords(gameId);
        return ApiResponse.success(records);
    }

    /**
     * 获取当前进行中的任务
     */
    @GetMapping("/record/game/{gameId}/current")
    @Operation(summary = "获取当前任务", description = "获取游戏当前进行中的任务")
    public ApiResponse<GameTaskRecord> getCurrentTask(@PathVariable Long gameId) {
        log.info("获取当前任务: gameId={}", gameId);
        GameTaskRecord record = gameTaskRecordService.getCurrentTask(gameId);
        return ApiResponse.success(record);
    }
}
