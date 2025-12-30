<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()

onMounted(() => {

  // 检查是否已登录
  const token = userStore.token
  if (!token && router.currentRoute.value.path !== '/login' && router.currentRoute.value.path !== '/register') {
    router.push('/login')
  }
})
</script>

<style scoped>
#app {
  width: 100%;
  min-height: 100vh;
}
</style>
