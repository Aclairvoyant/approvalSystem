<template>
  <div class="admin-page dashboard">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <h1 class="admin-page-title">仪表盘</h1>
        <p class="admin-page-subtitle">申请流转和用户规模的总览</p>
      </div>
      <div class="admin-page-actions">
        <a-button :loading="loading" @click="refreshStats">
          <template #icon><icon-refresh /></template>
          刷新
        </a-button>
      </div>
    </div>

    <a-spin class="dashboard-spin" :loading="loading">
      <router-link
        v-if="stats.pendingApplications > 0"
        class="admin-panel pending-banner"
        :to="{ path: '/admin/applications', query: { status: '1' } }"
      >
        <span><b class="tabular">{{ stats.pendingApplications }}</b> 条申请等待审批</span>
        <span class="pending-link">去处理 <icon-right /></span>
      </router-link>

      <h2 class="admin-section-title">申请</h2>
      <section class="admin-metric-grid">
        <div class="admin-metric-card">
          <div class="admin-metric-label">待审批</div>
          <div class="admin-metric-value" :class="{ 'is-pending': stats.pendingApplications > 0 }">
            {{ stats.pendingApplications }}
          </div>
        </div>
        <div class="admin-metric-card">
          <div class="admin-metric-label">已通过</div>
          <div class="admin-metric-value">{{ stats.approvedApplications }}</div>
          <div v-if="approvalRate !== null" class="admin-metric-note">占已处理的 {{ approvalRate }}%</div>
        </div>
        <div class="admin-metric-card">
          <div class="admin-metric-label">已驳回</div>
          <div class="admin-metric-value">{{ stats.rejectedApplications }}</div>
        </div>
        <div class="admin-metric-card">
          <div class="admin-metric-label">申请总数</div>
          <div class="admin-metric-value">{{ stats.totalApplications }}</div>
        </div>
      </section>

      <h2 class="admin-section-title">用户</h2>
      <section class="admin-metric-grid">
        <div class="admin-metric-card">
          <div class="admin-metric-label">注册用户</div>
          <div class="admin-metric-value">{{ stats.totalUsers }}</div>
        </div>
        <div class="admin-metric-card">
          <div class="admin-metric-label">正常状态</div>
          <div class="admin-metric-value">{{ stats.activeUsers }}</div>
          <div v-if="stats.totalUsers > stats.activeUsers" class="admin-metric-note">
            {{ stats.totalUsers - stats.activeUsers }} 个已禁用
          </div>
        </div>
      </section>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconRefresh, IconRight } from '@arco-design/web-vue/es/icon'
import { adminAPI, type DashboardStats } from '@/services/api'

const stats = ref<DashboardStats>({
  pendingApplications: 0,
  approvedApplications: 0,
  rejectedApplications: 0,
  totalApplications: 0,
  totalUsers: 0,
  activeUsers: 0
})

const loading = ref(false)

const approvalRate = computed(() => {
  const decided = stats.value.approvedApplications + stats.value.rejectedApplications
  return decided > 0 ? Math.round((stats.value.approvedApplications / decided) * 100) : null
})

const fetchStats = async (): Promise<boolean> => {
  loading.value = true
  try {
    stats.value = await adminAPI.getDashboardStats()
    return true
  } catch (error: any) {
    Message.error(error.message || '获取统计数据失败')
    return false
  } finally {
    loading.value = false
  }
}

const refreshStats = async (): Promise<void> => {
  if (await fetchStats()) {
    Message.success('已刷新')
  }
}

onMounted(fetchStats)
</script>

<style scoped>
.dashboard-spin {
  display: block;
}

.pending-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 14px 20px;
  color: var(--admin-text);
  font-size: 14px;
  text-decoration: none;
}

.pending-banner b {
  font-size: 16px;
  font-weight: 600;
}

.pending-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--accent);
}

.pending-banner:hover {
  border-color: var(--admin-border-strong);
}

.admin-metric-value.is-pending {
  color: var(--accent);
}
</style>
