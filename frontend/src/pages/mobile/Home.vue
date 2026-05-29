<template>
  <div class="home-page">
    <header class="top-bar">
      <div>
        <div class="hello">欢迎回来</div>
        <h1>{{ userStore.realName || userStore.username }}</h1>
      </div>
      <router-link class="partner-link" to="/mobile/relations">对象</router-link>
    </header>

    <section class="summary-grid">
      <router-link to="/mobile/approvals" class="summary-item">
        <span class="summary-value">{{ summary.pendingApprovalCount }}</span>
        <span class="summary-label">待审批</span>
      </router-link>
      <router-link to="/mobile/daily" class="summary-item">
        <span class="summary-value">{{ summary.todayDailyCount }}</span>
        <span class="summary-label">今日事项</span>
      </router-link>
      <router-link to="/mobile/daily" class="summary-item">
        <span class="summary-value">{{ summary.overdueDailyCount }}</span>
        <span class="summary-label">已逾期</span>
      </router-link>
      <router-link to="/mobile/events" class="summary-item">
        <span class="summary-value">{{ eventCount }}</span>
        <span class="summary-label">纪念日</span>
      </router-link>
    </section>

    <section class="quick-actions">
      <router-link to="/mobile/daily" class="action">
        <van-icon name="notes-o" />
        <span>记一件事</span>
      </router-link>
      <router-link to="/mobile/applications" class="action">
        <van-icon name="records-o" />
        <span>发起申请</span>
      </router-link>
      <router-link to="/mobile/templates" class="action">
        <van-icon name="orders-o" />
        <span>申请模板</span>
      </router-link>
      <router-link to="/mobile/events" class="action">
        <van-icon name="underway-o" />
        <span>纪念日</span>
      </router-link>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>今天要做</h2>
        <router-link to="/mobile/daily">全部</router-link>
      </div>
      <van-empty v-if="!loading && summary.todayDailyItems.length === 0" description="今天没有待办" />
      <van-cell
        v-for="item in summary.todayDailyItems"
        :key="item.id"
        :title="item.title"
        :label="item.content || typeText(item.itemType)"
        is-link
        to="/mobile/daily"
      />
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>近期纪念日</h2>
        <router-link to="/mobile/events">管理</router-link>
      </div>
      <van-empty
        v-if="!loading && summary.upcomingEvents.length === 0"
        :description="eventCount > 0 ? '30天内没有纪念日' : '还没有纪念日'"
      />
      <van-cell
        v-for="event in summary.upcomingEvents"
        :key="event.id"
        :title="event.title"
        :label="event.eventDate"
        is-link
        to="/mobile/events"
      >
        <template #value>{{ daysText(event.eventDate, event.repeatType) }}</template>
      </van-cell>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showFailToast } from 'vant'
import { reminderAPI, type ReminderSummary } from '@/services/api'
import { useUserStore } from '@/store/modules/user'

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

function typeText(type: number) {
  return ({ 1: '待办', 2: '清单', 3: '约会', 4: '约定' } as Record<number, string>)[type] || '事项'
}

function daysText(date: string, repeatType: number) {
  const today = new Date()
  const base = new Date(date)
  let next = base
  if (repeatType === 1) {
    next = new Date(today.getFullYear(), base.getMonth(), base.getDate())
    if (next < new Date(today.getFullYear(), today.getMonth(), today.getDate())) {
      next.setFullYear(next.getFullYear() + 1)
    }
  }
  const diff = Math.ceil((next.getTime() - today.getTime()) / 86400000)
  return diff <= 0 ? '今天' : `${diff}天`
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 18px 14px 76px;
  background: #f6f7f9;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.partner-link {
  padding: 6px 13px;
  border: 1px solid #d9dce3;
  border-radius: 999px;
  color: #4f46e5;
  font-size: 13px;
  text-decoration: none;
  background: #fff;
}

.hello {
  color: #7b8190;
  font-size: 13px;
}

h1 {
  margin: 3px 0 0;
  color: #1f2329;
  font-size: 24px;
  line-height: 1.2;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.summary-item,
.action {
  text-decoration: none;
  background: #fff;
  border-radius: 8px;
  color: #1f2329;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 4px;
}

.summary-value {
  color: #4f46e5;
  font-size: 20px;
  font-weight: 700;
}

.summary-label {
  margin-top: 4px;
  color: #7b8190;
  font-size: 11px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 4px;
  color: #3b3f4a;
  font-size: 12px;
}

.action .van-icon {
  color: #4f46e5;
  font-size: 22px;
}

.panel {
  overflow: hidden;
  margin-bottom: 12px;
  background: #fff;
  border-radius: 8px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 4px;
}

.panel-header h2 {
  margin: 0;
  color: #1f2329;
  font-size: 16px;
}

.panel-header a {
  color: #4f46e5;
  font-size: 13px;
  text-decoration: none;
}
</style>
