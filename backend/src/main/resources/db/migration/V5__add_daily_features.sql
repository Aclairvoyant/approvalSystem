USE approval_system;
CREATE TABLE IF NOT EXISTS `daily_items` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `creator_id` BIGINT NOT NULL,
  `partner_id` BIGINT NULL,
  `item_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1=todo,2=shopping,3=date_plan,4=promise',
  `title` VARCHAR(255) NOT NULL,
  `content` TEXT NULL,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=pending,2=completed,3=archived',
  `priority` TINYINT NOT NULL DEFAULT 1 COMMENT '1=normal,2=important',
  `target_date` DATE NULL,
  `completed_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_daily_creator` (`creator_id`),
  INDEX `idx_daily_partner` (`partner_id`),
  INDEX `idx_daily_status_date` (`status`, `target_date`),
  CONSTRAINT `fk_daily_creator` FOREIGN KEY (`creator_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_daily_partner` FOREIGN KEY (`partner_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `couple_events` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `creator_id` BIGINT NOT NULL,
  `partner_id` BIGINT NULL,
  `event_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1=anniversary,2=birthday,3=date,4=custom',
  `title` VARCHAR(255) NOT NULL,
  `event_date` DATE NOT NULL,
  `repeat_type` TINYINT NOT NULL DEFAULT 1 COMMENT '0=none,1=yearly',
  `remind_days_before` INT NOT NULL DEFAULT 1,
  `note` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_events_creator` (`creator_id`),
  INDEX `idx_events_partner` (`partner_id`),
  INDEX `idx_events_date` (`event_date`),
  CONSTRAINT `fk_events_creator` FOREIGN KEY (`creator_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_events_partner` FOREIGN KEY (`partner_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `application_templates` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `creator_id` BIGINT NOT NULL,
  `partner_id` BIGINT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  `remark` VARCHAR(500) NULL,
  `shared` TINYINT(1) NOT NULL DEFAULT 0,
  `usage_count` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_templates_creator` (`creator_id`),
  INDEX `idx_templates_partner_shared` (`partner_id`, `shared`),
  CONSTRAINT `fk_templates_creator` FOREIGN KEY (`creator_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_templates_partner` FOREIGN KEY (`partner_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
);
