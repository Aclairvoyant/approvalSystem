USE approval_system;

-- 上海麻将（百搭 / 敲麻）房间。每桌 2-4 个座位，房主固定坐 0 号位；
-- state_data 存当前（或刚结束的）一局完整牌局，含牌墙和所有人的暗牌，只能在服务端使用。
CREATE TABLE IF NOT EXISTS `mahjong_games` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `game_code` VARCHAR(20) NOT NULL COMMENT '6 位房间号',
  `owner_id` BIGINT NOT NULL COMMENT '房主',
  `mode` TINYINT NOT NULL COMMENT '1=百搭,2=敲麻',
  `seat_count` TINYINT NOT NULL COMMENT '座位数 2-4',
  `seat0_user_id` BIGINT NULL COMMENT '0 号位真人玩家（房主）',
  `seat1_user_id` BIGINT NULL COMMENT '1 号位真人玩家',
  `seat2_user_id` BIGINT NULL COMMENT '2 号位真人玩家',
  `seat3_user_id` BIGINT NULL COMMENT '3 号位真人玩家',
  `bot_mask` TINYINT NOT NULL DEFAULT 0 COMMENT '电脑座位位图：第 i 位为 1 表示 i 号位是电脑',
  `invited_user_id` BIGINT NULL COMMENT '房主直接邀请的对象',
  `cap_points` INT NOT NULL DEFAULT 128 COMMENT '每个付分人每局最多付多少分，0=不封顶',
  `game_status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=waiting,2=playing,3=round finished,4=cancelled',
  `round_no` INT NOT NULL DEFAULT 0 COMMENT '已开到第几局',
  `dealer_seat` TINYINT NOT NULL DEFAULT 0 COMMENT '当前（或下一局之前那局）的庄家座位',
  `huang_count` INT NOT NULL DEFAULT 0 COMMENT '下一局带的荒番数',
  `total_scores` JSON NULL COMMENT '每个座位的累计得分',
  `state_data` JSON NULL COMMENT '完整牌局状态（含隐藏信息）',
  `state_version` INT NOT NULL DEFAULT 0 COMMENT '状态版本，每次变化 +1',
  `action_deadline` DATETIME(3) NULL COMMENT '下一次自动行动或超时代打的时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` DATETIME NULL,
  `ended_at` DATETIME NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_mahjong_game_code` (`game_code`),
  INDEX `idx_mahjong_owner` (`owner_id`),
  INDEX `idx_mahjong_seat0` (`seat0_user_id`),
  INDEX `idx_mahjong_seat1` (`seat1_user_id`),
  INDEX `idx_mahjong_seat2` (`seat2_user_id`),
  INDEX `idx_mahjong_seat3` (`seat3_user_id`),
  INDEX `idx_mahjong_invited` (`invited_user_id`),
  INDEX `idx_mahjong_status_deadline` (`game_status`, `action_deadline`),
  CONSTRAINT `fk_mahjong_owner` FOREIGN KEY (`owner_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mahjong_seat0` FOREIGN KEY (`seat0_user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_mahjong_seat1` FOREIGN KEY (`seat1_user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_mahjong_seat2` FOREIGN KEY (`seat2_user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_mahjong_seat3` FOREIGN KEY (`seat3_user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_mahjong_invited` FOREIGN KEY (`invited_user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
);

-- 动作日志：摸牌、补花、出牌、吃碰杠胡等，tile 为牌编码（见 MahjongTiles）
CREATE TABLE IF NOT EXISTS `mahjong_moves` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `game_id` BIGINT NOT NULL,
  `round_no` INT NOT NULL,
  `move_number` INT NOT NULL COMMENT '房间内递增序号',
  `seat_index` TINYINT NOT NULL,
  `player_id` BIGINT NULL COMMENT '真人玩家；电脑为 NULL',
  `action_type` VARCHAR(16) NOT NULL COMMENT 'DRAW,FLOWER,DISCARD,QIAO,CHI,PONG,MING_GANG,JIA_GANG,AN_GANG,HU,DRAW_GAME,AUTO_ON,AUTO_OFF',
  `tile` TINYINT NULL COMMENT '牌编码',
  `move_data` JSON NULL COMMENT '附加信息，如 hidden=true 表示只有本人可见',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_mahjong_move_number` (`game_id`, `move_number`),
  INDEX `idx_mahjong_moves_game_round` (`game_id`, `round_no`),
  INDEX `idx_mahjong_moves_player` (`player_id`),
  CONSTRAINT `fk_mahjong_moves_game` FOREIGN KEY (`game_id`) REFERENCES `mahjong_games`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mahjong_moves_player` FOREIGN KEY (`player_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
);
