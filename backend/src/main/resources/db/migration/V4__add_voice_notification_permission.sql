-- 添加语音通知权限字段
ALTER TABLE `users`
ADD COLUMN `voice_notification_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用语音通知权限（0=否，1=是）' AFTER `role`;

-- 为管理员用户默认开启语音通知权限
UPDATE `users` SET `voice_notification_enabled` = 1 WHERE `role` = 1;
