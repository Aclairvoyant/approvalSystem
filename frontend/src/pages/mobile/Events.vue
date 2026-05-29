<template>
  <div class="events-page">
    <van-nav-bar title="纪念日" fixed placeholder>
      <template #right>
        <van-icon name="plus" size="20" @click="openCreate()" />
      </template>
    </van-nav-bar>

    <div class="preset-strip">
      <van-button
        v-for="preset in quickPresets"
        :key="preset.title"
        size="small"
        round
        plain
        type="primary"
        native-type="button"
        @click="openCreate(preset)"
      >
        {{ preset.title }}
      </van-button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="loadEvents">
      <van-empty v-if="!loading && events.length === 0" description="记录重要的日子">
        <van-button type="primary" size="small" round @click="openCreate()">添加纪念日</van-button>
      </van-empty>

      <div v-else class="event-list">
        <van-swipe-cell v-for="event in sortedEvents" :key="event.id">
          <div class="event-card" :class="{ 'event-card--today': daysUntil(event) === 0 }">
            <div class="event-main">
              <div class="event-header">
                <van-tag :type="eventTypeTag(event.eventType)" plain>{{ eventTypeText(event.eventType) }}</van-tag>
                <span class="event-repeat">{{ event.repeatType === 1 ? '每年重复' : '仅一次' }}</span>
              </div>
              <div class="event-title">{{ event.title }}</div>
              <div class="event-meta">
                原日期 {{ event.eventDate }} · 下次 {{ formatDate(nextOccurrence(event)) }}
              </div>
              <div class="event-reminder">{{ reminderText(event) }}</div>
              <div v-if="event.note" class="event-note">{{ event.note }}</div>
            </div>
            <div class="countdown">
              <div class="countdown-value">{{ countdownValue(event) }}</div>
              <div class="countdown-label">{{ countdownLabel(event) }}</div>
            </div>
          </div>
          <template #right>
            <van-button square type="primary" text="编辑" class="swipe-btn" @click="openEdit(event)" />
            <van-button square type="danger" text="删除" class="swipe-btn" @click="deleteEvent(event)" />
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <van-action-sheet v-model:show="showEditor" :title="editingId ? '编辑纪念日' : '添加纪念日'">
      <van-form class="editor" @submit="saveEvent">
        <van-cell-group inset>
          <van-field v-model="form.title" label="名称" placeholder="例如：在一起纪念日" required />
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
          <van-field label="重复">
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
          <van-field v-model="form.note" label="备注" type="textarea" rows="2" placeholder="可以写下想一起记住的话" />
        </van-cell-group>
        <div class="form-actions">
          <van-button round block type="primary" native-type="submit" :loading="saving">保存</van-button>
        </div>
      </van-form>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import { coupleEventAPI, type CoupleEvent, type CoupleEventRequest } from '@/services/api'

type EventPreset = {
  title: string
  eventType: number
  repeatYearly: boolean
  remindDaysBefore: number
  note?: string
}

const eventTypes = [
  { label: '纪念日', value: 1, tagType: 'primary' },
  { label: '生日', value: 2, tagType: 'success' },
  { label: '约会', value: 3, tagType: 'warning' },
  { label: '其他', value: 4, tagType: 'default' },
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
    await coupleEventAPI.delete(event.id)
    showSuccessToast('已删除')
    await loadEvents()
  } catch (error: any) {
    showFailToast(error.message || '删除失败')
  }
}

function daysUntil(event: CoupleEvent) {
  const today = startOfToday()
  const next = nextOccurrence(event)
  return Math.round((next.getTime() - today.getTime()) / 86400000)
}

function countdownValue(event: CoupleEvent) {
  const diff = daysUntil(event)
  if (diff === 0) return '今天'
  return Math.abs(diff).toString()
}

function countdownLabel(event: CoupleEvent) {
  const diff = daysUntil(event)
  if (diff === 0) return '就是今天'
  return diff > 0 ? '天后' : '天前'
}

function reminderText(event: CoupleEvent) {
  const remindDays = event.remindDaysBefore ?? 0
  const diff = daysUntil(event)
  const remindLabel = remindDays === 0 ? '当天提醒' : `提前${remindDays}天提醒`

  if (diff < 0) return `已过去 ${Math.abs(diff)} 天 · ${remindLabel}`
  if (diff === 0) return `今天要记得 · ${remindLabel}`
  if (diff <= remindDays) return `已进入提醒期 · ${remindLabel}`
  return remindLabel
}

function nextOccurrence(event: CoupleEvent) {
  const base = parseLocalDate(event.eventDate)
  if (event.repeatType !== 1) return base

  const today = startOfToday()
  const next = new Date(today.getFullYear(), base.getMonth(), base.getDate())
  if (next < today) {
    next.setFullYear(next.getFullYear() + 1)
  }
  return next
}

function eventSortKey(event: CoupleEvent) {
  const diff = daysUntil(event)
  return diff < 0 ? 100000 + Math.abs(diff) : diff
}

function eventTypeText(type?: number) {
  return eventTypes.find(item => item.value === type)?.label || '其他'
}

function eventTypeTag(type?: number) {
  return eventTypes.find(item => item.value === type)?.tagType || 'default'
}

function formatDate(date: Date) {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${month}-${day}`
}

function todayValue() {
  const today = new Date()
  const month = `${today.getMonth() + 1}`.padStart(2, '0')
  const day = `${today.getDate()}`.padStart(2, '0')
  return `${today.getFullYear()}-${month}-${day}`
}

function parseLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function startOfToday() {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}
</script>

<style scoped>
.events-page {
  min-height: 100vh;
  background: #f6f7f9;
  padding-bottom: 72px;
}

.preset-strip {
  display: flex;
  gap: 8px;
  padding: 12px 12px 0;
  overflow-x: auto;
  background: #f6f7f9;
}

.preset-strip :deep(.van-button) {
  flex: 0 0 auto;
}

.event-list {
  padding: 12px;
}

.event-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 8px;
}

.event-card--today {
  background: #fff8ec;
  box-shadow: inset 3px 0 0 #ff976a;
}

.event-main {
  flex: 1;
  min-width: 0;
}

.event-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.event-repeat,
.event-meta,
.event-reminder,
.event-note {
  color: #777;
  font-size: 13px;
}

.event-title {
  font-weight: 600;
  color: #202124;
}

.event-meta,
.event-reminder,
.event-note {
  margin-top: 6px;
  line-height: 1.4;
}

.event-note {
  color: #555;
}

.countdown {
  width: 64px;
  min-height: 64px;
  border-radius: 8px;
  background: #f2f6ff;
  color: #3867d6;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.countdown-value {
  max-width: 58px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  text-align: center;
  word-break: break-all;
}

.countdown-label {
  margin-top: 4px;
  font-size: 12px;
}

.swipe-btn {
  height: 100%;
}

.editor {
  padding: 16px 0;
}

.radio-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
}

.form-actions {
  padding: 16px;
}
</style>
