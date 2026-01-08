package com.approval.system.service;

import com.approval.system.entity.GameTaskRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 游戏任务记录服务接口
 */
public interface IGameTaskRecordService extends IService<GameTaskRecord> {

    /**
     * 触发任务
     * @param gameId 游戏ID
     * @param taskId 任务ID
     * @param triggerPlayerId 触发任务的玩家ID
     * @param executorPlayerId 执行/确认任务的玩家ID（另一方）
     * @param position 触发位置
     * @return 任务记录，如果已有进行中的任务则返回null
     */
    GameTaskRecord triggerTask(Long gameId, Long taskId, Long triggerPlayerId, Long executorPlayerId, Integer position);

    /**
     * 完成任务
     */
    void completeTask(Long recordId, Long userId, String completionNote);

    /**
     * 放弃任务
     */
    void abandonTask(Long recordId, Long userId);

    /**
     * 获取游戏的任务记录
     */
    List<GameTaskRecord> getGameTaskRecords(Long gameId);

    /**
     * 获取当前进行中的任务
     */
    GameTaskRecord getCurrentTask(Long gameId);

    /**
     * 检查任务是否超时
     */
    void checkTaskTimeout(Long recordId);
}
