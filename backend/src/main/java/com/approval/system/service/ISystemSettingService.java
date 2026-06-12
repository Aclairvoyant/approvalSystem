package com.approval.system.service;

import com.approval.system.dto.EffectiveEmailSettings;
import com.approval.system.dto.EffectiveVoiceModelSettings;
import com.approval.system.dto.EmailSettingsResponse;
import com.approval.system.dto.EmailSettingsUpdateRequest;
import com.approval.system.dto.VoiceModelSettingsResponse;
import com.approval.system.dto.VoiceModelSettingsUpdateRequest;

public interface ISystemSettingService {

    EmailSettingsResponse getEmailSettings();

    EmailSettingsResponse updateEmailSettings(EmailSettingsUpdateRequest request, Long updatedBy);

    EffectiveEmailSettings getEffectiveEmailSettings();

    VoiceModelSettingsResponse getVoiceModelSettings();

    VoiceModelSettingsResponse updateVoiceModelSettings(VoiceModelSettingsUpdateRequest request, Long updatedBy);

    EffectiveVoiceModelSettings getEffectiveVoiceModelSettings();
}
