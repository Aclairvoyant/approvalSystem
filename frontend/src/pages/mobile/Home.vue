<template>
  <div class="m-page home-page">
    <header class="top-bar">
      <div>
        <p class="hello">{{ greeting }}</p>
        <h1>{{ userStore.displayName }}</h1>
      </div>
      <router-link class="partner-link" to="/mobile/relations">我的对象</router-link>
    </header>

    <section class="summary m-card">
      <router-link to="/mobile/approvals" class="summary-item">
        <span class="summary-value tabular">{{ summary.pendingApprovalCount }}</span>
        <span class="summary-label">待我审批</span>
      </router-link>
      <router-link to="/mobile/daily" class="summary-item">
        <span class="summary-value tabular">{{ summary.todayDailyCount }}</span>
        <span class="summary-label">今日事项</span>
      </router-link>
      <router-link to="/mobile/daily" class="summary-item">
        <span class="summary-value tabular" :class="{ 'is-overdue': summary.overdueDailyCount > 0 }">
          {{ summary.overdueDailyCount }}
        </span>
        <span class="summary-label">已逾期</span>
      </router-link>
      <router-link to="/mobile/events" class="summary-item">
        <span class="summary-value tabular">{{ eventCount }}</span>
        <span class="summary-label">纪念日</span>
      </router-link>
    </section>

    <nav class="shortcuts">
      <router-link to="/mobile/applications?create=1" class="shortcut">
        <van-icon name="add-o" />
        <span>发起申请</span>
      </router-link>
      <router-link to="/mobile/daily" class="shortcut">
        <van-icon name="notes-o" />
        <span>记一件事</span>
      </router-link>
      <router-link to="/mobile/templates" class="shortcut">
        <van-icon name="orders-o" />
        <span>申请模板</span>
      </router-link>
      <router-link to="/mobile/events" class="shortcut">
        <van-icon name="calendar-o" />
        <span>纪念日</span>
      </router-link>
    </nav>

    <section v-if="summary.pendingApprovals.length" class="m-section">
      <div class="m-section-head">
        <h2 class="m-section-title">等你审批</h2>
        <router-link class="m-section-link" to="/mobile/approvals">全部</router-link>
      </div>
      <van-cell-group class="m-card list-card" :border="false">
        <van-cell
          v-for="app in summary.pendingApprovals.slice(0, 3)"
          :key="app.id"
          :title="app.title"
          :label="`${app.applicantName || '对方'} · ${relativeTime(app.createdAt)}`"
          is-link
          :to="`/mobile/application-detail/${app.id}`"
        />
      </van-cell-group>
    </section>

    <section class="m-section">
      <div class="m-section-head">
        <h2 class="m-section-title">今天要做</h2>
        <router-link class="m-section-link" to="/mobile/daily">全部</router-link>
      </div>
      <van-cell-group class="m-card list-card" :border="false">
        <van-cell
          v-for="item in summary.overdueDailyItems"
          :key="`overdue-${item.id}`"
          :title="item.title"
          :label="`已逾期 · 原定 ${item.targetDate || ''}`"
          is-link
          to="/mobile/daily"
          class="overdue-cell"
        />
        <van-cell
          v-for="item in summary.todayDailyItems"
          :key="item.id"
          :title="item.title"
          :label="item.content || dailyTypeText(item.itemType)"
          is-link
          to="/mobile/daily"
        />
        <p
          v-if="!loading && summary.todayDailyItems.length === 0 && summary.overdueDailyItems.length === 0"
          class="list-empty"
        >
          今天没有待办
        </p>
      </van-cell-group>
    </section>

    <section class="m-section">
      <div class="m-section-head">
        <h2 class="m-section-title">近期纪念日</h2>
        <router-link class="m-section-link" to="/mobile/events">管理</router-link>
      </div>
      <van-cell-group class="m-card list-card" :border="false">
        <van-cell
          v-for="event in summary.upcomingEvents"
          :key="event.id"
          :title="event.title"
          :label="event.eventDate"
          is-link
          to="/mobile/events"
        >
          <template #value>
            <span class="days tabular">{{ daysText(event.eventDate, event.repeatType) }}</span>
          </template>
        </van-cell>
        <p v-if="!loading && summary.upcomingEvents.length === 0" class="list-empty">
          {{ eventCount > 0 ? '30 天内没有纪念日' : '还没有纪念日' }}
        </p>
      </van-cell-group>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showFailToast } from 'vant'
import { reminderAPI, type ReminderSummary } from '@/services/api'
import { useUserStore } from '@/store/modules/user'
import { daysUntil, relativeTime } from '@/utils/format'
import { dailyTypeText } from '@/utils/status'

const userStore = useUserStore()
const loading = ref(false)
const summary = reactive<ReminderSummary>({
  pendingApprovalCount: 0,
  todayDailyCount: 0,
  overdueDailyCount: 0,
  upcomingEventCount: 0,
  pendingApprovals: [],
  todayDailyItems: [],
  overdueDailyItems: [],
  upcomingEvents: [],
})

const eventCount = computed(() => summary.totalEventCount ?? summary.upcomingEventCount)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

onMounted(() => {
  loadSummary()
})

async function loadSummary() {
  loading.value = true
  try {
    Object.assign(summary, await reminderAPI.today())
  } catch (error: any) {
    showFailToast(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function daysText(date: string, repeatType: number) {
  const diff = daysUntil(date, repeatType)
  if (diff === 0) return '今天'
  if (diff === 1) return '明天'
  return diff > 0 ? `${diff} 天后` : `${-diff} 天前`
}
</script>

<style scoped>
.home-page {
  padding-top: max(20px, env(safe-area-inset-top));
}

.top-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 4px 16px;
}

.hello {
  color: var(--ink-2);
  font-size: 13px;
}

h1 {
  margin-top: 2px;
  color: var(--ink);
  font-size: 24px;
  line-height: 1.25;
}

.partner-link {
  color: var(--accent);
  font-size: 14px;
  text-decoration: none;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: 16px 0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-decoration: none;
}

.summary-item + .summary-item {
  border-left: 1px solid var(--rule);
}

.summary-value {
  color: var(--ink);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
}

.summary-value.is-overdue {
  color: var(--danger);
}

.summary-label {
  color: var(--ink-3);
  font-size: 12px;
}

.shortcuts {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 12px;
}

.shortcut {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
  color: var(--ink-2);
  font-size: 12px;
  text-decoration: none;
}

.shortcut .van-icon {
  color: var(--ink);
  font-size: 24px;
}

.list-card {
  overflow: hidden;
}

.overdue-cell :deep(.van-cell__label) {
  color: var(--danger);
}

.days {
  color: var(--ink);
  font-weight: 500;
}

.list-empty {
  padding: 20px 16px;
  color: var(--ink-3);
  font-size: 13px;
  text-align: center;
}
</style>
