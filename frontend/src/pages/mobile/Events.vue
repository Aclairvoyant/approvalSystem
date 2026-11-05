<template>
  <div class="events-page">
    <van-nav-bar title="纪念日" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <van-icon name="plus" size="20" aria-label="添加纪念日" @click="openCreate()" />
      </template>
    </van-nav-bar>

    <div class="preset-strip">
      <van-button
        v-for="preset in quickPresets"
        :key="preset.title"
        size="small"
        plain
        native-type="button"
        @click="openCreate(preset)"
      >
        {{ preset.title }}
      </van-button>
    </div>

    <van-pull-refresh v-model="refreshing" class="m-page" @refresh="loadEvents">
      <van-empty v-if="!loading && events.length === 0" class="m-empty" description="记下你们重要的日子">
        <van-button type="primary" size="small" @click="openCreate()">添加纪念日</van-button>
      </van-empty>

      <div v-else class="event-list">
        <van-swipe-cell v-for="event in sortedEvents" :key="event.id" class="m-card">
          <div class="event-row">
            <div class="event-main">
              <div class="m-title">{{ event.title }}</div>
              <div class="m-meta">
                <span>{{ eventTypeText(event.eventType) }}</span>
                <span>{{ event.repeatType === 1 ? '每年' : '仅一次' }} · {{ event.eventDate }}</span>
              </div>
              <p class="event-reminder">{{ reminderText(event) }}</p>
              <p v-if="event.note" class="event-note">{{ event.note }}</p>
            </div>
            <div class="countdown" :class="{ 'is-today': eventDays(event) === 0, 'is-past': eventDays(event) < 0 }">
              <span class="countdown-value tabular">{{ countdownValue(event) }}</span>
              <span class="countdown-label">{{ countdownLabel(event) }}</span>
            </div>
          </div>
          <template #right>
            <van-button square type="primary" text="编辑" class="m-swipe-btn" @click="openEdit(event)" />
            <van-button square type="danger" text="删除" class="m-swipe-btn" @click="deleteEvent(event)" />
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <van-action-sheet v-model:show="showEditor" :title="editingId ? '编辑纪念日' : '添加纪念日'">
      <van-form class="m-sheet" @submit="saveEvent">
        <van-cell-group inset>
          <van-field v-model="form.title" label="名称" placeholder="例如：在一起纪念日" maxlength="100" required />
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.eventType" direction="horizontal" class="radio-row">
                <van-radio v-for="type in eventTypes" :key="type.value" :name="type.value">
                  {{ type.label }}
                </van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field v-model="form.eventDate" label="日期" type="date" required />
          <van-field label="每年重复">
            <template #input>
              <van-switch v-model="repeatYearly" size="20" />
            </template>
          </van-field>
          <van-field label="提醒">
            <template #input>
              <van-radio-group v-model="form.remindDaysBefore" direction="horizontal" class="radio-row">
                <van-radio v-for="option in remindOptions" :key="option.value" :name="option.value">
                  {{ option.label }}
                </van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field v-model="form.note" label="备注" type="textarea" rows="2" maxlength="200" placeholder="想一起记住的话" />
        </van-cell-group>
        <div class="m-sheet-footer">
          <van-button block type="primary" native-type="submit" :loading="saving">保存</van-button>
        </div>
      </van-form>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { coupleEventAPI, type CoupleEvent, type CoupleEventRequest } from '@/services/api'
import { daysUntil, todayValue } from '@/utils/format'

type EventPreset = {
  title: string
  eventType: number
  repeatYearly: boolean
  remindDaysBefore: number
  note?: string
}

const eventTypes = [
  { label: '纪念日', value: 1 },
  { label: '生日', value: 2 },
  { label: '约会', value: 3 },
  { label: '其他', value: 4 },
] as const

const remindOptions = [
  { label: '当天', value: 0 },
  { label: '1天', value: 1 },
  { label: '3天', value: 3 },
  { label: '7天', value: 7 },
]

const quickPresets: EventPreset[] = [
  { title: '在一起纪念日', eventType: 1, repeatYearly: true, remindDaysBefore: 7 },
  { title: '生日', eventType: 2, repeatYearly: true, remindDaysBefore: 3 },
  { title: '第一次见面', eventType: 1, repeatYearly: true, remindDaysBefore: 7 },
  { title: '下次约会', eventType: 3, repeatYearly: false, remindDaysBefore: 1 },
]

const events = ref<CoupleEvent[]>([])
const loading = ref(false)
const refreshing = ref(false)
const saving = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const repeatYearly = ref(true)

const form = reactive({
  title: '',
  eventType: 1,
  eventDate: '',
  remindDaysBefore: 1,
  note: '',
})

const sortedEvents = computed(() => {
  return [...events.value].sort((a, b) => eventSortKey(a) - eventSortKey(b))
})

onMounted(() => {
  loadEvents()
})

async function loadEvents() {
  loading.value = true
  try {
    events.value = await coupleEventAPI.list()
  } catch (error: any) {
    showFailToast(error.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function openCreate(preset?: EventPreset) {
  editingId.value = null
  repeatYearly.value = preset?.repeatYearly ?? true
  Object.assign(form, {
    title: preset?.title || '',
    eventType: preset?.eventType || 1,
    eventDate: todayValue(),
    remindDaysBefore: preset?.remindDaysBefore ?? 1,
    note: preset?.note || '',
  })
  showEditor.value = true
}

function openEdit(event: CoupleEvent) {
  editingId.value = event.id
  repeatYearly.value = event.repeatType === 1
  Object.assign(form, {
    title: event.title,
    eventType: event.eventType || 1,
    eventDate: event.eventDate,
    remindDaysBefore: event.remindDaysBefore ?? 1,
    note: event.note || '',
  })
  showEditor.value = true
}

async function saveEvent() {
  if (!form.title.trim() || !form.eventDate) {
    showFailToast('请填写名称和日期')
    return
  }

  saving.value = true
  try {
    const payload: CoupleEventRequest = {
      title: form.title.trim(),
      eventDate: form.eventDate,
      note: form.note.trim(),
      repeatType: repeatYearly.value ? 1 : 0,
      remindDaysBefore: form.remindDaysBefore,
      eventType: form.eventType,
    }

    if (editingId.value) {
      await coupleEventAPI.update(editingId.value, payload)
    } else {
      await coupleEventAPI.create(payload)
    }

    showEditor.value = false
    showSuccessToast('已保存')
    await loadEvents()
  } catch (error: any) {
    showFailToast(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteEvent(event: CoupleEvent) {
  try {
    await showConfirmDialog({ title: '删除纪念日', message: `确定删除"${event.title}"吗？` })
  } catch {
    return
  }
  try {
    await coupleEventAPI.delete(event.id)
    showSuccessToast('已删除')
    await loadEvents()
  } catch (error: any) {
    showFailToast(error.message || '删除失败')
  }
}

function eventDays(event: CoupleEvent) {
  return daysUntil(event.eventDate, event.repeatType)
}

function countdownValue(event: CoupleEvent) {
  const diff = eventDays(event)
  if (diff === 0) return '今天'
  return Math.abs(diff).toString()
}

function countdownLabel(event: CoupleEvent) {
  const diff = eventDays(event)
  if (diff === 0) return ''
  return diff > 0 ? '天后' : '天前'
}

function reminderText(event: CoupleEvent) {
  const remindDays = event.remindDaysBefore ?? 0
  const diff = eventDays(event)
  const remindLabel = remindDays === 0 ? '当天提醒' : `提前 ${remindDays} 天提醒`

  if (diff < 0) return `已过去 ${Math.abs(diff)} 天`
  if (diff === 0) return `就是今天 · ${remindLabel}`
  if (diff <= remindDays) return `快到了 · ${remindLabel}`
  return remindLabel
}

function eventSortKey(event: CoupleEvent) {
  const diff = eventDays(event)
  return diff < 0 ? 100000 + Math.abs(diff) : diff
}

function eventTypeText(type?: number) {
  return eventTypes.find(item => item.value === type)?.label || '其他'
}
</script>

<style scoped>
.events-page {
  min-height: 100%;
  background: var(--bg);
}

.preset-strip {
  display: flex;
  gap: 8px;
  padding: 12px 12px 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.preset-strip :deep(.van-button) {
  flex: 0 0 auto;
}

.event-list .m-card + .m-card {
  margin-top: 8px;
}

.event-list :deep(.van-swipe-cell) {
  overflow: hidden;
}

.event-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}

.event-main {
  flex: 1;
  min-width: 0;
}

.m-meta {
  margin-top: 4px;
}

.event-reminder,
.event-note {
  margin-top: 6px;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.5;
}

.countdown {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  min-width: 52px;
}

.countdown-value {
  color: var(--ink);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
}

.countdown-label {
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 12px;
}

.countdown.is-today .countdown-value {
  color: var(--accent);
  font-size: 20px;
}

.countdown.is-past .countdown-value {
  color: var(--ink-3);
}

.radio-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}
</style>
