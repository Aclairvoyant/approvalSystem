package com.approval.system.schedule;

import com.approval.system.entity.MahjongGame;
import com.approval.system.service.IMahjongService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 麻将的自动行动：电脑、托管、已敲的座位约 1 秒后出牌；真人超时后代打。
 * <p>
 * 每次状态变化后按截止时间排一个任务（同一房间只保留最新的一个），任务带着当时的状态版本，
 * 执行时版本对不上（有人先操作了）就什么都不做。服务重启会丢掉内存里的任务，
 * 所以另有一个定时兜底，把数据库里已经过期的房间补跑一遍。
 * <p>
 * 用自己的线程池而不是注册 TaskScheduler bean，免得影响项目里其他 @Scheduled 任务用哪个调度器。
 */
@Slf4j
@Component
public class MahjongAutoPlayScheduler {

    private final IMahjongService mahjongService;
    private final ScheduledExecutorService executor;
    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public MahjongAutoPlayScheduler(IMahjongService mahjongService) {
        this.mahjongService = mahjongService;
        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "mahjong-auto-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void schedule(Long gameId, int version, long runAtMillis) {
        long delay = Math.max(0, runAtMillis - System.currentTimeMillis());
        ScheduledFuture<?> future = executor.schedule(() -> run(gameId, version), delay, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = pending.put(gameId, future);
        if (previous != null) {
            previous.cancel(false);
        }
        pending.values().removeIf(ScheduledFuture::isDone);
    }

    /** 服务重启或任务丢失时，补跑已经过期的房间 */
    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void runOverdueGames() {
        try {
            for (MahjongGame game : mahjongService.findOverdueGames()) {
                run(game.getId(), game.getStateVersion());
            }
        } catch (Exception e) {
            log.warn("麻将超时检查失败", e);
        }
    }

    void run(Long gameId, int version) {
        try {
            mahjongService.runScheduledStep(gameId, version);
        } catch (Exception e) {
            log.warn("麻将自动行动失败: gameId={}, version={}", gameId, version, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
