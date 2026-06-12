<template>
  <div class="admin-page admin-page-narrow system-page">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <div class="admin-page-kicker">SYSTEM</div>
        <h1 class="admin-page-title">系统配置</h1>
        <p class="admin-page-subtitle">
          管理发件邮箱与语音大模型运行配置，敏感字段仅显示脱敏状态。
        </p>
      </div>
      <div class="admin-page-actions">
        <a-button :loading="loading" @click="fetchConfig">
          <template #icon><icon-refresh /></template>
          刷新配置
        </a-button>
      </div>
    </div>

    <a-alert
      v-if="error"
      class="system-alert"
      type="error"
      :message="error"
      show-icon
    />

    <a-spin class="system-spin" :loading="loading">
      <template v-if="emailSettings && voiceSettings && !error">
        <section class="admin-panel admin-panel-padded system-summary" :class="summaryClass">
          <div class="summary-icon">
            <component :is="summaryIcon" />
          </div>
          <div class="summary-body">
            <div class="summary-title">{{ summaryTitle }}</div>
            <div class="summary-copy">{{ summaryCopy }}</div>
            <div class="summary-tags">
              <a-tag :color="emailSettings.passwordConfigured ? 'green' : 'orange'">
                邮箱 {{ emailSettings.passwordConfigured ? '已配置' : '待配置' }}
              </a-tag>
              <a-tag :color="voiceSettings.enabled ? 'green' : 'gray'">
                语音 {{ voiceSettings.enabled ? '已启用' : '已停用' }}
              </a-tag>
              <a-tag :color="voiceSettings.asrApiKeyConfigured && voiceSettings.chatApiKeyConfigured ? 'green' : 'orange'">
                模型 Key {{ voiceSettings.asrApiKeyConfigured && voiceSettings.chatApiKeyConfigured ? '已配置' : '待配置' }}
              </a-tag>
            </div>
          </div>
        </section>

        <a-tabs v-model:active-key="activeTab" class="settings-tabs">
          <a-tab-pane key="email" title="发件邮箱">
            <section class="admin-panel admin-panel-padded settings-panel">
              <div class="settings-panel-header">
                <div>
                  <div class="settings-title">
                    <icon-thunderbolt />
                    <span>邮件发送</span>
                  </div>
                  <div class="settings-subtitle">SMTP 发件账号</div>
                </div>
                <a-tag :color="emailSettings.passwordConfigured ? 'green' : 'orange'">
                  {{ emailSettings.passwordMasked || '未配置密码' }}
                </a-tag>
              </div>

              <a-form :model="emailForm" layout="vertical">
                <div class="settings-form-grid">
                  <a-form-item field="host" label="SMTP 主机">
                    <a-input v-model="emailForm.host" placeholder="smtp.example.com" allow-clear />
                  </a-form-item>
                  <a-form-item field="port" label="端口">
                    <a-input-number
                      v-model="emailForm.port"
                      :min="1"
                      :max="65535"
                      :precision="0"
                      hide-button
                    />
                  </a-form-item>
                  <a-form-item field="username" label="登录邮箱">
                    <a-input v-model="emailForm.username" placeholder="sender@example.com" allow-clear />
                  </a-form-item>
                  <a-form-item field="fromEmail" label="发件邮箱">
                    <a-input v-model="emailForm.fromEmail" placeholder="notice@example.com" allow-clear />
                  </a-form-item>
                  <a-form-item field="password" label="授权码/密码">
                    <a-input-password
                      v-model="emailForm.password"
                      :placeholder="emailPasswordPlaceholder"
                      allow-clear
                    />
                  </a-form-item>
                  <a-form-item field="sslEnabled" label="SSL">
                    <a-switch v-model="emailForm.sslEnabled" />
                  </a-form-item>
                </div>
              </a-form>

              <div class="settings-actions">
                <a-button type="primary" :loading="savingEmail" @click="saveEmailSettings">
                  <template #icon><icon-check-circle /></template>
                  保存发件邮箱
                </a-button>
              </div>
            </section>
          </a-tab-pane>

          <a-tab-pane key="voice" title="语音大模型">
            <section class="admin-panel admin-panel-padded settings-panel">
              <div class="settings-panel-header">
                <div>
                  <div class="settings-title">
                    <icon-robot />
                    <span>模型服务</span>
                  </div>
                  <div class="settings-subtitle">OpenAI 兼容接口</div>
                </div>
                <div class="settings-tag-group">
                  <a-tag :color="voiceSettings.asrApiKeyConfigured ? 'green' : 'orange'">
                    ASR {{ voiceSettings.asrApiKeyMasked || '未配置' }}
                  </a-tag>
                  <a-tag :color="voiceSettings.chatApiKeyConfigured ? 'green' : 'orange'">
                    Chat {{ voiceSettings.chatApiKeyMasked || '未配置' }}
                  </a-tag>
                </div>
              </div>

              <a-form :model="voiceForm" layout="vertical">
                <div class="settings-form-grid">
                  <a-form-item field="enabled" label="启用">
                    <a-switch v-model="voiceForm.enabled" />
                  </a-form-item>
                  <a-form-item field="provider" label="服务商">
                    <a-select v-model="voiceForm.provider" @change="handleProviderChange">
                      <a-option value="mimo">MiMo</a-option>
                      <a-option value="deepseek">DeepSeek</a-option>
                      <a-option value="openai_compatible">OpenAI 兼容</a-option>
                    </a-select>
                  </a-form-item>
                  <a-form-item field="asrBaseUrl" label="ASR Base URL">
                    <a-input v-model="voiceForm.asrBaseUrl" placeholder="https://api.example.com/v1" allow-clear />
                  </a-form-item>
                  <a-form-item field="asrAuthScheme" label="ASR 鉴权">
                    <a-select v-model="voiceForm.asrAuthScheme" @change="syncAuthHeader('asr')">
                      <a-option value="api_key">api-key Header</a-option>
                      <a-option value="bearer">Bearer Token</a-option>
                    </a-select>
                  </a-form-item>
                  <a-form-item field="asrApiKeyHeader" label="ASR Key Header">
                    <a-input
                      v-model="voiceForm.asrApiKeyHeader"
                      placeholder="api-key"
                      :disabled="voiceForm.asrAuthScheme === 'bearer'"
                      allow-clear
                    />
                  </a-form-item>
                  <a-form-item field="asrApiKey" label="ASR API Key">
                    <a-input-password
                      v-model="voiceForm.asrApiKey"
                      :placeholder="asrApiKeyPlaceholder"
                      allow-clear
                    />
                  </a-form-item>
                  <a-form-item field="asrModel" label="ASR 模型">
                    <a-input v-model="voiceForm.asrModel" placeholder="mimo-v2.5-asr" allow-clear />
                  </a-form-item>
                  <a-form-item field="chatBaseUrl" label="Chat Base URL">
                    <a-input v-model="voiceForm.chatBaseUrl" placeholder="https://api.deepseek.com/v1" allow-clear />
                  </a-form-item>
                  <a-form-item field="chatAuthScheme" label="Chat 鉴权">
                    <a-select v-model="voiceForm.chatAuthScheme" @change="syncAuthHeader('chat')">
                      <a-option value="api_key">api-key Header</a-option>
                      <a-option value="bearer">Bearer Token</a-option>
                    </a-select>
                  </a-form-item>
                  <a-form-item field="chatApiKeyHeader" label="Chat Key Header">
                    <a-input
                      v-model="voiceForm.chatApiKeyHeader"
                      placeholder="Authorization"
                      :disabled="voiceForm.chatAuthScheme === 'bearer'"
                      allow-clear
                    />
                  </a-form-item>
                  <a-form-item field="chatApiKey" label="Chat API Key">
                    <a-input-password
                      v-model="voiceForm.chatApiKey"
                      :placeholder="chatApiKeyPlaceholder"
                      allow-clear
                    />
                  </a-form-item>
                  <a-form-item field="chatModel" label="Chat 模型">
                    <a-input v-model="voiceForm.chatModel" placeholder="deepseek-chat" allow-clear />
                  </a-form-item>
                </div>
              </a-form>

              <div class="security-note">
                <icon-safe />
                <span>保存时留空密钥会保留当前值，后台不会把原始密钥返回给页面。</span>
              </div>

              <div class="settings-actions">
                <a-button type="primary" :loading="savingVoice" @click="saveVoiceSettings">
                  <template #icon><icon-check-circle /></template>
                  保存语音大模型
                </a-button>
              </div>
            </section>
          </a-tab-pane>
        </a-tabs>
      </template>

      <div v-else-if="!loading && !error" class="admin-empty-state">
        暂无系统配置数据
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import {
  adminAPI,
  type EmailSettingsResponse,
  type EmailSettingsUpdateRequest,
  type VoiceModelSettingsResponse,
  type VoiceModelSettingsUpdateRequest
} from '@/services/api'
import {
  IconCheckCircle,
  IconCloseCircle,
  IconExclamationCircle,
  IconRefresh,
  IconRobot,
  IconSafe,
  IconThunderbolt
} from '@arco-design/web-vue/es/icon'

const activeTab = ref('email')
const loading = ref(false)
const savingEmail = ref(false)
const savingVoice = ref(false)
const error = ref('')

const emailSettings = ref<EmailSettingsResponse | null>(null)
const voiceSettings = ref<VoiceModelSettingsResponse | null>(null)

const emailForm = reactive<EmailSettingsUpdateRequest>({
  host: '',
  port: 465,
  username: '',
  fromEmail: '',
  password: '',
  sslEnabled: true
})

const voiceForm = reactive<VoiceModelSettingsUpdateRequest>({
  enabled: true,
  provider: 'mimo',
  asrBaseUrl: '',
  asrAuthScheme: 'api_key',
  asrApiKeyHeader: 'api-key',
  asrApiKey: '',
  asrModel: '',
  chatBaseUrl: '',
  chatAuthScheme: 'api_key',
  chatApiKeyHeader: 'api-key',
  chatApiKey: '',
  chatModel: ''
})

const providerDefaults: Record<string, Partial<VoiceModelSettingsUpdateRequest>> = {
  mimo: {
    asrBaseUrl: 'https://token-plan-cn.xiaomimimo.com/v1',
    asrAuthScheme: 'api_key',
    asrApiKeyHeader: 'api-key',
    asrModel: 'mimo-v2.5-asr',
    chatBaseUrl: 'https://token-plan-cn.xiaomimimo.com/v1',
    chatAuthScheme: 'api_key',
    chatApiKeyHeader: 'api-key',
    chatModel: 'mimo-v2.5'
  },
  deepseek: {
    chatBaseUrl: 'https://api.deepseek.com/v1',
    chatAuthScheme: 'bearer',
    chatApiKeyHeader: 'Authorization',
    chatModel: 'deepseek-chat'
  },
  openai_compatible: {
    chatAuthScheme: 'bearer',
    chatApiKeyHeader: 'Authorization'
  }
}

const emailReady = computed(() => Boolean(emailSettings.value?.passwordConfigured && emailSettings.value?.host))
const voiceReady = computed(() => Boolean(
  voiceSettings.value?.enabled &&
  voiceSettings.value?.asrApiKeyConfigured &&
  voiceSettings.value?.chatApiKeyConfigured &&
  voiceSettings.value?.asrBaseUrl &&
  voiceSettings.value?.chatBaseUrl
))

const summaryTitle = computed(() => {
  if (!emailReady.value && !voiceReady.value) return '系统配置待完善'
  if (!emailReady.value) return '发件邮箱待完善'
  if (!voiceReady.value) return '语音大模型待完善'
  return '系统配置可用'
})

const summaryCopy = computed(() => {
  if (!emailReady.value && !voiceReady.value) return '邮件与语音模型都需要补齐运行配置。'
  if (!emailReady.value) return '语音配置可用，邮件发件账号仍需补齐。'
  if (!voiceReady.value) return '邮件配置可用，语音模型仍需补齐或启用。'
  return '邮件发送与语音模型均已具备运行所需配置。'
})

const summaryClass = computed(() => {
  if (emailReady.value && voiceReady.value) return 'is-configured'
  if (!emailReady.value && !voiceReady.value) return 'is-disabled'
  return 'is-unconfigured'
})

const summaryIcon = computed(() => {
  if (emailReady.value && voiceReady.value) return IconCheckCircle
  if (!emailReady.value && !voiceReady.value) return IconCloseCircle
  return IconExclamationCircle
})

const normalizeEmail = (value?: string): string => (value || '').trim().toLowerCase()

const emailUsernameChanged = computed(() => {
  if (!emailSettings.value) return false
  return normalizeEmail(emailForm.username) !== normalizeEmail(emailSettings.value.username)
})

const emailPasswordPlaceholder = computed(() => {
  if (emailUsernameChanged.value) {
    return '更换邮箱时必须输入新的 SMTP 授权码'
  }
  return emailSettings.value?.passwordConfigured
    ? `保留当前 ${emailSettings.value.passwordMasked || '已配置密码'}`
    : '输入 SMTP 授权码/应用专用密码'
})

const asrApiKeyPlaceholder = computed(() => {
  return voiceSettings.value?.asrApiKeyConfigured
    ? `保留当前 ${voiceSettings.value.asrApiKeyMasked || '已配置 ASR Key'}`
    : '输入 ASR API Key'
})

const chatApiKeyPlaceholder = computed(() => {
  return voiceSettings.value?.chatApiKeyConfigured
    ? `保留当前 ${voiceSettings.value.chatApiKeyMasked || '已配置 Chat Key'}`
    : '输入 Chat API Key'
})

const applyEmailSettings = (settings: EmailSettingsResponse): void => {
  emailSettings.value = settings
  emailForm.host = settings.host || ''
  emailForm.port = settings.port || 465
  emailForm.username = settings.username || ''
  emailForm.fromEmail = settings.fromEmail || settings.username || ''
  emailForm.password = ''
  emailForm.sslEnabled = settings.sslEnabled ?? true
}

const applyVoiceSettings = (settings: VoiceModelSettingsResponse): void => {
  voiceSettings.value = settings
  voiceForm.enabled = settings.enabled ?? true
  voiceForm.provider = settings.provider || 'mimo'
  voiceForm.asrBaseUrl = settings.asrBaseUrl || settings.baseUrl || ''
  voiceForm.asrAuthScheme = settings.asrAuthScheme || settings.authScheme || 'api_key'
  voiceForm.asrApiKeyHeader = settings.asrApiKeyHeader || settings.apiKeyHeader || (voiceForm.asrAuthScheme === 'bearer' ? 'Authorization' : 'api-key')
  voiceForm.asrApiKey = ''
  voiceForm.asrModel = settings.asrModel || ''
  voiceForm.chatBaseUrl = settings.chatBaseUrl || settings.baseUrl || ''
  voiceForm.chatAuthScheme = settings.chatAuthScheme || settings.authScheme || 'api_key'
  voiceForm.chatApiKeyHeader = settings.chatApiKeyHeader || settings.apiKeyHeader || (voiceForm.chatAuthScheme === 'bearer' ? 'Authorization' : 'api-key')
  voiceForm.chatApiKey = ''
  voiceForm.chatModel = settings.chatModel || ''
}

const fetchConfig = async (): Promise<void> => {
  loading.value = true
  error.value = ''

  try {
    const [email, voice] = await Promise.all([
      adminAPI.getEmailSettings(),
      adminAPI.getVoiceSettings()
    ])
    applyEmailSettings(email)
    applyVoiceSettings(voice)
  } catch (err: any) {
    error.value = err.message || '获取系统配置失败'
    Message.error(error.value)
  } finally {
    loading.value = false
  }
}

const saveEmailSettings = async (): Promise<void> => {
  savingEmail.value = true

  try {
    const password = emailForm.password?.trim()
    if (emailUsernameChanged.value && !password) {
      Message.warning('更换登录邮箱时必须填写新的 SMTP 授权码/应用专用密码')
      return
    }
    const payload: EmailSettingsUpdateRequest = {
      ...emailForm,
      password: password || undefined
    }
    const response = await adminAPI.updateEmailSettings(payload)
    applyEmailSettings(response)
    Message.success('发件邮箱已保存')
  } catch (err: any) {
    Message.error(err.message || '保存发件邮箱失败')
  } finally {
    savingEmail.value = false
  }
}

const saveVoiceSettings = async (): Promise<void> => {
  savingVoice.value = true

  try {
    const payload: VoiceModelSettingsUpdateRequest = {
      ...voiceForm,
      asrApiKey: voiceForm.asrApiKey?.trim() || undefined,
      chatApiKey: voiceForm.chatApiKey?.trim() || undefined
    }
    const response = await adminAPI.updateVoiceSettings(payload)
    applyVoiceSettings(response)
    Message.success('语音大模型已保存')
  } catch (err: any) {
    Message.error(err.message || '保存语音大模型失败')
  } finally {
    savingVoice.value = false
  }
}

const handleProviderChange = (value: unknown): void => {
  const provider = String(value)
  const defaults = providerDefaults[provider]
  if (!defaults) return

  Object.assign(voiceForm, {
    ...defaults,
    provider,
    asrApiKey: '',
    chatApiKey: ''
  })
}

const syncAuthHeader = (target: 'asr' | 'chat'): void => {
  if (target === 'asr') {
    voiceForm.asrApiKeyHeader = voiceForm.asrAuthScheme === 'bearer' ? 'Authorization' : 'api-key'
    return
  }

  voiceForm.chatApiKeyHeader = voiceForm.chatAuthScheme === 'bearer' ? 'Authorization' : 'api-key'
}

onMounted(() => {
  fetchConfig()
})
</script>

<style scoped>
.system-page {
  padding-bottom: 8px;
}

.system-alert {
  margin-bottom: 16px;
}

.system-spin {
  display: block;
  min-height: 220px;
}

.system-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  border-left: 4px solid #d7dce5;
}

.system-summary.is-configured {
  border-left-color: #00b42a;
}

.system-summary.is-unconfigured {
  border-left-color: #ff7d00;
}

.system-summary.is-disabled {
  border-left-color: #86909c;
}

.summary-icon {
  display: flex;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #2563eb;
  background: #eef4ff;
  font-size: 24px;
}

.summary-body {
  min-width: 0;
}

.summary-title {
  color: var(--admin-text);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.summary-copy {
  margin-top: 6px;
  color: var(--admin-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.settings-tabs :deep(.arco-tabs-content) {
  padding-top: 8px;
}

.settings-panel {
  margin-bottom: 16px;
}

.settings-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.settings-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--admin-text);
  font-size: 16px;
  font-weight: 650;
}

.settings-title svg {
  color: #2563eb;
}

.settings-tag-group {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.settings-subtitle {
  margin-top: 6px;
  color: var(--admin-text-muted);
  font-size: 13px;
}

.settings-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
}

.security-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 4px;
  padding: 10px;
  border-radius: 6px;
  color: #4e5969;
  background: #f7f8fa;
  font-size: 13px;
  line-height: 1.5;
}

.security-note svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #00b42a;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 768px) {
  .system-summary {
    gap: 12px;
    padding: 16px;
  }

  .summary-icon {
    width: 42px;
    height: 42px;
    flex-basis: 42px;
    font-size: 22px;
  }

  .settings-panel-header {
    flex-direction: column;
  }

  .settings-form-grid {
    grid-template-columns: 1fr;
  }

  .settings-actions .arco-btn {
    width: 100%;
  }
}
</style>
