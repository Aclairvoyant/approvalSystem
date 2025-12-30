package com.approval.system.service.impl;

import com.approval.system.common.enums.NotifyTypeEnum;

import com.approval.system.common.utils.SmsUtils;
import com.approval.system.entity.Notification;
import com.approval.system.mapper.NotificationMapper;
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


    @Override
    @Async
    public void sendSmsNotification(Long applicationId, Long userId, String phone, String title, String content) {
        try {
            Notification notification = createNotification(applicationId, userId, NotifyTypeEnum.SMS.getCode(), title, content);
            notification.setPhone(phone);

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
            Notification notification = createNotification(applicationId, userId, NotifyTypeEnum.EMAIL.getCode(), title, content);
            notification.setEmail(email);

            // boolean success = emailUtils.sendEmail(email, title, content);

//            if (success) {
//                notification.setSendStatus(2); // 已发送
//                notification.setSentAt(LocalDateTime.now());
//            } else {
//                notification.setSendStatus(3); // 发送失败
//                notification.setSendError("邮件发送失败");
//            }

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
                if (notification.getNotifyType().equals(NotifyTypeEnum.SMS.getCode())) {
                    smsUtils.sendSms(notification.getPhone(), notification.getNotifyTitle(), notification.getNotifyContent());
                } else if (notification.getNotifyType().equals(NotifyTypeEnum.EMAIL.getCode())) {
                    //emailUtils.sendEmail(notification.getEmail(), notification.getNotifyTitle(), notification.getNotifyContent());
                }

                notification.setSendStatus(2);
                notification.setSentAt(LocalDateTime.now());
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
    public Notification createNotification(Long applicationId, Long userId, Integer notifyType, String title, String content) {
        Notification notification = Notification.builder()
                .applicationId(applicationId)
                .notifyUserId(userId)
                .notifyType(notifyType)
                .notifyTitle(title)
                .notifyContent(content)
                .sendStatus(1) // 1=待发送
                .createdAt(LocalDateTime.now())
                .build();

        this.save(notification);
        return notification;
    }
}
