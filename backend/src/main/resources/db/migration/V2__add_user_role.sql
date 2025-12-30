-- 添加用户角色字段
-- role: 0=普通用户, 1=管理员

ALTER TABLE users ADD COLUMN role TINYINT DEFAULT 0 COMMENT '用户角色: 0=普通用户, 1=管理员' AFTER avatar;

-- 设置第一个用户为管理员（如果需要）
UPDATE users SET role = 1 WHERE id = 1;
