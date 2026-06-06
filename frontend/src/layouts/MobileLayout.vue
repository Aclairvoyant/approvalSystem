<template>
  <div class="mobile-layout">
    <div class="mobile-content">
      <router-view />
    </div>

    <nav class="mobile-nav">
      <router-link to="/mobile/home" class="nav-item" :class="{ active: isActive('home') }">
        <van-icon name="wap-home-o" class="nav-icon" />
        <div class="nav-label">首页</div>
      </router-link>
      <router-link to="/mobile/daily" class="nav-item" :class="{ active: isActive('daily') }">
        <van-icon name="notes-o" class="nav-icon" />
        <div class="nav-label">日常</div>
      </router-link>
      <router-link to="/mobile/applications" class="nav-item" :class="{ active: isActive('applications') || isActive('approvals') }">
        <van-icon name="records-o" class="nav-icon" />
        <div class="nav-label">申请</div>
      </router-link>
      <router-link to="/mobile/game" class="nav-item" :class="{ active: isActive('game') }">
        <van-icon name="flag-o" class="nav-icon" />
        <div class="nav-label">游戏</div>
      </router-link>
      <router-link to="/mobile/profile" class="nav-item" :class="{ active: isActive('profile') }">
        <van-icon name="user-o" class="nav-icon" />
        <div class="nav-label">我的</div>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import {
  GobangGameStatus,
  gobangApi,
  type GobangGame
} from '@/services/gobangApi'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const inviteChecked = ref(false)

const isActive = (name: string): boolean => route.path.includes(name)

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

async function checkPendingGobangInvite(): Promise<void> {
  if (inviteChecked.value || !userStore.userId) return
  if (route.path.includes('/mobile/gobang/room')) return

  inviteChecked.value = true
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
  overflow: hidden;
  background: #f6f7f9;
}

.mobile-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
}

.mobile-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  height: 60px;
  background: #fff;
  border-top: 1px solid #eceff3;
  box-shadow: 0 -2px 10px rgba(31, 35, 41, 0.06);
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  text-decoration: none;
  color: #8a8f99;
}

.nav-item.active {
  color: #4f46e5;
}

.nav-icon {
  font-size: 22px;
  margin-bottom: 3px;
}

.nav-label {
  font-size: 11px;
  line-height: 1;
}
</style>
