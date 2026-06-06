USE approval_system;

CREATE TABLE IF NOT EXISTS `gobang_games` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `game_code` VARCHAR(20) NOT NULL,
  `black_player_id` BIGINT NOT NULL,
  `invited_player_id` BIGINT NOT NULL,
  `white_player_id` BIGINT NULL,
  `current_turn` TINYINT NOT NULL DEFAULT 1 COMMENT '1=black,2=white',
  `game_status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=waiting,2=playing,3=finished,4=cancelled',
  `winner_id` BIGINT NULL,
  `board_data` JSON NULL,
  `last_move_id` BIGINT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` DATETIME NULL,
  `ended_at` DATETIME NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_gobang_game_code` (`game_code`),
  INDEX `idx_gobang_black_player` (`black_player_id`),
  INDEX `idx_gobang_invited_player` (`invited_player_id`),
  INDEX `idx_gobang_white_player` (`white_player_id`),
  INDEX `idx_gobang_game_status` (`game_status`),
  INDEX `idx_gobang_created_at` (`created_at`),
  INDEX `idx_gobang_pair_status` (`black_player_id`, `invited_player_id`, `game_status`),
  CONSTRAINT `fk_gobang_black_player` FOREIGN KEY (`black_player_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gobang_invited_player` FOREIGN KEY (`invited_player_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gobang_white_player` FOREIGN KEY (`white_player_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gobang_winner` FOREIGN KEY (`winner_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS `gobang_moves` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `game_id` BIGINT NOT NULL,
  `player_id` BIGINT NOT NULL,
  `move_number` INT NOT NULL,
  `move_type` VARCHAR(32) NOT NULL,
  `row_index` TINYINT NULL,
  `col_index` TINYINT NULL,
  `color` TINYINT NULL COMMENT '1=black,2=white',
  `move_data` JSON NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_gobang_move_number` (`game_id`, `move_number`),
  INDEX `idx_gobang_moves_game_id` (`game_id`),
  INDEX `idx_gobang_moves_player_id` (`player_id`),
  INDEX `idx_gobang_moves_created_at` (`created_at`),
  CONSTRAINT `fk_gobang_moves_game` FOREIGN KEY (`game_id`) REFERENCES `gobang_games`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gobang_moves_player` FOREIGN KEY (`player_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `gobang_undo_requests` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `game_id` BIGINT NOT NULL,
  `requester_id` BIGINT NOT NULL,
  `responder_id` BIGINT NOT NULL,
  `target_move_number` INT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=pending,2=accepted,3=rejected,4=expired',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `responded_at` DATETIME NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_gobang_undo_game_id` (`game_id`),
  INDEX `idx_gobang_undo_requester` (`requester_id`),
  INDEX `idx_gobang_undo_responder` (`responder_id`),
  INDEX `idx_gobang_undo_status` (`status`),
  INDEX `idx_gobang_undo_game_status` (`game_id`, `status`),
  CONSTRAINT `fk_gobang_undo_game` FOREIGN KEY (`game_id`) REFERENCES `gobang_games`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gobang_undo_requester` FOREIGN KEY (`requester_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gobang_undo_responder` FOREIGN KEY (`responder_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
);
