<template>
  <div class="notifications-page">
    <h1>通知管理</h1>
    <a-table :columns="columns" :data="notifications" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const notifications = ref<any[]>([])

const columns = [
  { title: '通知ID', dataIndex: 'id' },
  { title: '申请ID', dataIndex: 'applicationId' },
  { title: '通知类型', dataIndex: 'notifyType' },
  { title: '接收方', dataIndex: 'notifyUserName' },
  { title: '发送状态', dataIndex: 'sendStatus' },
  { title: '创建时间', dataIndex: 'createdAt' },
  { title: '发送时间', dataIndex: 'sentAt' },
]
</script>

<style scoped>
.notifications-page {
  max-width: 1200px;
}

h1 {
  margin: 0 0 20px 0;
  font-size: 28px;
}
</style>
