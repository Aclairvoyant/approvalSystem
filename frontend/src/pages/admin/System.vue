<template>
  <div class="admin-page admin-page-narrow system-page">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <div class="admin-page-kicker">SYSTEM</div>
        <h1 class="admin-page-title">系统配置</h1>
        <p class="admin-page-subtitle">
          查看 MiMo 语音与对话配置状态。页面只显示后端返回的安全字段和脱敏密钥。
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
      <template v-if="config && !error">
        <section class="admin-panel admin-panel-padded system-summary" :class="summaryClass">
          <div class="summary-icon">
            <component :is="summaryIcon" />
          </div>
          <div class="summary-body">
            <div class="summary-title">{{ summaryTitle }}</div>
            <div class="summary-copy">{{ summaryCopy }}</div>
            <div class="summary-tags">
              <a-tag :color="config.enabled ? 'green' : 'gray'">
                {{ config.enabled ? 'MiMo 已启用' : 'MiMo 已停用' }}
              </a-tag>
              <a-tag :color="config.apiKeyConfigured ? 'green' : 'orange'">
                {{ config.apiKeyConfigured ? 'API Key 已配置' : 'API Key 未配置' }}
              </a-tag>
            </div>
          </div>
        </section>

        <a-alert
          v-if="!config.enabled"
          class="system-alert"
          type="warning"
          message="MiMo 功能当前处于停用状态，语音解析或对话相关能力不会使用该配置。"
          show-icon
        />

        <a-alert
          v-if="config.enabled && !config.apiKeyConfigured"
          class="system-alert"
          type="warning"
          message="MiMo 已启用但 API Key 未配置，请在后端配置中补齐密钥后再使用相关能力。"
          show-icon
        />

        <section class="system-grid">
          <article class="admin-panel admin-panel-padded system-card">
            <div class="system-card-header">
              <icon-thunderbolt />
              <span>服务状态</span>
            </div>
            <div class="system-field">
              <span class="system-field-label">enabled</span>
              <a-tag :color="config.enabled ? 'green' : 'gray'">
                {{ config.enabled ? 'true' : 'false' }}
              </a-tag>
            </div>
            <div class="system-field">
              <span class="system-field-label">配置状态</span>
              <span>{{ config.apiKeyConfigured ? 'configured' : 'unconfigured' }}</span>
            </div>
          </article>

          <article class="admin-panel admin-panel-padded system-card">
            <div class="system-card-header">
              <icon-robot />
              <span>模型配置</span>
            </div>
            <div class="system-field">
              <span class="system-field-label">ASR 模型</span>
              <span class="admin-text-break">{{ displayValue(config.asrModel) }}</span>
            </div>
            <div class="system-field">
              <span class="system-field-label">Chat 模型</span>
              <span class="admin-text-break">{{ displayValue(config.chatModel) }}</span>
            </div>
          </article>

          <article class="admin-panel admin-panel-padded system-card">
            <div class="system-card-header">
              <icon-link />
              <span>服务端点</span>
            </div>
            <div class="system-field vertical">
              <span class="system-field-label">baseUrl</span>
              <span class="admin-text-break">{{ displayValue(config.baseUrl) }}</span>
            </div>
          </article>

          <article class="admin-panel admin-panel-padded system-card">
            <div class="system-card-header">
              <icon-lock />
              <span>密钥状态</span>
            </div>
            <div class="system-field">
              <span class="system-field-label">apiKeyConfigured</span>
              <a-tag :color="config.apiKeyConfigured ? 'green' : 'orange'">
                {{ config.apiKeyConfigured ? 'true' : 'false' }}
              </a-tag>
            </div>
            <div class="system-field vertical">
              <span class="system-field-label">apiKeyMasked</span>
              <code class="masked-key">{{ maskedKey }}</code>
            </div>
            <div class="security-note">
              <icon-safe />
              <span>后端只返回脱敏密钥，前端不展示原始 API Key。</span>
            </div>
          </article>
        </section>
      </template>

      <div v-else-if="!loading && !error" class="admin-empty-state">
        暂无 MiMo 配置数据
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { adminAPI, type MimoConfigResponse } from '@/services/api'
import {
  IconCheckCircle,
  IconCloseCircle,
  IconExclamationCircle,
  IconLink,
  IconLock,
  IconRefresh,
  IconRobot,
  IconSafe,
  IconThunderbolt
} from '@arco-design/web-vue/es/icon'

const config = ref<MimoConfigResponse | null>(null)
const loading = ref(false)
const error = ref('')

const isConfigured = computed(() => Boolean(config.value?.apiKeyConfigured))
const isEnabled = computed(() => Boolean(config.value?.enabled))

const summaryTitle = computed(() => {
  if (!isEnabled.value) return 'MiMo 当前停用'
  if (!isConfigured.value) return 'MiMo 等待密钥配置'
  return 'MiMo 配置可用'
})

const summaryCopy = computed(() => {
  if (!isEnabled.value) return '配置已读取，但开关处于关闭状态。'
  if (!isConfigured.value) return '服务开关已开启，但 API Key 未达到可用配置状态。'
  return '服务开关与 API Key 均已配置，页面仅展示安全脱敏信息。'
})

const summaryClass = computed(() => {
  if (!isEnabled.value) return 'is-disabled'
  if (!isConfigured.value) return 'is-unconfigured'
  return 'is-configured'
})

const summaryIcon = computed(() => {
  if (!isEnabled.value) return IconCloseCircle
  if (!isConfigured.value) return IconExclamationCircle
  return IconCheckCircle
})

const maskedKey = computed(() => {
  if (!config.value?.apiKeyConfigured) return '未配置'
  return config.value.apiKeyMasked || '已配置'
})

const displayValue = (value?: string): string => {
  return value && value.trim() ? value : '-'
}

const fetchConfig = async (): Promise<void> => {
  loading.value = true
  error.value = ''

  try {
    config.value = await adminAPI.getMimoConfig()
  } catch (err: any) {
    error.value = err.message || '获取 MiMo 配置失败'
    Message.error(error.value)
  } finally {
    loading.value = false
  }
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

.system-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.system-card {
  min-width: 0;
}

.system-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: var(--admin-text);
  font-size: 16px;
  font-weight: 650;
}

.system-card-header svg {
  color: #2563eb;
}

.system-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 11px 0;
  border-top: 1px solid var(--admin-border);
  color: var(--admin-text);
  font-size: 14px;
}

.system-field.vertical {
  display: block;
}

.system-field-label {
  flex: 0 0 auto;
  color: var(--admin-text-muted);
  font-size: 13px;
}

.system-field.vertical .system-field-label {
  display: block;
  margin-bottom: 8px;
}

.masked-key {
  display: inline-block;
  max-width: 100%;
  padding: 6px 8px;
  overflow-wrap: anywhere;
  border-radius: 6px;
  color: #334155;
  background: #f1f5f9;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.security-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 14px;
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

  .system-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .system-field {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
