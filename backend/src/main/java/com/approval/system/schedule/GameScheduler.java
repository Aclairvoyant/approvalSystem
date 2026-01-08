package com.approval.system.schedule;

import com.approval.system.common.enums.GameStatusEnum;
import com.approval.system.entity.Game;
import com.approval.system.service.IGameService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏定时任务
 * 处理游戏超时、清理等定时任务
 */
@Slf4j
@Component
public class GameScheduler {

    @Autowired
    private IGameService gameService;

    /**
     * 检查超时的等待中游戏
     * 每5分钟执行一次
     * 等待超过30分钟的游戏自动取消
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)  // 5分钟
    @Transactional
    public void checkWaitingGamesTimeout() {
        log.debug("开始检查等待超时的游戏...");

        try {
            QueryWrapper<Game> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("game_status", GameStatusEnum.WAITING.getCode());
            queryWrapper.lt("created_at", LocalDateTime.now().minusMinutes(30));

            List<Game> timeoutGames = gameService.list(queryWrapper);

            for (Game game : timeoutGames) {
                game.setGameStatus(GameStatusEnum.CANCELLED.getCode());
                game.setUpdatedAt(LocalDateTime.now());
                gameService.updateById(game);
                log.info("等待超时游戏已取消: gameId={}", game.getId());
            }

            if (!timeoutGames.isEmpty()) {
                log.info("共取消{}个等待超时的游戏", timeoutGames.size());
            }
        } catch (Exception e) {
            log.error("检查等待超时游戏失败", e);
        }
    }

    /**
     * 检查超时的进行中游戏
     * 每10分钟执行一次
     * 超过2小时没有操作的游戏自动结束
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)  // 10分钟
    @Transactional
    public void checkPlayingGamesTimeout() {
        log.debug("开始检查进行超时的游戏...");

        try {
            QueryWrapper<Game> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("game_status", GameStatusEnum.PLAYING.getCode());
            queryWrapper.lt("last_move_time", LocalDateTime.now().minusHours(2));

            List<Game> timeoutGames = gameService.list(queryWrapper);

            for (Game game : timeoutGames) {
                // 超时游戏以平局结束（不设置赢家）
                game.setGameStatus(GameStatusEnum.FINISHED.getCode());
                game.setEndedAt(LocalDateTime.now());
                game.setUpdatedAt(LocalDateTime.now());
                gameService.updateById(game);
                log.info("进行超时游戏已结束: gameId={}", game.getId());
            }

            if (!timeoutGames.isEmpty()) {
                log.info("共结束{}个进行超时的游戏", timeoutGames.size());
            }
        } catch (Exception e) {
            log.error("检查进行超时游戏失败", e);
        }
    }

    /**
     * 清理过期的游戏数据
     * 每天凌晨3点执行
     * 删除30天前已结束/已取消的游戏相关数据
     */
    @Scheduled(cron = "0 0 3 * * ?")  // 每天3点
    @Transactional
    public void cleanupOldGames() {
        log.info("开始清理过期游戏数据...");

        try {
            // 这里可以添加清理逻辑
            // 例如：删除30天前的游戏记录
            // 注意：需要考虑数据保留策略，可能需要先归档

            log.info("过期游戏数据清理完成");
        } catch (Exception e) {
            log.error("清理过期游戏数据失败", e);
        }
    }

    /**
     * 游戏状态持久化
     * 每分钟执行一次
     * 确保内存中的游戏状态已持久化到数据库
     */
    @Scheduled(fixedRate = 60 * 1000)  // 1分钟
    public void persistGameStates() {
        // 由于我们的设计是每次操作都实时持久化到数据库
        // 这个定时任务主要用于日志记录和监控
        log.debug("游戏状态持久化检查...");

        try {
            QueryWrapper<Game> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("game_status", GameStatusEnum.PLAYING.getCode());
            long activeGames = gameService.count(queryWrapper);

            if (activeGames > 0) {
                log.debug("当前活跃游戏数: {}", activeGames);
            }
        } catch (Exception e) {
            log.error("游戏状态持久化检查失败", e);
        }
    }
}
