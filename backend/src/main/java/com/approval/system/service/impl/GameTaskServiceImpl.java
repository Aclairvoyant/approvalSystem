package com.approval.system.service.impl;

import com.approval.system.dto.GameTaskResponse;
import com.approval.system.dto.TaskCreateRequest;
import com.approval.system.entity.GameTask;
import com.approval.system.mapper.GameTaskMapper;
import com.approval.system.service.IGameTaskService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 游戏任务服务实现类
 */
@Slf4j
@Service
public class GameTaskServiceImpl extends ServiceImpl<GameTaskMapper, GameTask> implements IGameTaskService {

    private final Random random = new Random();

    @Override
    @Transactional
    public GameTaskResponse createCustomTask(Long userId, TaskCreateRequest request) {
        log.info("创建自定义任务: userId={}, title={}", userId, request.getTitle());

        GameTask task = GameTask.builder()
                .taskType(2)  // 自定义任务
                .creatorId(userId)
                .category(request.getCategory())
                .difficulty(request.getDifficulty())
                .title(request.getTitle())
                .description(request.getDescription())
                .requirement(request.getRequirement())
                .timeLimit(request.getTimeLimit())
                .points(request.getPoints() != null ? request.getPoints() : 10)
                .isActive(1)
                .usageCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        this.save(task);
        log.info("自定义任务创建成功: taskId={}", task.getId());

        return convertToResponse(task);
    }

    @Override
    public Page<GameTaskResponse> getPresetTasks(String category, Integer difficulty, Integer pageNum, Integer pageSize) {
        QueryWrapper<GameTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("task_type", 1);  // 预设任务
        queryWrapper.eq("is_active", 1);

        if (category != null && !category.isEmpty()) {
            queryWrapper.eq("category", category);
        }
        if (difficulty != null) {
            queryWrapper.eq("difficulty", difficulty);
        }
        queryWrapper.orderByDesc("usage_count");

        Page<GameTask> page = this.page(new Page<>(pageNum, pageSize), queryWrapper);

        Page<GameTaskResponse> responsePage = new Page<>(pageNum, pageSize, page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::convertToResponse).toList());

        return responsePage;
    }

    @Override
    public Page<GameTaskResponse> getUserCustomTasks(Long userId, Integer pageNum, Integer pageSize) {
        QueryWrapper<GameTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("task_type", 2);  // 自定义任务
        queryWrapper.eq("creator_id", userId);
        queryWrapper.eq("is_active", 1);
        queryWrapper.orderByDesc("created_at");

        Page<GameTask> page = this.page(new Page<>(pageNum, pageSize), queryWrapper);

        Page<GameTaskResponse> responsePage = new Page<>(pageNum, pageSize, page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::convertToResponse).toList());

        return responsePage;
    }

    @Override
    public GameTaskResponse getRandomTask(String category) {
        QueryWrapper<GameTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_active", 1);

        if (category != null && !category.isEmpty()) {
            queryWrapper.eq("category", category);
        }

        // 获取所有可用任务
        List<GameTask> tasks = this.list(queryWrapper);

        if (tasks.isEmpty()) {
            log.warn("没有可用的任务");
            return null;
        }

        // 随机选择一个任务
        GameTask selectedTask = tasks.get(random.nextInt(tasks.size()));
        log.info("随机选择任务: taskId={}, title={}", selectedTask.getId(), selectedTask.getTitle());

        return convertToResponse(selectedTask);
    }

    @Override
    public List<GameTaskResponse> getRandomTasks(int count) {
        QueryWrapper<GameTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_active", 1);

        // 获取所有可用任务
        List<GameTask> tasks = this.list(queryWrapper);

        if (tasks.isEmpty()) {
            log.warn("没有可用的任务");
            return List.of();
        }

        // 打乱任务列表
        java.util.Collections.shuffle(tasks, random);

        // 取指定数量（如果任务数不够，则全部返回并循环使用）
        List<GameTaskResponse> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            GameTask task = tasks.get(i % tasks.size());
            result.add(convertToResponse(task));
        }

        log.info("随机选择了 {} 个任务", result.size());
        return result;
    }

    @Override
    @Transactional
    public void deleteCustomTask(Long userId, Long taskId) {
        GameTask task = this.getById(taskId);

        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        // 只能删除自己创建的自定义任务
        if (!task.getCreatorId().equals(userId)) {
            throw new RuntimeException("无权删除该任务");
        }

        if (task.getTaskType() != 2) {
            throw new RuntimeException("不能删除预设任务");
        }

        // 软删除
        task.setIsActive(0);
        this.updateById(task);

        log.info("删除自定义任务: taskId={}, userId={}", taskId, userId);
    }

    @Override
    @Transactional
    public void incrementUsageCount(Long taskId) {
        GameTask task = this.getById(taskId);
        if (task != null) {
            task.setUsageCount(task.getUsageCount() + 1);
            this.updateById(task);
        }
    }

    @Override
    public GameTaskResponse getTaskById(Long taskId) {
        GameTask task = this.getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        return convertToResponse(task);
    }

    /**
     * 转换为响应对象
     */
    private GameTaskResponse convertToResponse(GameTask task) {
        return GameTaskResponse.builder()
                .id(task.getId())
                .taskType(task.getTaskType())
                .creatorId(task.getCreatorId())
                .category(task.getCategory())
                .difficulty(task.getDifficulty())
                .title(task.getTitle())
                .description(task.getDescription())
                .requirement(task.getRequirement())
                .timeLimit(task.getTimeLimit())
                .points(task.getPoints())
                .usageCount(task.getUsageCount())
                .createdAt(task.getCreatedAt())
                .build();
    }
}
