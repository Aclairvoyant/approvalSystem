package com.approval.system.service.impl;

import com.approval.system.common.enums.TaskStatusEnum;
import com.approval.system.entity.GameTask;
import com.approval.system.entity.GameTaskRecord;
import com.approval.system.mapper.GameTaskRecordMapper;
import com.approval.system.service.IGameTaskRecordService;
import com.approval.system.service.IGameTaskService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏任务记录服务实现类
 */
@Slf4j
@Service
public class GameTaskRecordServiceImpl extends ServiceImpl<GameTaskRecordMapper, GameTaskRecord> implements IGameTaskRecordService {

    @Autowired
    private IGameTaskService gameTaskService;

    @Override
    @Transactional
    public GameTaskRecord triggerTask(Long gameId, Long taskId, Long triggerPlayerId, Long executorPlayerId, Integer position) {
        log.info("触发任务: gameId={}, taskId={}, triggerPlayerId={}, executorPlayerId={}, position={}",
                gameId, taskId, triggerPlayerId, executorPlayerId, position);

        // 检查是否有进行中的任务，如果有则跳过（不触发新任务）
        GameTaskRecord currentTask = getCurrentTask(gameId);
        if (currentTask != null) {
            log.warn("游戏已有进行中的任务，跳过新任务触发: recordId={}", currentTask.getId());
            return null;  // 返回null表示未触发新任务
        }

        // 创建任务记录
        // 执行者设为另一方玩家（由调用方传入）
        GameTaskRecord record = GameTaskRecord.builder()
                .gameId(gameId)
                .taskId(taskId)
                .triggerPlayerId(triggerPlayerId)
                .executorPlayerId(executorPlayerId)  // 另一方玩家执行/确认
                .taskStatus(TaskStatusEnum.IN_PROGRESS.getCode())
                .triggeredPosition(position)
                .createdAt(LocalDateTime.now())
                .build();

        this.save(record);

        // 增加任务使用次数
        gameTaskService.incrementUsageCount(taskId);

        log.info("任务记录创建成功: recordId={}", record.getId());
        return record;
    }

    @Override
    @Transactional
    public void completeTask(Long recordId, Long userId, String completionNote) {
        GameTaskRecord record = this.getById(recordId);

        if (record == null) {
            throw new RuntimeException("任务记录不存在");
        }

        if (!record.getTaskStatus().equals(TaskStatusEnum.IN_PROGRESS.getCode())) {
            throw new RuntimeException("任务已完成或已放弃");
        }

        // 验证是否是任务执行者
        if (!record.getExecutorPlayerId().equals(userId)) {
            throw new RuntimeException("只有任务执行者可以完成任务");
        }

        record.setTaskStatus(TaskStatusEnum.COMPLETED.getCode());
        record.setCompletionNote(completionNote);
        record.setCompletedAt(LocalDateTime.now());

        this.updateById(record);

        log.info("任务完成: recordId={}, userId={}", recordId, userId);
    }

    @Override
    @Transactional
    public void abandonTask(Long recordId, Long userId) {
        GameTaskRecord record = this.getById(recordId);

        if (record == null) {
            throw new RuntimeException("任务记录不存在");
        }

        if (!record.getTaskStatus().equals(TaskStatusEnum.IN_PROGRESS.getCode())) {
            throw new RuntimeException("任务已完成或已放弃");
        }

        // 验证是否是任务执行者
        if (!record.getExecutorPlayerId().equals(userId)) {
            throw new RuntimeException("只有任务执行者可以放弃任务");
        }

        record.setTaskStatus(TaskStatusEnum.ABANDONED.getCode());
        record.setCompletedAt(LocalDateTime.now());

        this.updateById(record);

        log.info("任务放弃: recordId={}, userId={}", recordId, userId);
    }

    @Override
    public List<GameTaskRecord> getGameTaskRecords(Long gameId) {
        QueryWrapper<GameTaskRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("game_id", gameId);
        queryWrapper.orderByDesc("created_at");

        return this.list(queryWrapper);
    }

    @Override
    public GameTaskRecord getCurrentTask(Long gameId) {
        QueryWrapper<GameTaskRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("game_id", gameId);
        queryWrapper.eq("task_status", TaskStatusEnum.IN_PROGRESS.getCode());

        return this.getOne(queryWrapper);
    }

    @Override
    @Transactional
    public void checkTaskTimeout(Long recordId) {
        GameTaskRecord record = this.getById(recordId);

        if (record == null || !record.getTaskStatus().equals(TaskStatusEnum.IN_PROGRESS.getCode())) {
            return;
        }

        // 获取任务的时间限制
        GameTask task = gameTaskService.getById(record.getTaskId());
        if (task == null || task.getTimeLimit() == null) {
            return;
        }

        // 检查是否超时
        LocalDateTime deadline = record.getCreatedAt().plusSeconds(task.getTimeLimit());
        if (LocalDateTime.now().isAfter(deadline)) {
            record.setTaskStatus(TaskStatusEnum.TIMEOUT.getCode());
            record.setCompletedAt(LocalDateTime.now());
            this.updateById(record);

            log.info("任务超时: recordId={}", recordId);
        }
    }
}
