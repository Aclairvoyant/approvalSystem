use approval_system;
ALTER TABLE `applications`
  ADD COLUMN `voice_status` TINYINT NOT NULL DEFAULT 0 COMMENT '语音上传状态: 0=none,1=uploading,2=ready,3=failed' AFTER `voice_transcript`,
  ADD COLUMN `voice_upload_error` VARCHAR(500) NULL COMMENT '语音上传失败信息' AFTER `voice_status`;
