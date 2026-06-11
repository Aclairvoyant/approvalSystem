package com.approval.system.service.impl;

import com.approval.system.config.MiMoConfig;
import com.approval.system.dto.EffectiveEmailSettings;
import com.approval.system.dto.EffectiveVoiceModelSettings;
import com.approval.system.dto.EmailSettingsResponse;
import com.approval.system.dto.EmailSettingsUpdateRequest;
import com.approval.system.dto.VoiceModelSettingsResponse;
import com.approval.system.dto.VoiceModelSettingsUpdateRequest;
import com.approval.system.entity.SystemSetting;
import com.approval.system.mapper.SystemSettingMapper;
import com.approval.system.service.ISystemSettingService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SystemSettingServiceImpl implements ISystemSettingService {

    private static final String EMAIL_HOST = "email.host";
    private static final String EMAIL_PORT = "email.port";
    private static final String EMAIL_USERNAME = "email.username";
    private static final String EMAIL_FROM = "email.from";
    private static final String EMAIL_PASSWORD = "email.password";
    private static final String EMAIL_SSL_ENABLED = "email.sslEnabled";

    private static final String VOICE_ENABLED = "voice.enabled";
    private static final String VOICE_PROVIDER = "voice.provider";
    private static final String VOICE_BASE_URL = "voice.baseUrl";
    private static final String VOICE_AUTH_SCHEME = "voice.authScheme";
    private static final String VOICE_API_KEY_HEADER = "voice.apiKeyHeader";
    private static final String VOICE_API_KEY = "voice.apiKey";
    private static final String VOICE_ASR_BASE_URL = "voice.asr.baseUrl";
    private static final String VOICE_ASR_AUTH_SCHEME = "voice.asr.authScheme";
    private static final String VOICE_ASR_API_KEY_HEADER = "voice.asr.apiKeyHeader";
    private static final String VOICE_ASR_API_KEY = "voice.asr.apiKey";
    private static final String VOICE_ASR_MODEL = "voice.asrModel";
    private static final String VOICE_CHAT_BASE_URL = "voice.chat.baseUrl";
    private static final String VOICE_CHAT_AUTH_SCHEME = "voice.chat.authScheme";
    private static final String VOICE_CHAT_API_KEY_HEADER = "voice.chat.apiKeyHeader";
    private static final String VOICE_CHAT_API_KEY = "voice.chat.apiKey";
    private static final String VOICE_CHAT_MODEL = "voice.chatModel";

    @Autowired
    private SystemSettingMapper systemSettingMapper;

    @Autowired
    private MiMoConfig miMoConfig;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:465}")
    private Integer mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:true}")
    private Boolean mailSslEnabled;

    @Override
    public EmailSettingsResponse getEmailSettings() {
        return toEmailResponse(getEffectiveEmailSettings());
    }

    @Override
    @Transactional
    public EmailSettingsResponse updateEmailSettings(EmailSettingsUpdateRequest request, Long updatedBy) {
        if (request == null) {
            throw new IllegalArgumentException("Email settings cannot be empty");
        }

        EffectiveEmailSettings current = getEffectiveEmailSettings();
        String host = requiredOrCurrent(request.getHost(), current.getHost(), "SMTP host cannot be empty");
        Integer port = request.getPort() != null ? request.getPort() : current.getPort();
        validatePort(port);
        String username = requiredOrCurrent(request.getUsername(), current.getUsername(), "SMTP username cannot be empty");
        String fromEmail = valueOrCurrent(request.getFromEmail(), current.getFromEmail());
        Boolean sslEnabled = request.getSslEnabled() != null ? request.getSslEnabled() : current.getSslEnabled();
        String currentUsername = StringUtils.hasText(current.getUsername()) ? current.getUsername().trim() : "";
        if (!username.equalsIgnoreCase(currentUsername) && !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("Changing SMTP username requires a new SMTP password or authorization code");
        }

        replaceSetting(EMAIL_HOST, host, false, "SMTP host", updatedBy);
        replaceSetting(EMAIL_PORT, String.valueOf(port), false, "SMTP port", updatedBy);
        replaceSetting(EMAIL_USERNAME, username, false, "SMTP username", updatedBy);
        replaceSetting(EMAIL_FROM, StringUtils.hasText(fromEmail) ? fromEmail.trim() : username, false, "Email from address", updatedBy);
        replaceSetting(EMAIL_SSL_ENABLED, String.valueOf(Boolean.TRUE.equals(sslEnabled)), false, "SMTP SSL enabled", updatedBy);
        if (StringUtils.hasText(request.getPassword())) {
            replaceSetting(EMAIL_PASSWORD, request.getPassword().trim(), true, "SMTP password", updatedBy);
        }

        return getEmailSettings();
    }

    @Override
    public EffectiveEmailSettings getEffectiveEmailSettings() {
        Map<String, String> settings = loadSettings();
        String username = valueOrDefault(settings, EMAIL_USERNAME, mailUsername);
        String fromEmail = valueOrDefault(settings, EMAIL_FROM, username);
        return EffectiveEmailSettings.builder()
                .host(valueOrDefault(settings, EMAIL_HOST, mailHost))
                .port(parseInteger(valueOrDefault(settings, EMAIL_PORT, String.valueOf(mailPort)), mailPort))
                .username(username)
                .fromEmail(fromEmail)
                .password(valueOrDefault(settings, EMAIL_PASSWORD, mailPassword))
                .sslEnabled(parseBoolean(valueOrDefault(settings, EMAIL_SSL_ENABLED, String.valueOf(Boolean.TRUE.equals(mailSslEnabled))), true))
                .build();
    }

    @Override
    public VoiceModelSettingsResponse getVoiceModelSettings() {
        return toVoiceResponse(getEffectiveVoiceModelSettings());
    }

    @Override
    @Transactional
    public VoiceModelSettingsResponse updateVoiceModelSettings(VoiceModelSettingsUpdateRequest request, Long updatedBy) {
        if (request == null) {
            throw new IllegalArgumentException("Voice model settings cannot be empty");
        }

        EffectiveVoiceModelSettings current = getEffectiveVoiceModelSettings();
        String currentProvider = normalizeProvider(current.getProvider());
        String provider = normalizeProvider(valueOrCurrent(request.getProvider(), current.getProvider()));
        boolean providerChanged = StringUtils.hasText(request.getProvider()) && !provider.equals(currentProvider);
        String asrAuthScheme = normalizeAuthScheme(firstText(
                request.getAsrAuthScheme(),
                request.getAuthScheme(),
                current.getAsrAuthScheme(),
                "api_key"
        ));
        String asrApiKeyHeader = firstText(
                request.getAsrApiKeyHeader(),
                request.getApiKeyHeader(),
                current.getAsrApiKeyHeader(),
                defaultApiKeyHeader(asrAuthScheme)
        );
        String chatAuthScheme = normalizeAuthScheme(firstText(
                request.getChatAuthScheme(),
                request.getAuthScheme(),
                providerChanged ? defaultAuthScheme(provider) : current.getChatAuthScheme(),
                current.getChatAuthScheme(),
                defaultAuthScheme(provider)
        ));
        String chatApiKeyHeader = firstText(
                request.getChatApiKeyHeader(),
                request.getApiKeyHeader(),
                providerChanged ? defaultApiKeyHeader(chatAuthScheme) : current.getChatApiKeyHeader(),
                current.getChatApiKeyHeader(),
                defaultApiKeyHeader(chatAuthScheme)
        );
        String asrBaseUrl = requiredOrCurrent(
                firstText(request.getAsrBaseUrl(), request.getBaseUrl()),
                current.getAsrBaseUrl(),
                "ASR base URL cannot be empty"
        );
        String chatBaseUrl = requiredOrCurrent(
                firstText(request.getChatBaseUrl(), request.getBaseUrl()),
                providerChanged ? defaultBaseUrl(provider, current.getChatBaseUrl()) : current.getChatBaseUrl(),
                "Chat model base URL cannot be empty"
        );
        String asrModel = requiredOrCurrent(request.getAsrModel(), current.getAsrModel(), "ASR model cannot be empty");
        String chatModel = requiredOrCurrent(
                request.getChatModel(),
                providerChanged ? defaultChatModel(provider, current.getChatModel()) : current.getChatModel(),
                "Chat model cannot be empty"
        );
        Boolean enabled = request.getEnabled() != null ? request.getEnabled() : current.getEnabled();

        replaceSetting(VOICE_ENABLED, String.valueOf(Boolean.TRUE.equals(enabled)), false, "Voice model enabled", updatedBy);
        replaceSetting(VOICE_PROVIDER, provider, false, "Voice model provider", updatedBy);
        replaceSetting(VOICE_ASR_BASE_URL, asrBaseUrl, false, "Voice ASR base URL", updatedBy);
        replaceSetting(VOICE_ASR_AUTH_SCHEME, asrAuthScheme, false, "Voice ASR auth scheme", updatedBy);
        replaceSetting(VOICE_ASR_API_KEY_HEADER, asrApiKeyHeader, false, "Voice ASR API key header", updatedBy);
        replaceSetting(VOICE_ASR_MODEL, asrModel, false, "Voice ASR model", updatedBy);
        replaceSetting(VOICE_CHAT_BASE_URL, chatBaseUrl, false, "Voice chat base URL", updatedBy);
        replaceSetting(VOICE_CHAT_AUTH_SCHEME, chatAuthScheme, false, "Voice chat auth scheme", updatedBy);
        replaceSetting(VOICE_CHAT_API_KEY_HEADER, chatApiKeyHeader, false, "Voice chat API key header", updatedBy);
        replaceSetting(VOICE_CHAT_MODEL, chatModel, false, "Voice chat model", updatedBy);
        replaceSetting(VOICE_BASE_URL, asrBaseUrl, false, "Legacy voice model base URL", updatedBy);
        replaceSetting(VOICE_AUTH_SCHEME, asrAuthScheme, false, "Legacy voice model auth scheme", updatedBy);
        replaceSetting(VOICE_API_KEY_HEADER, asrApiKeyHeader, false, "Legacy voice model API key header", updatedBy);
        if (StringUtils.hasText(request.getApiKey())) {
            String apiKey = request.getApiKey().trim();
            replaceSetting(VOICE_API_KEY, apiKey, true, "Legacy voice model API key", updatedBy);
            replaceSetting(VOICE_ASR_API_KEY, apiKey, true, "Voice ASR API key", updatedBy);
            replaceSetting(VOICE_CHAT_API_KEY, apiKey, true, "Voice chat API key", updatedBy);
        }
        if (StringUtils.hasText(request.getAsrApiKey())) {
            replaceSetting(VOICE_ASR_API_KEY, request.getAsrApiKey().trim(), true, "Voice ASR API key", updatedBy);
        }
        if (StringUtils.hasText(request.getChatApiKey())) {
            replaceSetting(VOICE_CHAT_API_KEY, request.getChatApiKey().trim(), true, "Voice chat API key", updatedBy);
        } else if (providerChanged && !StringUtils.hasText(request.getApiKey())) {
            replaceSetting(VOICE_CHAT_API_KEY, "", true, "Voice chat API key", updatedBy);
        }

        return getVoiceModelSettings();
    }

    @Override
    public EffectiveVoiceModelSettings getEffectiveVoiceModelSettings() {
        Map<String, String> settings = loadSettings();
        String provider = normalizeProvider(valueOrDefault(settings, VOICE_PROVIDER, "mimo"));
        String legacyBaseUrl = valueOrDefault(settings, VOICE_BASE_URL, miMoConfig.getBaseUrl());
        String legacyAuthScheme = normalizeAuthScheme(valueOrDefault(settings, VOICE_AUTH_SCHEME, "api_key"));
        String legacyApiKeyHeader = valueOrDefault(settings, VOICE_API_KEY_HEADER, defaultApiKeyHeader(legacyAuthScheme));
        String legacyApiKey = valueOrDefault(settings, VOICE_API_KEY, miMoConfig.getApiKey());

        String asrAuthScheme = normalizeAuthScheme(valueOrDefault(settings, VOICE_ASR_AUTH_SCHEME, legacyAuthScheme));
        String chatAuthScheme = normalizeAuthScheme(valueOrDefault(settings, VOICE_CHAT_AUTH_SCHEME, defaultAuthScheme(provider)));
        String asrBaseUrl = valueOrDefault(settings, VOICE_ASR_BASE_URL, legacyBaseUrl);
        String chatBaseUrl = valueOrDefault(settings, VOICE_CHAT_BASE_URL, defaultBaseUrl(provider, legacyBaseUrl));
        String asrApiKey = valueOrDefault(settings, VOICE_ASR_API_KEY, legacyApiKey);
        String chatApiKey = valueOrDefault(settings, VOICE_CHAT_API_KEY, defaultChatApiKeyFallback(provider, legacyApiKey));
        String asrApiKeyHeader = valueOrDefault(settings, VOICE_ASR_API_KEY_HEADER, defaultApiKeyHeader(asrAuthScheme));
        String chatApiKeyHeader = valueOrDefault(settings, VOICE_CHAT_API_KEY_HEADER, defaultApiKeyHeader(chatAuthScheme));

        return EffectiveVoiceModelSettings.builder()
                .enabled(parseBoolean(valueOrDefault(settings, VOICE_ENABLED, String.valueOf(Boolean.TRUE.equals(miMoConfig.getEnabled()))), true))
                .provider(provider)
                .asrBaseUrl(asrBaseUrl)
                .asrAuthScheme(asrAuthScheme)
                .asrApiKeyHeader(asrApiKeyHeader)
                .asrApiKey(asrApiKey)
                .asrModel(valueOrDefault(settings, VOICE_ASR_MODEL, miMoConfig.getAsrModel()))
                .chatBaseUrl(chatBaseUrl)
                .chatAuthScheme(chatAuthScheme)
                .chatApiKeyHeader(chatApiKeyHeader)
                .chatApiKey(chatApiKey)
                .chatModel(valueOrDefault(settings, VOICE_CHAT_MODEL, defaultChatModel(provider, miMoConfig.getChatModel())))
                .baseUrl(asrBaseUrl)
                .authScheme(asrAuthScheme)
                .apiKeyHeader(asrApiKeyHeader)
                .apiKey(asrApiKey)
                .build();
    }

    private Map<String, String> loadSettings() {
        List<SystemSetting> settings = systemSettingMapper.selectList(new QueryWrapper<>());
        Map<String, String> map = new HashMap<>();
        if (settings == null) {
            return map;
        }
        for (SystemSetting setting : settings) {
            if (setting != null && StringUtils.hasText(setting.getSettingKey())) {
                map.put(setting.getSettingKey(), setting.getSettingValue());
            }
        }
        return map;
    }

    private void replaceSetting(String key, String value, boolean sensitive, String description, Long updatedBy) {
        LocalDateTime now = LocalDateTime.now();
        SystemSetting setting = SystemSetting.builder()
                .settingKey(key)
                .settingValue(value)
                .sensitive(sensitive)
                .description(description)
                .updatedBy(updatedBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
        systemSettingMapper.upsert(setting);
    }

    private EmailSettingsResponse toEmailResponse(EffectiveEmailSettings settings) {
        boolean configured = isConfiguredSecret(settings.getPassword());
        return EmailSettingsResponse.builder()
                .host(settings.getHost())
                .port(settings.getPort())
                .username(settings.getUsername())
                .fromEmail(settings.getFromEmail())
                .sslEnabled(settings.getSslEnabled())
                .passwordConfigured(configured)
                .passwordMasked(configured ? maskSecret(settings.getPassword()) : "")
                .build();
    }

    private VoiceModelSettingsResponse toVoiceResponse(EffectiveVoiceModelSettings settings) {
        boolean asrConfigured = isConfiguredSecret(settings.getAsrApiKey());
        boolean chatConfigured = isConfiguredSecret(settings.getChatApiKey());
        return VoiceModelSettingsResponse.builder()
                .enabled(settings.getEnabled())
                .provider(settings.getProvider())
                .asrBaseUrl(settings.getAsrBaseUrl())
                .asrAuthScheme(settings.getAsrAuthScheme())
                .asrApiKeyHeader(settings.getAsrApiKeyHeader())
                .asrModel(settings.getAsrModel())
                .asrApiKeyConfigured(asrConfigured)
                .asrApiKeyMasked(asrConfigured ? maskSecret(settings.getAsrApiKey()) : "")
                .chatBaseUrl(settings.getChatBaseUrl())
                .chatAuthScheme(settings.getChatAuthScheme())
                .chatApiKeyHeader(settings.getChatApiKeyHeader())
                .chatModel(settings.getChatModel())
                .chatApiKeyConfigured(chatConfigured)
                .chatApiKeyMasked(chatConfigured ? maskSecret(settings.getChatApiKey()) : "")
                .baseUrl(settings.getBaseUrl())
                .authScheme(settings.getAuthScheme())
                .apiKeyHeader(settings.getApiKeyHeader())
                .apiKeyConfigured(asrConfigured)
                .apiKeyMasked(asrConfigured ? maskSecret(settings.getAsrApiKey()) : "")
                .build();
    }

    private String valueOrDefault(Map<String, String> settings, String key, String fallback) {
        String value = settings.get(key);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String requiredOrCurrent(String value, String current, String message) {
        String resolved = valueOrCurrent(value, current);
        if (!StringUtils.hasText(resolved)) {
            throw new IllegalArgumentException(message);
        }
        return resolved.trim();
    }

    private Integer parseInteger(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Boolean parseBoolean(String value, Boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private void validatePort(Integer port) {
        if (port == null || port <= 0 || port > 65535) {
            throw new IllegalArgumentException("SMTP port must be between 1 and 65535");
        }
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "mimo";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAuthScheme(String authScheme) {
        if (!StringUtils.hasText(authScheme)) {
            return "api_key";
        }
        String normalized = authScheme.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return "bearer".equals(normalized) ? "bearer" : "api_key";
    }

    private String defaultAuthScheme(String provider) {
        String normalized = normalizeProvider(provider);
        if ("deepseek".equals(normalized) || "openai".equals(normalized) || "openai_compatible".equals(normalized)) {
            return "bearer";
        }
        return "api_key";
    }

    private String defaultBaseUrl(String provider, String fallback) {
        String normalized = normalizeProvider(provider);
        if ("deepseek".equals(normalized)) {
            return "https://api.deepseek.com/v1";
        }
        if ("mimo".equals(normalized) && miMoConfig != null && StringUtils.hasText(miMoConfig.getBaseUrl())) {
            return miMoConfig.getBaseUrl().trim();
        }
        return fallback;
    }

    private String defaultChatModel(String provider, String fallback) {
        String normalized = normalizeProvider(provider);
        if ("deepseek".equals(normalized)) {
            return "deepseek-chat";
        }
        if ("mimo".equals(normalized) && miMoConfig != null && StringUtils.hasText(miMoConfig.getChatModel())) {
            return miMoConfig.getChatModel().trim();
        }
        return fallback;
    }

    private String defaultChatApiKeyFallback(String provider, String legacyApiKey) {
        return "mimo".equals(normalizeProvider(provider)) ? legacyApiKey : "";
    }

    private String defaultApiKeyHeader(String authScheme) {
        return "bearer".equals(normalizeAuthScheme(authScheme)) ? "Authorization" : "api-key";
    }

    private boolean isConfiguredSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("your-")
                && !normalized.startsWith("replace-with-")
                && !normalized.contains("your_")
                && !normalized.contains("请替换");
    }

    private String maskSecret(String value) {
        if (!isConfiguredSecret(value)) {
            return "";
        }
        String trimmed = value.trim();
        int length = trimmed.length();
        if (length <= 3) {
            return "***";
        }
        if (length <= 8) {
            return trimmed.substring(0, 1) + "***" + trimmed.substring(length - 1);
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(length - 3);
    }
}
