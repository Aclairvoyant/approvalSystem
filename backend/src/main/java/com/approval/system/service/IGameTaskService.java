package com.approval.system.service;

import com.approval.system.dto.GameTaskResponse;
import com.approval.system.dto.TaskCreateRequest;
import com.approval.system.entity.GameTask;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 游戏任务服务接口
 */
public interface IGameTaskService extends IService<GameTask> {

    /**
     * 创建自定义任务
     */
    GameTaskResponse createCustomTask(Long userId, TaskCreateRequest request);

    /**
     * 获取预设任务列表
     */
    Page<GameTaskResponse> getPresetTasks(String category, Integer difficulty, Integer pageNum, Integer pageSize);

    /**
     * 获取用户的自定义任务
     */
    Page<GameTaskResponse> getUserCustomTasks(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 随机选择一个任务（用于触发特殊格子）
     */
    GameTaskResponse getRandomTask(String category);

    /**
     * 随机选择指定数量的任务（用于游戏开始时分配任务）
     */
    List<GameTaskResponse> getRandomTasks(int count);

    /**
     * 删除自定义任务
     */
    void deleteCustomTask(Long userId, Long taskId);

    /**
     * 更新任务使用次数
     */
    void incrementUsageCount(Long taskId);

    /**
     * 根据ID获取任务详情
     */
    GameTaskResponse getTaskById(Long taskId);
}
