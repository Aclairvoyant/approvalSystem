package com.approval.system.service;

import com.approval.system.entity.Application;
import com.approval.system.entity.Notification;
import com.baomidou.mybatisplus.extension.service.IService;

public interface INotificationService extends IService<Notification> {

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
     * 创建通知记录（待发送状态）
     *
     * @param applicationId 申请ID
     * @param userId 用户ID
     * @param notifyType 通知类型
     * @param title 标题
     * @param content 内容
     * @param phone 手机号(可选)
     * @param email 邮箱(可选)
     * @return 通知记录
     */
    Notification createNotification(Long applicationId, Long userId, Integer notifyType, String title, String content, String phone, String email);

    /**
     * 创建已发送的通知记录
     *
     * @param applicationId 申请ID
     * @param userId 用户ID
     * @param notifyType 通知类型
     * @param title 标题
     * @param content 内容
     * @param phone 手机号(可选)
     * @param email 邮箱(可选)
     * @param success 是否发送成功
     * @return 通知记录
     */
    Notification createSentNotification(Long applicationId, Long userId, Integer notifyType,
                                       String title, String content, String phone, String email, boolean success);
}
