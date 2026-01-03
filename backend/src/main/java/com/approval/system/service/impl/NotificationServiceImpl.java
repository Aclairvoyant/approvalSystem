package com.approval.system.service.impl;

import com.approval.system.common.enums.NotifyTypeEnum;

import com.approval.system.common.utils.SmsUtils;
import com.approval.system.entity.Notification;
import com.approval.system.mapper.NotificationMapper;
import com.approval.system.service.IEmailService;
import com.approval.system.service.INotificationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements INotificationService {

    @Autowired
    private SmsUtils smsUtils;

    @Autowired
    private IEmailService emailService;

    @Override
    @Async
    public void sendSmsNotification(Long applicationId, Long userId, String phone, String title, String content) {
        try {
            // 创建通知并直接包含 phone
            Notification notification = Notification.builder()
                    .applicationId(applicationId)
                    .notifyUserId(userId)
                    .notifyType(NotifyTypeEnum.SMS.getCode())
                    .notifyTitle(title)
                    .notifyContent(content)
                    .phone(phone)
                    .sendStatus(1) // 1=待发送
                    .createdAt(LocalDateTime.now())
                    .build();
            this.save(notification);

            boolean success = smsUtils.sendSms(phone, title, content);

            if (success) {
                notification.setSendStatus(2); // 已发送
                notification.setSentAt(LocalDateTime.now());
            } else {
                notification.setSendStatus(3); // 发送失败
                notification.setSendError("短信发送失败");
            }

            this.updateById(notification);
        } catch (Exception e) {
            log.error("发送短信通知异常，userId: {}, phone: {}", userId, phone, e);
        }
    }

    @Override
    @Async
    public void sendEmailNotification(Long applicationId, Long userId, String email, String title, String content) {
        try {
            // 创建通知并直接包含 email
            Notification notification = Notification.builder()
                    .applicationId(applicationId)
                    .notifyUserId(userId)
                    .notifyType(NotifyTypeEnum.EMAIL.getCode())
                    .notifyTitle(title)
                    .notifyContent(content)
                    .email(email)
                    .sendStatus(1) // 1=待发送
                    .createdAt(LocalDateTime.now())
                    .build();
            this.save(notification);

            boolean success = emailService.sendEmail(email, title, content);

            if (success) {
                notification.setSendStatus(2); // 已发送
                notification.setSentAt(LocalDateTime.now());
            } else {
                notification.setSendStatus(3); // 发送失败
                notification.setSendError("邮件发送失败");
            }

            this.updateById(notification);
        } catch (Exception e) {
            log.error("发送邮件通知异常，userId: {}, email: {}", userId, email, e);
        }
    }

    @Override
    public void processPendingNotifications() {
        QueryWrapper<Notification> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("send_status", 1); // 1=待发送
        queryWrapper.le("created_at", LocalDateTime.now());

        List<Notification> notifications = this.list(queryWrapper);

        notifications.forEach(notification -> {
            try {
                boolean success = false;
                if (notification.getNotifyType().equals(NotifyTypeEnum.SMS.getCode())) {
                    success = smsUtils.sendSms(notification.getPhone(), notification.getNotifyTitle(), notification.getNotifyContent());
                } else if (notification.getNotifyType().equals(NotifyTypeEnum.EMAIL.getCode())) {
                    success = emailService.sendEmail(notification.getEmail(), notification.getNotifyTitle(), notification.getNotifyContent());
                }

                if (success) {
                    notification.setSendStatus(2); // 已发送
                    notification.setSentAt(LocalDateTime.now());
                } else {
                    notification.setSendStatus(3); // 发送失败
                    notification.setSendError("通知发送失败");
                }
                this.updateById(notification);
            } catch (Exception e) {
                log.error("处理待发送通知失败，notificationId: {}", notification.getId(), e);
                notification.setSendStatus(3);
                notification.setSendError(e.getMessage());
                this.updateById(notification);
            }
        });
    }

    @Override
    public Notification createNotification(Long applicationId, Long userId, Integer notifyType, String title, String content, String phone, String email) {
        Notification notification = Notification.builder()
                .applicationId(applicationId)
                .notifyUserId(userId)
                .notifyType(notifyType)
                .notifyTitle(title)
                .notifyContent(content)
                .phone(phone)
                .email(email)
                .sendStatus(1) // 1=待发送
                .createdAt(LocalDateTime.now())
                .build();

        this.save(notification);
        return notification;
    }

    @Override
    public Notification createSentNotification(Long applicationId, Long userId, Integer notifyType,
                                              String title, String content, String phone, String email, boolean success) {
        Notification notification = Notification.builder()
                .applicationId(applicationId)
                .notifyUserId(userId)
                .notifyType(notifyType)
                .notifyTitle(title)
                .notifyContent(content)
                .phone(phone)
                .email(email)
                .sendStatus(success ? 2 : 3) // 2=已发送, 3=发送失败
                .sentAt(success ? LocalDateTime.now() : null)
                .sendError(success ? null : "发送失败")
                .createdAt(LocalDateTime.now())
                .build();

        this.save(notification);
        return notification;
    }
}
