package com.approval.system.mapper;

import com.approval.system.entity.SystemSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {

    @Insert("""
            INSERT INTO system_settings
              (setting_key, setting_value, `sensitive`, description, updated_by, created_at, updated_at)
            VALUES
              (#{settingKey}, #{settingValue}, #{sensitive}, #{description}, #{updatedBy}, #{createdAt}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
              setting_value = VALUES(setting_value),
              `sensitive` = VALUES(`sensitive`),
              description = VALUES(description),
              updated_by = VALUES(updated_by),
              updated_at = VALUES(updated_at)
            """)
    int upsert(SystemSetting setting);
}
