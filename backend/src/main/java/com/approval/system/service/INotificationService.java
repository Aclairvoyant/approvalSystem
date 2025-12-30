package com.approval.system.service;

import com.approval.system.entity.Notification;

public interface INotificationService {

    /**
     * 发送短信通知
     */
    void sendSmsNotification(Long applicationId, Long userId, String phone, String title, String content);

    /**
     * 发送邮件通知
     */
    void sendEmailNotification(Long applicationId, Long userId, String email, String title, String content);

    /**
     * 异步发送待处理的通知
     */
    void processPendingNotifications();

    /**
     * 创建通知记录
     */
    Notification createNotification(Long applicationId, Long userId, Integer notifyType, String title, String content);
}
