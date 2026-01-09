<template>
  <div class="game-center">
    <!-- 顶部区域 -->
    <div class="header-section">
      <div class="title">游戏中心</div>
      <div class="subtitle">和TA一起玩，感情升温</div>
    </div>

    <!-- 游戏选择卡片 -->
    <div class="game-cards">
      <!-- 飞行棋 -->
      <div class="game-card flight-chess" @click="goToFlightChess">
        <div class="card-bg"></div>
        <div class="card-content">
          <div class="game-icon">
            <span class="icon-emoji">&#x2708;</span>
          </div>
          <div class="game-info">
            <h3 class="game-title">飞行棋</h3>
            <p class="game-desc">经典飞行棋，完成任务获得奖励</p>
          </div>
          <div class="game-arrow">
            <van-icon name="arrow" />
          </div>
        </div>
        <div class="game-badge" v-if="activeFlightChessGames > 0">
          <van-badge :content="activeFlightChessGames" />
        </div>
      </div>

      <!-- 麻将 -->
      <div class="game-card mahjong" @click="goToMahjong">
        <div class="card-bg"></div>
        <div class="card-content">
          <div class="game-icon">
            <span class="icon-emoji">&#x1F004;</span>
          </div>
          <div class="game-info">
            <h3 class="game-title">上海麻将</h3>
            <p class="game-desc">敲麻/百搭规则，最多4人对战</p>
          </div>
          <div class="game-arrow">
            <van-icon name="arrow" />
          </div>
        </div>
        <div class="game-badge" v-if="activeMahjongGames > 0">
          <van-badge :content="activeMahjongGames" />
        </div>
      </div>
    </div>

    <!-- 快速入口 -->
    <div class="quick-actions">
      <div class="section-title">快速入口</div>
      <van-cell-group inset>
        <van-cell
          title="加入房间"
          label="输入房间号快速加入游戏"
          is-link
          center
          @click="showJoinDialog = true"
        >
          <template #icon>
            <van-icon name="scan" size="24" color="#07c160" class="cell-icon" />
          </template>
        </van-cell>
        <van-cell
          title="游戏任务"
          label="查看和管理飞行棋任务"
          is-link
          center
          @click="router.push('/mobile/game/tasks')"
        >
          <template #icon>
            <van-icon name="todo-list-o" size="24" color="#ff976a" class="cell-icon" />
          </template>
        </van-cell>
      </van-cell-group>
    </div>

    <!-- 最近游戏 -->
    <div class="recent-games" v-if="recentGames.length > 0">
      <div class="section-title">最近游戏</div>
      <div class="recent-list">
        <div
          v-for="game in recentGames"
          :key="game.id"
          class="recent-item"
          @click="goToRecentGame(game)"
        >
          <div class="recent-icon">
            {{ game.type === 'mahjong' ? '&#x1F004;' : '&#x2708;' }}
          </div>
          <div class="recent-info">
            <div class="recent-name">
              {{ game.type === 'mahjong' ? '上海麻将' : '飞行棋' }}
            </div>
            <div class="recent-status">
              <van-tag :type="game.status === 'playing' ? 'primary' : 'success'" size="small">
                {{ game.status === 'playing' ? '进行中' : '已结束' }}
              </van-tag>
              <span class="recent-time">{{ game.time }}</span>
            </div>
          </div>
          <van-icon name="arrow" class="recent-arrow" />
        </div>
      </div>
    </div>

    <!-- 加入房间弹窗 -->
    <van-dialog
      v-model:show="showJoinDialog"
      title="加入游戏房间"
      show-cancel-button
      :before-close="handleJoinGame"
    >
      <div class="dialog-content">
        <van-field
          v-model="joinGameCode"
          label="房间号"
          placeholder="请输入房间号"
          maxlength="8"
        />
        <van-radio-group v-model="joinGameType" class="game-type-radio">
          <van-radio name="flight">飞行棋</van-radio>
          <van-radio name="mahjong">麻将</van-radio>
        </van-radio-group>
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showLoadingToast, closeToast } from 'vant'
import { useGameStore } from '@/store/modules/game'
import { useMahjongStore } from '@/store/modules/mahjong'

const router = useRouter()
const gameStore = useGameStore()
const mahjongStore = useMahjongStore()

// 活跃游戏数量
const activeFlightChessGames = ref(0)
const activeMahjongGames = ref(0)

// 最近游戏
const recentGames = ref<Array<{
  id: number
  type: 'flight' | 'mahjong'
  status: 'playing' | 'finished'
  time: string
}>>([])

// 加入房间
const showJoinDialog = ref(false)
const joinGameCode = ref('')
const joinGameType = ref<'flight' | 'mahjong'>('flight')

// 跳转到飞行棋
function goToFlightChess() {
  router.push('/mobile/game/flight-chess')
}

// 跳转到麻将
function goToMahjong() {
  router.push('/mobile/mahjong')
}

// 跳转到最近游戏
function goToRecentGame(game: { id: number; type: string }) {
  if (game.type === 'mahjong') {
    router.push(`/mobile/mahjong/room/${game.id}`)
  } else {
    router.push(`/mobile/game/room/${game.id}`)
  }
}

// 加入游戏
async function handleJoinGame(action: string): Promise<boolean> {
  if (action === 'confirm') {
    if (!joinGameCode.value) {
      showToast({ type: 'fail', message: '请输入房间号' })
      return false
    }

    const toast = showLoadingToast({ message: '加入中...', forbidClick: true })
    try {
      if (joinGameType.value === 'mahjong') {
        const game = await mahjongStore.joinGame(joinGameCode.value)
        closeToast()
        showToast({ type: 'success', message: '加入成功' })
        joinGameCode.value = ''
        router.push(`/mobile/mahjong/room/${game.id}`)
      } else {
        const game = await gameStore.joinGame(joinGameCode.value)
        closeToast()
        showToast({ type: 'success', message: '加入成功' })
        joinGameCode.value = ''
        router.push(`/mobile/game/room/${game.id}`)
      }
      return true
    } catch (error: any) {
      closeToast()
      showToast({ type: 'fail', message: error.response?.data?.message || error.message || '加入失败' })
      return false
    }
  }
  return true
}

// 加载活跃游戏数量
async function loadActiveGames() {
  try {
    // 加载飞行棋活跃游戏
    const flightGames = await gameStore.fetchGameHistory(2) // 进行中
    activeFlightChessGames.value = gameStore.gameHistory.length

    // 加载麻将活跃游戏
    const mahjongGame = await mahjongStore.getActiveGame()
    activeMahjongGames.value = mahjongGame ? 1 : 0

    // 构建最近游戏列表
    const recent: typeof recentGames.value = []

    // 添加飞行棋游戏
    gameStore.gameHistory.slice(0, 2).forEach((g: any) => {
      recent.push({
        id: g.id,
        type: 'flight',
        status: g.gameStatus === 2 ? 'playing' : 'finished',
        time: formatTime(g.createdAt)
      })
    })

    // 添加麻将游戏
    if (mahjongGame) {
      recent.push({
        id: mahjongGame.id,
        type: 'mahjong',
        status: mahjongGame.gameStatus === 2 ? 'playing' : 'finished',
        time: formatTime(mahjongGame.createdAt)
      })
    }

    // 按时间排序
    recentGames.value = recent.slice(0, 5)
  } catch (error) {
    console.error('加载游戏数据失败', error)
  }
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

onMounted(() => {
  loadActiveGames()
})
</script>

<style scoped lang="scss">
.game-center {
  min-height: 100vh;
  background-color: #f7f8fa;
  padding-bottom: 80px;
}

.header-section {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px 60px;
  text-align: center;
  color: white;

  .title {
    font-size: 28px;
    font-weight: bold;
    margin-bottom: 8px;
  }

  .subtitle {
    font-size: 14px;
    opacity: 0.9;
  }
}

.game-cards {
  padding: 0 16px;
  margin-top: -30px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.game-card {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;

  &:active {
    transform: scale(0.98);
  }

  .card-bg {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
  }

  .card-content {
    position: relative;
    display: flex;
    align-items: center;
    padding: 20px;
    gap: 16px;
  }

  .game-icon {
    width: 60px;
    height: 60px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 32px;
    background: rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(4px);
  }

  .icon-emoji {
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
  }

  .game-info {
    flex: 1;
  }

  .game-title {
    font-size: 20px;
    font-weight: bold;
    color: white;
    margin: 0 0 4px 0;
  }

  .game-desc {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.8);
    margin: 0;
  }

  .game-arrow {
    color: rgba(255, 255, 255, 0.8);
    font-size: 20px;
  }

  .game-badge {
    position: absolute;
    top: 12px;
    right: 12px;
  }

  &.flight-chess {
    .card-bg {
      background: linear-gradient(135deg, #667eea 0%, #5a67d8 100%);
    }
  }

  &.mahjong {
    .card-bg {
      background: linear-gradient(135deg, #1a472a 0%, #2d5a3f 100%);
    }
  }
}

.quick-actions {
  padding: 24px 16px 0;

  .section-title {
    font-size: 16px;
    font-weight: 500;
    margin-bottom: 12px;
    padding-left: 4px;
  }

  .cell-icon {
    margin-right: 12px;
  }
}

.recent-games {
  padding: 24px 16px 0;

  .section-title {
    font-size: 16px;
    font-weight: 500;
    margin-bottom: 12px;
    padding-left: 4px;
  }

  .recent-list {
    background: white;
    border-radius: 12px;
    overflow: hidden;
  }

  .recent-item {
    display: flex;
    align-items: center;
    padding: 14px 16px;
    border-bottom: 1px solid #f5f5f5;
    cursor: pointer;

    &:last-child {
      border-bottom: none;
    }

    &:active {
      background: #f7f8fa;
    }
  }

  .recent-icon {
    width: 40px;
    height: 40px;
    border-radius: 8px;
    background: #f5f5f5;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    margin-right: 12px;
  }

  .recent-info {
    flex: 1;
  }

  .recent-name {
    font-size: 15px;
    font-weight: 500;
    color: #323233;
    margin-bottom: 4px;
  }

  .recent-status {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .recent-time {
    font-size: 12px;
    color: #969799;
  }

  .recent-arrow {
    color: #969799;
  }
}

.dialog-content {
  padding: 16px;

  .game-type-radio {
    display: flex;
    justify-content: center;
    gap: 24px;
    margin-top: 16px;
  }
}
</style>
