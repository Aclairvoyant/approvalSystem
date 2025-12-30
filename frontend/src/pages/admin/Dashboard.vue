<template>
  <div class="dashboard">
    <h1>仪表盘</h1>

    <!-- 统计卡片 -->
    <div class="stats">
      <div class="stat-card pending">
        <div class="stat-icon">
          <icon-clock-circle />
        </div>
        <div class="stat-info">
          <div class="stat-label">待审批申请</div>
          <div class="stat-value">{{ stats.pendingApplications }}</div>
        </div>
      </div>
      <div class="stat-card approved">
        <div class="stat-icon">
          <icon-check-circle />
        </div>
        <div class="stat-info">
          <div class="stat-label">已批准申请</div>
          <div class="stat-value">{{ stats.approvedApplications }}</div>
        </div>
      </div>
      <div class="stat-card rejected">
        <div class="stat-icon">
          <icon-close-circle />
        </div>
        <div class="stat-info">
          <div class="stat-label">已驳回申请</div>
          <div class="stat-value">{{ stats.rejectedApplications }}</div>
        </div>
      </div>
      <div class="stat-card total">
        <div class="stat-icon">
          <icon-file />
        </div>
        <div class="stat-info">
          <div class="stat-label">总申请数</div>
          <div class="stat-value">{{ stats.totalApplications }}</div>
        </div>
      </div>
    </div>

    <!-- 用户统计 -->
    <div class="user-stats">
      <div class="stat-card users">
        <div class="stat-icon">
          <icon-user-group />
        </div>
        <div class="stat-info">
          <div class="stat-label">总用户数</div>
          <div class="stat-value">{{ stats.totalUsers }}</div>
        </div>
      </div>
      <div class="stat-card active">
        <div class="stat-icon">
          <icon-user />
        </div>
        <div class="stat-info">
          <div class="stat-label">活跃用户</div>
          <div class="stat-value">{{ stats.activeUsers }}</div>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="quick-actions">
      <h2>快捷操作</h2>
      <div class="action-buttons">
        <a-button type="primary" @click="$router.push('/admin/applications')">
          <template #icon><icon-file /></template>
          申请管理
        </a-button>
        <a-button type="primary" @click="$router.push('/admin/users')">
          <template #icon><icon-user /></template>
          用户管理
        </a-button>
        <a-button @click="refreshStats">
          <template #icon><icon-refresh /></template>
          刷新数据
        </a-button>
      </div>
    </div>
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
  IconRefresh
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

const fetchStats = async (): Promise<void> => {
  loading.value = true
  try {
    const data = await adminAPI.getDashboardStats()
    stats.value = data
  } catch (error: any) {
    Message.error(error.message || '获取统计数据失败')
  } finally {
    loading.value = false
  }
}

const refreshStats = (): void => {
  fetchStats()
  Message.success('数据已刷新')
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
}

h1 {
  margin: 0 0 24px 0;
  font-size: 28px;
  font-weight: 600;
  color: #1d2129;
}

h2 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 500;
  color: #1d2129;
}

.stats, .user-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 24px;
}

.stat-card.pending .stat-icon {
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  color: #ff7a45;
}

.stat-card.approved .stat-icon {
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  color: #52c41a;
}

.stat-card.rejected .stat-icon {
  background: linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%);
  color: #ff4d4f;
}

.stat-card.total .stat-icon {
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  color: #1890ff;
}

.stat-card.users .stat-icon {
  background: linear-gradient(135deg, #f9f0ff 0%, #efdbff 100%);
  color: #722ed1;
}

.stat-card.active .stat-icon {
  background: linear-gradient(135deg, #e6fffb 0%, #b5f5ec 100%);
  color: #13c2c2;
}

.stat-info {
  flex: 1;
}

.stat-label {
  color: #86909c;
  font-size: 14px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #1d2129;
}

.quick-actions {
  background: white;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.action-buttons :deep(.arco-btn) {
  height: 40px;
  border-radius: 8px;
}
</style>
