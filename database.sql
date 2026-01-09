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
  role TINYINT DEFAULT 0 COMMENT '角色：0=普通用户，1=管理员',
  voice_notification_enabled TINYINT DEFAULT 0 COMMENT '语音通知权限：0=禁用，1=启用',
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

-- ============================================
-- 游戏系统相关表
-- ============================================

-- 游戏表（飞行棋游戏）
CREATE TABLE games (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '游戏ID',
  game_code VARCHAR(20) NOT NULL UNIQUE COMMENT '游戏房间号（6位随机码）',
  player1_id BIGINT NOT NULL COMMENT '玩家1（房主）ID',
  player2_id BIGINT COMMENT '玩家2 ID（未加入时为NULL）',
  current_turn TINYINT DEFAULT 1 COMMENT '当前轮到谁：1=玩家1，2=玩家2',
  game_status TINYINT DEFAULT 1 COMMENT '游戏状态：1=等待加入，2=游戏中，3=已结束，4=已取消',
  winner_id BIGINT COMMENT '获胜者ID',
  board_data JSON COMMENT '棋盘状态（JSON格式存储所有棋子位置）',
  player1_pieces JSON COMMENT '玩家1的4个棋子位置数组 [0,0,0,0]',
  player2_pieces JSON COMMENT '玩家2的4个棋子位置数组 [0,0,0,0]',
  last_dice_result TINYINT COMMENT '最后一次骰子结果（1-6）',
  last_move_time DATETIME COMMENT '最后一次操作时间',
  task_positions JSON COMMENT '自定义任务位置数组，如 [5,10,15,20,25]',
  task_assignments JSON COMMENT '任务位置分配映射，如 {"5":1,"10":3}',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  started_at DATETIME COMMENT '游戏开始时间',
  ended_at DATETIME COMMENT '游戏结束时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  FOREIGN KEY (player1_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (player2_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (winner_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_game_code (game_code),
  INDEX idx_player1_id (player1_id),
  INDEX idx_player2_id (player2_id),
  INDEX idx_game_status (game_status),
  INDEX idx_created_at (created_at)
) COMMENT='飞行棋游戏表';

-- 游戏任务表（情侣升温任务）
CREATE TABLE game_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
  task_type TINYINT DEFAULT 1 COMMENT '任务类型：1=预设任务，2=用户自定义',
  creator_id BIGINT COMMENT '创建者ID（自定义任务时有值）',
  category VARCHAR(50) COMMENT '任务分类：romantic（浪漫）, fun（趣味）, challenge（挑战）, intimate（亲密）',
  difficulty TINYINT DEFAULT 1 COMMENT '难度等级：1=简单，2=中等，3=困难',
  title VARCHAR(255) NOT NULL COMMENT '任务标题',
  description TEXT COMMENT '任务描述',
  requirement TEXT COMMENT '完成要求',
  time_limit INT COMMENT '完成时限（分钟）',
  points INT DEFAULT 10 COMMENT '任务积分',
  is_active TINYINT DEFAULT 1 COMMENT '是否启用：1=启用，0=禁用',
  usage_count INT DEFAULT 0 COMMENT '被使用次数',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_task_type (task_type),
  INDEX idx_category (category),
  INDEX idx_creator_id (creator_id),
  INDEX idx_is_active (is_active)
) COMMENT='游戏任务表';

-- 游戏任务完成记录表
CREATE TABLE game_task_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  game_id BIGINT NOT NULL COMMENT '游戏ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  trigger_player_id BIGINT NOT NULL COMMENT '触发任务的玩家ID',
  executor_player_id BIGINT NOT NULL COMMENT '执行任务的玩家ID（可能与触发者不同）',
  task_status TINYINT DEFAULT 1 COMMENT '任务状态：1=进行中，2=已完成，3=已放弃，4=已超时',
  completion_note TEXT COMMENT '完成备注/说明',
  triggered_position INT COMMENT '触发任务时的棋盘位置',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
  completed_at DATETIME COMMENT '完成时间',
  FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
  FOREIGN KEY (task_id) REFERENCES game_tasks(id) ON DELETE CASCADE,
  FOREIGN KEY (trigger_player_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (executor_player_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_game_id (game_id),
  INDEX idx_task_id (task_id),
  INDEX idx_trigger_player_id (trigger_player_id)
) COMMENT='游戏任务完成记录表';

-- 游戏操作历史表
CREATE TABLE game_moves (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '操作ID',
  game_id BIGINT NOT NULL COMMENT '游戏ID',
  player_id BIGINT NOT NULL COMMENT '操作玩家ID',
  move_type TINYINT NOT NULL COMMENT '操作类型：1=掷骰子，2=移动棋子，3=吃子，4=触发任务，5=完成任务',
  dice_result TINYINT COMMENT '骰子结果（1-6）',
  piece_index TINYINT COMMENT '棋子索引（0-3）',
  from_position INT COMMENT '起始位置',
  to_position INT COMMENT '目标位置',
  captured_piece_index TINYINT COMMENT '被吃的对方棋子索引',
  task_id BIGINT COMMENT '触发的任务ID',
  move_data JSON COMMENT '操作详细数据（JSON格式）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (task_id) REFERENCES game_tasks(id) ON DELETE SET NULL,
  INDEX idx_game_id (game_id),
  INDEX idx_player_id (player_id),
  INDEX idx_created_at (created_at)
) COMMENT='游戏操作历史表';

-- 用户游戏统计表
CREATE TABLE user_game_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '统计ID',
  user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
  total_games INT DEFAULT 0 COMMENT '总游戏场次',
  win_count INT DEFAULT 0 COMMENT '胜利次数',
  lose_count INT DEFAULT 0 COMMENT '失败次数',
  draw_count INT DEFAULT 0 COMMENT '平局次数',
  total_tasks_completed INT DEFAULT 0 COMMENT '完成任务总数',
  total_points INT DEFAULT 0 COMMENT '累计积分',
  win_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '胜率（百分比）',
  favorite_task_category VARCHAR(50) COMMENT '最喜欢的任务分类',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_win_rate (win_rate),
  INDEX idx_total_points (total_points)
) COMMENT='用户游戏统计表';

-- 插入预设任务数据
INSERT INTO game_tasks (task_type, category, difficulty, title, description, requirement, points) VALUES
(1, 'romantic', 1, '说出三个喜欢对方的理由', '用心感受对方的美好', '真诚说出三个具体的理由', 10),
(1, 'romantic', 2, '唱一首情歌', '用歌声传递爱意', '完整唱一首情歌给对方听', 15),
(1, 'romantic', 2, '说出你们的三个美好回忆', '回忆过去的甜蜜时光', '具体描述三个难忘的瞬间', 15),
(1, 'romantic', 3, '写一首小诗表达爱意', '用文字表达内心的情感', '创作并朗读一首4句以上的小诗', 20),
(1, 'fun', 1, '模仿对方的口头禅', '观察对方的小习惯', '模仿3句对方的口头禅', 10),
(1, 'fun', 1, '做5个搞怪表情', '逗对方开心', '连续做5个夸张的表情', 10),
(1, 'fun', 2, '用三个词描述对方', '考验你对对方的了解', '选择三个最贴切的形容词', 15),
(1, 'fun', 2, '说出对方最喜欢的5样东西', '展示你的细心', '说出对方最爱的食物/电影/歌曲等', 15),
(1, 'challenge', 2, '15秒四目相对不笑', '考验彼此的默契', '对视15秒不笑场', 15),
(1, 'challenge', 2, '背诵对方的生日和重要纪念日', '考验记忆力', '准确说出至少3个重要日期', 15),
(1, 'challenge', 3, '用一句话总结你们的关系', '深度思考和表达', '用最精炼的语言表达你们的爱情', 20),
(1, 'intimate', 2, '给对方一个20秒的拥抱', '增进亲密关系', '拥抱20秒不松开', 15),
(1, 'intimate', 3, '给对方一个60秒的拥抱', '增进亲密关系', '拥抱60秒不松开', 20),
(1, 'intimate', 2, '亲吻对方的额头', '温柔的爱意表达', '轻轻亲吻对方的额头', 15),
(1, 'intimate', 3, '计划一次约会', '用心准备美好时光', '详细说出约会的时间、地点和活动安排', 20);

-- ============================================
-- 数据库升级脚本（用于已有数据库）
-- ============================================

-- 为games表添加任务配置字段（如果不存在）
use approval_system;
ALTER TABLE games ADD COLUMN task_positions JSON COMMENT '自定义任务位置数组，如 [5,10,15,20,25]';
ALTER TABLE games ADD COLUMN task_assignments JSON COMMENT '任务位置分配映射，如 {"5":1,"10":3}';

-- ============================================
-- 麻将游戏系统相关表
-- ============================================

-- 麻将游戏表
CREATE TABLE mahjong_games (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '游戏ID',
    game_code VARCHAR(8) NOT NULL UNIQUE COMMENT '房间号（6-8位随机码）',

    -- 游戏配置
    rule_type TINYINT NOT NULL DEFAULT 1 COMMENT '规则类型: 1=敲麻, 2=百搭',
    flower_mode TINYINT NOT NULL DEFAULT 8 COMMENT '花牌模式: 8=8花, 20=20花, 36=36花 (仅百搭)',
    player_count TINYINT NOT NULL DEFAULT 4 COMMENT '玩家人数: 2/3/4',
    total_rounds INT NOT NULL DEFAULT 8 COMMENT '总局数: 4/8/16',
    base_score INT NOT NULL DEFAULT 1 COMMENT '底分: 1/2/5/10',
    max_score INT NULL DEFAULT 100 COMMENT '封顶分数: 20/50/100/200/NULL=无封顶',
    fly_count TINYINT NOT NULL DEFAULT 0 COMMENT '飞苍蝇数量: 0-5',

    -- 百搭配置（百搭模式专用）
    wild_tile VARCHAR(10) NULL COMMENT '百搭牌 (如 "4WAN")',
    guide_tile VARCHAR(10) NULL COMMENT '前端引导牌',
    dice1 TINYINT NULL COMMENT '第一个骰子点数',
    dice2 TINYINT NULL COMMENT '第二个骰子点数',
    wall_start_seat TINYINT NULL COMMENT '开始取牌的玩家座位 1-4',
    wall_start_pos INT NULL COMMENT '牌墙起始位置',

    -- 玩家信息（座位1-4）
    player1_id BIGINT NOT NULL COMMENT '玩家1（房主）ID',
    player2_id BIGINT NULL COMMENT '玩家2 ID',
    player3_id BIGINT NULL COMMENT '玩家3 ID',
    player4_id BIGINT NULL COMMENT '玩家4 ID',

    -- 游戏状态
    game_status TINYINT NOT NULL DEFAULT 1 COMMENT '1=等待加入, 2=进行中, 3=已结束, 4=已取消',
    current_round INT NOT NULL DEFAULT 0 COMMENT '当前局数（0表示未开始）',
    dealer_seat TINYINT NOT NULL DEFAULT 1 COMMENT '当前庄家座位 1-4',

    -- 积分（累计）
    player1_score INT NOT NULL DEFAULT 0 COMMENT '玩家1累计积分',
    player2_score INT NOT NULL DEFAULT 0 COMMENT '玩家2累计积分',
    player3_score INT NOT NULL DEFAULT 0 COMMENT '玩家3累计积分',
    player4_score INT NOT NULL DEFAULT 0 COMMENT '玩家4累计积分',

    -- 时间戳
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    started_at DATETIME NULL COMMENT '游戏开始时间',
    ended_at DATETIME NULL COMMENT '游戏结束时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (player1_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (player2_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (player3_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (player4_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_mahjong_game_code (game_code),
    INDEX idx_mahjong_players (player1_id, player2_id, player3_id, player4_id),
    INDEX idx_mahjong_status (game_status),
    INDEX idx_mahjong_created (created_at)
) COMMENT='麻将游戏表';

-- 麻将单局记录表
CREATE TABLE mahjong_rounds (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '单局ID',
    game_id BIGINT NOT NULL COMMENT '所属游戏ID',
    round_number INT NOT NULL COMMENT '第几局',

    -- 局状态
    round_status TINYINT NOT NULL DEFAULT 1 COMMENT '1=进行中, 2=流局(荒番), 3=胡牌结束',
    dealer_seat TINYINT NOT NULL COMMENT '本局庄家座位 1-4',
    current_turn TINYINT NOT NULL DEFAULT 1 COMMENT '当前操作玩家座位 1-4',

    -- 牌局数据 (JSON格式)
    wall_tiles JSON COMMENT '牌墙剩余牌 ["1WAN","2WAN"...]',
    player1_hand JSON COMMENT '玩家1手牌',
    player2_hand JSON COMMENT '玩家2手牌',
    player3_hand JSON COMMENT '玩家3手牌',
    player4_hand JSON COMMENT '玩家4手牌',

    player1_melds JSON COMMENT '玩家1明牌(碰/杠) [{"type":"PONG","tiles":["1WAN","1WAN","1WAN"]}]',
    player2_melds JSON COMMENT '玩家2明牌',
    player3_melds JSON COMMENT '玩家3明牌',
    player4_melds JSON COMMENT '玩家4明牌',

    player1_discards JSON COMMENT '玩家1弃牌区',
    player2_discards JSON COMMENT '玩家2弃牌区',
    player3_discards JSON COMMENT '玩家3弃牌区',
    player4_discards JSON COMMENT '玩家4弃牌区',

    player1_flowers JSON COMMENT '玩家1花牌',
    player2_flowers JSON COMMENT '玩家2花牌',
    player3_flowers JSON COMMENT '玩家3花牌',
    player4_flowers JSON COMMENT '玩家4花牌',

    -- 最后操作信息
    last_tile VARCHAR(10) NULL COMMENT '最后打出/摸到的牌',
    last_action VARCHAR(20) NULL COMMENT '最后操作类型 DRAW/DISCARD/PONG/KONG/HU',
    last_action_seat TINYINT NULL COMMENT '最后操作者座位',

    -- 等待响应（碰/杠/胡）
    pending_actions JSON NULL COMMENT '待处理的响应操作 [{"seat":2,"actions":["PONG","HU"]}]',

    -- 结算信息
    winner_seat TINYINT NULL COMMENT '胡牌者座位（NULL=流局）',
    hu_type VARCHAR(200) NULL COMMENT '胡牌类型（可多个，逗号分隔）',
    fan_count INT NULL COMMENT '总番数',
    score_changes JSON COMMENT '分数变化 {"1":-10,"2":30,"3":-10,"4":-10}',

    -- 时间
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    ended_at DATETIME NULL COMMENT '结束时间',

    FOREIGN KEY (game_id) REFERENCES mahjong_games(id) ON DELETE CASCADE,
    INDEX idx_mahjong_round_game (game_id, round_number),
    INDEX idx_mahjong_round_status (round_status)
) COMMENT='麻将单局记录表';

-- 麻将操作记录表
CREATE TABLE mahjong_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '操作ID',
    round_id BIGINT NOT NULL COMMENT '所属单局ID',
    player_seat TINYINT NOT NULL COMMENT '操作者座位 1-4',
    action_type VARCHAR(20) NOT NULL COMMENT '操作类型: DRAW/DISCARD/PONG/MING_KONG/AN_KONG/BU_KONG/BU_HUA/HU/PASS',
    tile VARCHAR(10) NULL COMMENT '相关的牌 (如 "1WAN")',
    action_data JSON NULL COMMENT '额外数据 (如杠的详细信息)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    FOREIGN KEY (round_id) REFERENCES mahjong_rounds(id) ON DELETE CASCADE,
    INDEX idx_mahjong_action_round (round_id),
    INDEX idx_mahjong_action_time (created_at)
) COMMENT='麻将操作记录表';

-- 麻将用户统计表
CREATE TABLE mahjong_user_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '统计ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    total_games INT DEFAULT 0 COMMENT '总游戏场次',
    total_rounds INT DEFAULT 0 COMMENT '总局数',
    win_count INT DEFAULT 0 COMMENT '胡牌次数',
    self_draw_count INT DEFAULT 0 COMMENT '自摸次数',
    total_score INT DEFAULT 0 COMMENT '累计积分',
    max_fan INT DEFAULT 0 COMMENT '最高番数',
    four_wild_count INT DEFAULT 0 COMMENT '四百搭次数',
    no_wild_count INT DEFAULT 0 COMMENT '无百搭次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_mahjong_stats_score (total_score)
) COMMENT='麻将用户统计表';