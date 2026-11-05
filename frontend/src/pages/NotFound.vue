<template>
  <main class="not-found">
    <p class="code">404</p>
    <h1>页面不存在</h1>
    <p class="hint">地址可能输错了，或者这个页面已经被移除。</p>
    <router-link class="home-link" :to="homePath">返回首页</router-link>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const homePath = computed(() => {
  if (!userStore.token) return '/login'
  return userStore.isAdmin ? '/admin/dashboard' : '/mobile/home'
})
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  padding: 24px;
  background: var(--bg);
  text-align: center;
}

.code {
  color: var(--ink-3);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

h1 {
  margin-top: 4px;
  color: var(--ink);
  font-size: 22px;
}

.hint {
  margin-top: 8px;
  color: var(--ink-2);
  font-size: 14px;
}

.home-link {
  margin-top: 24px;
  padding: 10px 20px;
  border-radius: var(--radius);
  background: var(--accent);
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}
</style>
