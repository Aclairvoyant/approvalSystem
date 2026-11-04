<template>
  <div class="mobile-layout">
    <main class="mobile-content">
      <!-- 以 path 作 key：从房间 A 跳到房间 B 这类同组件换参数的跳转也会重新挂载 -->
      <router-view v-slot="{ Component, route: current }">
        <component :is="Component" :key="current.path" />
      </router-view>
    </main>

    <nav class="mobile-nav">
      <router-link
        v-for="tab in tabs"
        :key="tab.name"
        :to="tab.to"
        class="nav-item"
        :class="{ active: activeTab === tab.name }"
      >
        <van-icon :name="tab.icon" class="nav-icon" />
        <span class="nav-label">{{ tab.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import {
  GobangGameStatus,
  gobangApi,
  type GobangGame
} from '@/services/gobangApi'
import { useUserStore } from '@/store/modules/user'

const tabs = [
  { name: 'home', label: '首页', icon: 'wap-home-o', to: '/mobile/home' },
  { name: 'daily', label: '日常', icon: 'notes-o', to: '/mobile/daily' },
  { name: 'applications', label: '申请', icon: 'records-o', to: '/mobile/applications' },
  { name: 'game', label: '游戏', icon: 'flag-o', to: '/mobile/game' },
  { name: 'profile', label: '我的', icon: 'user-o', to: '/mobile/profile' },
] as const

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeTab = computed(() => route.meta.tab ?? '')

function normalizeGobangGames(response: unknown): GobangGame[] {
  if (Array.isArray(response)) return response as GobangGame[]
  if (typeof response === 'object' && response !== null && 'records' in response) {
    const records = (response as { records?: unknown }).records
    return Array.isArray(records) ? records as GobangGame[] : []
  }
  return []
}

function findPendingInvite(games: GobangGame[]): GobangGame | null {
  return games.find(game =>
    game.gameStatus === GobangGameStatus.WAITING &&
    game.invitedPlayerId === userStore.userId &&
    !game.whitePlayerId
  ) ?? null
}

// 进入移动端时检查一次是否有人邀请我下五子棋
async function checkPendingGobangInvite(): Promise<void> {
  if (!userStore.userId) return
  if (route.name === 'MobileGobangRoom') return

  try {
    const response = await gobangApi.getUserGames(GobangGameStatus.WAITING, 1, 20)
    const invite = findPendingInvite(normalizeGobangGames(response))
    if (!invite) return

    try {
      await showConfirmDialog({
        title: '五子棋邀请',
        message: `${invite.blackPlayerName || '你的对象'} 邀请你加入五子棋房间 ${invite.gameCode}`,
        confirmButtonText: '加入',
        cancelButtonText: '稍后'
      })
    } catch {
      return
    }

    const joinedGame = await gobangApi.joinGame(invite.gameCode)
    showToast({ type: 'success', message: '已加入五子棋房间' })
    router.push(`/mobile/gobang/room/${joinedGame.id}`)
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '五子棋邀请加载失败' })
  }
}

onMounted(() => {
  void checkPendingGobangInvite()
})
</script>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--bg);
}

.mobile-content {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 底部导航是普通的 flex 子元素，自然占位，不会盖住页面内容 */
.mobile-nav {
  display: grid;
  flex-shrink: 0;
  grid-template-columns: repeat(5, 1fr);
  height: calc(54px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  border-top: 1px solid var(--rule);
  background: var(--surface);
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  color: var(--ink-3);
  text-decoration: none;
}

.nav-item.active {
  color: var(--accent);
}

.nav-icon {
  font-size: 22px;
}

.nav-label {
  font-size: 11px;
  line-height: 1;
}

.nav-item.active .nav-label {
  font-weight: 600;
}
</style>
