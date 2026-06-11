use approval_system;
CREATE TABLE IF NOT EXISTS system_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Setting ID',
  setting_key VARCHAR(100) NOT NULL COMMENT 'Namespaced setting key',
  setting_value TEXT COMMENT 'Setting value',
  `sensitive` TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether value is sensitive',
  description VARCHAR(255) COMMENT 'Setting description',
  updated_by BIGINT COMMENT 'Last admin user ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  UNIQUE KEY uk_system_settings_key (setting_key),
  INDEX idx_system_settings_updated_at (updated_at)
) COMMENT='Runtime system settings';
