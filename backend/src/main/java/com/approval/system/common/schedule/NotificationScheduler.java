package com.approval.system.common.schedule;

import com.approval.system.service.INotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationScheduler {

    @Autowired
    private INotificationService notificationService;

    /**
     * 定时处理待发送的通知 - 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void processPendingNotifications() {
        try {
            log.debug("开始处理待发送的通知");
            notificationService.processPendingNotifications();
        } catch (Exception e) {
            log.error("处理待发送通知异常", e);
        }
    }
}
