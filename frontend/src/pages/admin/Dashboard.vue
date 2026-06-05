<template>
  <div class="admin-page admin-page-narrow dashboard">
    <div class="admin-page-header">
      <div class="admin-page-heading">
        <div class="admin-page-kicker">OVERVIEW</div>
        <h1 class="admin-page-title">仪表盘</h1>
        <p class="admin-page-subtitle">集中查看申请流转、用户规模与常用后台入口。</p>
      </div>
      <div class="admin-page-actions">
        <a-button :loading="loading" @click="refreshStats">
          <template #icon><icon-refresh /></template>
          刷新数据
        </a-button>
      </div>
    </div>

    <a-spin class="dashboard-spin" :loading="loading">
      <section class="admin-metric-grid">
        <div class="admin-metric-card metric-pending">
          <div class="admin-metric-icon">
            <icon-clock-circle />
          </div>
          <div class="admin-metric-content">
            <div class="admin-metric-label">待审批申请</div>
            <div class="admin-metric-value">{{ stats.pendingApplications }}</div>
          </div>
        </div>
        <div class="admin-metric-card metric-approved">
          <div class="admin-metric-icon">
            <icon-check-circle />
          </div>
          <div class="admin-metric-content">
            <div class="admin-metric-label">已批准申请</div>
            <div class="admin-metric-value">{{ stats.approvedApplications }}</div>
          </div>
        </div>
        <div class="admin-metric-card metric-rejected">
          <div class="admin-metric-icon">
            <icon-close-circle />
          </div>
          <div class="admin-metric-content">
            <div class="admin-metric-label">已驳回申请</div>
            <div class="admin-metric-value">{{ stats.rejectedApplications }}</div>
          </div>
        </div>
        <div class="admin-metric-card metric-total">
          <div class="admin-metric-icon">
            <icon-file />
          </div>
          <div class="admin-metric-content">
            <div class="admin-metric-label">总申请数</div>
            <div class="admin-metric-value">{{ stats.totalApplications }}</div>
          </div>
        </div>
        <div class="admin-metric-card metric-users">
          <div class="admin-metric-icon">
            <icon-user-group />
          </div>
          <div class="admin-metric-content">
            <div class="admin-metric-label">总用户数</div>
            <div class="admin-metric-value">{{ stats.totalUsers }}</div>
          </div>
        </div>
        <div class="admin-metric-card metric-active">
          <div class="admin-metric-icon">
            <icon-user />
          </div>
          <div class="admin-metric-content">
            <div class="admin-metric-label">活跃用户</div>
            <div class="admin-metric-value">{{ stats.activeUsers }}</div>
          </div>
        </div>
      </section>

      <section class="admin-panel admin-panel-padded quick-actions">
        <h2 class="admin-section-title">快捷操作</h2>
        <div class="action-buttons">
          <a-button type="primary" @click="$router.push('/admin/applications')">
            <template #icon><icon-file /></template>
            申请管理
          </a-button>
          <a-button type="primary" @click="$router.push('/admin/users')">
            <template #icon><icon-user /></template>
            用户管理
          </a-button>
          <a-button @click="$router.push('/admin/system')">
            <template #icon><icon-settings /></template>
            系统配置
          </a-button>
        </div>
      </section>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { adminAPI, type DashboardStats } from '@/services/api'
import {
  IconClockCircle,
  IconCheckCircle,
  IconCloseCircle,
  IconFile,
  IconUserGroup,
  IconUser,
  IconRefresh,
  IconSettings
} from '@arco-design/web-vue/es/icon'

const stats = ref<DashboardStats>({
  pendingApplications: 0,
  approvedApplications: 0,
  rejectedApplications: 0,
  totalApplications: 0,
  totalUsers: 0,
  activeUsers: 0
})

const loading = ref(false)

const fetchStats = async (): Promise<boolean> => {
  loading.value = true
  try {
    const data = await adminAPI.getDashboardStats()
    stats.value = data
    return true
  } catch (error: any) {
    Message.error(error.message || '获取统计数据失败')
    return false
  } finally {
    loading.value = false
  }
}

const refreshStats = async (): Promise<void> => {
  const success = await fetchStats()
  if (success) {
    Message.success('数据已刷新')
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard-spin {
  display: block;
}

.admin-metric-card .admin-metric-icon {
  color: #2563eb;
  background: #eef4ff;
}

.metric-pending .admin-metric-icon {
  color: #ff7d00;
  background: #fff7e8;
}

.metric-approved .admin-metric-icon {
  color: #00b42a;
  background: #e8ffef;
}

.metric-rejected .admin-metric-icon {
  color: #f53f3f;
  background: #fff0f0;
}

.metric-total .admin-metric-icon {
  color: #2563eb;
  background: #eef4ff;
}

.metric-users .admin-metric-icon {
  color: #7c3aed;
  background: #f3ecff;
}

.metric-active .admin-metric-icon {
  color: #08979c;
  background: #e6fffb;
}

.quick-actions {
  margin-top: 4px;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.action-buttons :deep(.arco-btn) {
  min-width: 112px;
}

@media (max-width: 768px) {
  .action-buttons :deep(.arco-btn) {
    flex: 1 1 130px;
    min-width: 0;
  }
}
</style>
