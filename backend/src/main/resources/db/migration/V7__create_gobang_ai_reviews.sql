USE approval_system;

CREATE TABLE IF NOT EXISTS `gobang_ai_reviews` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `game_id` BIGINT NOT NULL,
  `source` VARCHAR(32) NOT NULL COMMENT 'mimo or heuristic',
  `review_data` JSON NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_gobang_ai_review_game` (`game_id`),
  INDEX `idx_gobang_ai_review_created_at` (`created_at`),
  CONSTRAINT `fk_gobang_ai_review_game` FOREIGN KEY (`game_id`) REFERENCES `gobang_games`(`id`) ON DELETE CASCADE
);
