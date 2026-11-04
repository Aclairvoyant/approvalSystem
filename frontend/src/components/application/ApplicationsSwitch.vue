<template>
  <nav class="apps-switch" aria-label="申请列表切换">
    <router-link to="/mobile/applications" class="apps-switch-item" :class="{ active: current === 'mine' }" replace>
      我发起的
    </router-link>
    <router-link to="/mobile/approvals" class="apps-switch-item" :class="{ active: current === 'approvals' }" replace>
      待我审批
    </router-link>
  </nav>
</template>

<script setup lang="ts">
defineProps<{ current: 'mine' | 'approvals' }>()
</script>

<style scoped>
.apps-switch {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: var(--radius);
  background: var(--surface-2);
}

.apps-switch-item {
  flex: 1;
  padding: 6px 0;
  border-radius: 5px;
  color: var(--ink-2);
  font-size: 14px;
  text-align: center;
  text-decoration: none;
}

.apps-switch-item.active {
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
</style>
