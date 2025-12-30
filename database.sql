-- 创建数据库
CREATE DATABASE IF NOT EXISTS approval_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE approval_system;

-- 用户表
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  phone VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号',
  email VARCHAR(100) COMMENT '邮箱',
  password VARCHAR(255) NOT NULL COMMENT '密码（加密）',
  real_name VARCHAR(50) COMMENT '真实姓名',
  avatar VARCHAR(255) COMMENT '头像URL',
  status TINYINT DEFAULT 1 COMMENT '用户状态：1=正常，0=禁用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_username (username),
  INDEX idx_phone (phone)
) COMMENT='用户表';

-- 用户关系表（双向对象关系）
CREATE TABLE user_relations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系ID',
  user_id BIGINT NOT NULL COMMENT '用户A的ID',
  related_user_id BIGINT NOT NULL COMMENT '用户B的ID',
  relation_type TINYINT DEFAULT 1 COMMENT '关系类型：1=申请中，2=已互为对象',
  requester_id BIGINT NOT NULL COMMENT '谁发起的申请：user_id或related_user_id',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY unique_relation (user_id, related_user_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (related_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user_id (user_id),
  INDEX idx_related_user_id (related_user_id),
  INDEX idx_relation_type (relation_type)
) COMMENT='用户关系表';

-- 申请单表
CREATE TABLE applications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
  applicant_id BIGINT NOT NULL COMMENT '申请人ID',
  approver_id BIGINT NOT NULL COMMENT '审批人ID',
  title VARCHAR(255) NOT NULL COMMENT '事项标题',
  description LONGTEXT COMMENT '事项描述/理由',
  remark VARCHAR(500) COMMENT '备注',
  status TINYINT DEFAULT 1 COMMENT '申请状态：1=待审批，2=已批准，3=已驳回，4=草稿',
  reject_reason VARCHAR(500) COMMENT '驳回原因',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  approved_at DATETIME COMMENT '审批时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  FOREIGN KEY (applicant_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (approver_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_applicant_id (applicant_id),
  INDEX idx_approver_id (approver_id),
  INDEX idx_status (status),
  INDEX idx_created_at (created_at)
) COMMENT='申请单表';

-- 通知表（短信和邮件记录）
CREATE TABLE notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通知ID',
  application_id BIGINT NOT NULL COMMENT '申请ID',
  notify_user_id BIGINT NOT NULL COMMENT '接收通知的用户ID',
  notify_type TINYINT NOT NULL COMMENT '通知方式：1=短信，2=邮件，3=APP推送',
  notify_title VARCHAR(255) COMMENT '通知标题',
  notify_content LONGTEXT COMMENT '通知内容',
  phone VARCHAR(20) COMMENT '接收的手机号',
  email VARCHAR(100) COMMENT '接收的邮箱',
  send_status TINYINT DEFAULT 1 COMMENT '发送状态：1=待发送，2=已发送，3=发送失败',
  send_error VARCHAR(500) COMMENT '发送失败原因',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  sent_at DATETIME COMMENT '发送时间',
  FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
  FOREIGN KEY (notify_user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_notify_user_id (notify_user_id),
  INDEX idx_send_status (send_status),
  INDEX idx_created_at (created_at)
) COMMENT='通知记录表';

-- 操作日志表（用于流程时间线记录）
CREATE TABLE operation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
  application_id BIGINT NOT NULL COMMENT '申请ID',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operation_type TINYINT NOT NULL COMMENT '操作类型：1=创建，2=审批通过，3=驳回，4=修改，5=取消',
  old_status TINYINT COMMENT '旧状态',
  new_status TINYINT COMMENT '新状态',
  operation_detail VARCHAR(500) COMMENT '操作详情/备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
  FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_application_id (application_id),
  INDEX idx_created_at (created_at)
) COMMENT='操作日志表';

-- 申请附件表（存储图片等附件）
CREATE TABLE application_attachments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '附件ID',
  application_id BIGINT NOT NULL COMMENT '申请ID',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_url VARCHAR(500) NOT NULL COMMENT '文件URL（OSS地址）',
  file_type VARCHAR(50) COMMENT '文件类型',
  file_size BIGINT COMMENT '文件大小（字节）',
  oss_key VARCHAR(500) COMMENT 'OSS中的对象键',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
  INDEX idx_application_id (application_id),
  INDEX idx_created_at (created_at)
) COMMENT='申请附件表';

-- 审批附件表（存储审批时上传的图片）
CREATE TABLE approval_attachments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '附件ID',
  application_id BIGINT NOT NULL COMMENT '申请ID',
  operation_log_id BIGINT COMMENT '操作日志ID',
  operator_id BIGINT NOT NULL COMMENT '上传者ID',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_url VARCHAR(500) NOT NULL COMMENT '文件URL（OSS地址）',
  file_type VARCHAR(50) COMMENT '文件类型',
  file_size BIGINT COMMENT '文件大小（字节）',
  oss_key VARCHAR(500) COMMENT 'OSS中的对象键',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
  FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_application_id (application_id),
  INDEX idx_operator_id (operator_id),
  INDEX idx_created_at (created_at)
) COMMENT='审批附件表';

-- 创建索引以提高查询性能
CREATE INDEX idx_applications_applicant_approver ON applications(applicant_id, approver_id);
CREATE INDEX idx_applications_approver_status ON applications(approver_id, status);