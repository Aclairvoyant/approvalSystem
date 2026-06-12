use approval_system
ALTER TABLE `applications`
  ADD COLUMN `app_type` TINYINT NOT NULL DEFAULT 1 COMMENT '申请类型：1=普通申请，2=语音申请' AFTER `approver_id`,
  ADD COLUMN `voice_transcript` LONGTEXT NULL COMMENT '语音申请ASR转写文本' AFTER `remark`;
