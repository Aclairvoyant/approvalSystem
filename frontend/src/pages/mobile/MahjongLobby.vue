<template>
  <div class="mahjong-lobby">
    <van-nav-bar title="麻将大厅" left-arrow @click-left="router.push('/mobile/game')" />

    <div class="lobby-content">
      <!-- 大厅头部 -->
      <div class="lobby-header">
        <div class="logo-area">
          <span class="logo-text">上海麻将</span>
          <span class="logo-sub">敲麻 / 百搭</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <van-button type="primary" size="large" block @click="showCreateDialog = true">
          创建房间
        </van-button>
        <van-button type="success" size="large" block @click="showJoinDialog = true">
          加入房间
        </van-button>
      </div>

      <!-- 进行中的游戏 -->
      <div v-if="activeGame" class="active-game-card">
        <div class="card-header">
          <span class="card-title">进行中的游戏</span>
          <van-tag type="primary">{{ activeGame.gameCode }}</van-tag>
        </div>
        <div class="card-body">
          <div class="game-info">
            <span>{{ activeGame.ruleTypeName }}</span>
            <span>第{{ activeGame.currentRound }}/{{ activeGame.totalRounds }}局</span>
          </div>
          <van-button type="primary" size="small" @click="continueGame">
            继续游戏
          </van-button>
        </div>
      </div>

      <!-- 历史记录 -->
      <div class="history-section">
        <div class="section-header">
          <span class="section-title">历史记录</span>
        </div>
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="loadHistory"
        >
          <div
            v-for="game in historyGames"
            :key="game.id"
            class="history-item"
            @click="viewGame(game)"
          >
            <div class="history-info">
              <span class="game-code">{{ game.gameCode }}</span>
              <span class="game-type">{{ game.ruleTypeName }}</span>
            </div>
            <div class="history-result">
              <span class="score" :class="{ win: getMyScore(game) > 0 }">
                {{ getMyScore(game) > 0 ? '+' : '' }}{{ getMyScore(game) }}
              </span>
              <span class="date">{{ formatDate(game.endedAt) }}</span>
            </div>
          </div>
        </van-list>
      </div>
    </div>

    <!-- 创建房间弹窗 -->
    <van-popup v-model:show="showCreateDialog" position="bottom" round style="height: 70%">
      <div class="create-dialog">
        <div class="dialog-header">
          <span class="dialog-title">创建房间</span>
          <van-icon name="cross" @click="showCreateDialog = false" />
        </div>
        <van-form @submit="createGame">
          <!-- 规则类型 -->
          <van-field name="ruleType" label="规则类型">
            <template #input>
              <van-radio-group v-model="createForm.ruleType" direction="horizontal">
                <van-radio :name="1">敲麻</van-radio>
                <van-radio :name="2">百搭</van-radio>
              </van-radio-group>
            </template>
          </van-field>

          <!-- 花牌模式（仅百搭） -->
          <van-field v-if="createForm.ruleType === 2" name="flowerMode" label="花牌模式">
            <template #input>
              <van-radio-group v-model="createForm.flowerMode" direction="horizontal">
                <van-radio :name="8">8花</van-radio>
                <van-radio :name="20">20花</van-radio>
                <van-radio :name="36">36花</van-radio>
              </van-radio-group>
            </template>
          </van-field>

          <!-- 玩家人数 -->
          <van-field name="playerCount" label="玩家人数">
            <template #input>
              <van-stepper v-model="createForm.playerCount" :min="2" :max="4" />
            </template>
          </van-field>

          <!-- 局数 -->
          <van-field name="totalRounds" label="总局数">
            <template #input>
              <van-radio-group v-model="createForm.totalRounds" direction="horizontal">
                <van-radio :name="4">4局</van-radio>
                <van-radio :name="8">8局</van-radio>
                <van-radio :name="16">16局</van-radio>
              </van-radio-group>
            </template>
          </van-field>

          <!-- 封顶 -->
          <van-field name="maxScore" label="封顶">
            <template #input>
              <van-radio-group v-model="createForm.maxScore" direction="horizontal">
                <van-radio :name="20">20</van-radio>
                <van-radio :name="50">50</van-radio>
                <van-radio :name="100">100</van-radio>
                <van-radio :name="200">200</van-radio>
                <van-radio :name="0">无</van-radio>
              </van-radio-group>
            </template>
          </van-field>

          <!-- 底分 -->
          <van-field name="baseScore" label="底分">
            <template #input>
              <van-stepper v-model="createForm.baseScore" :min="1" :max="10" />
            </template>
          </van-field>

          <!-- 飞苍蝇 -->
          <van-field name="flyCount" label="飞苍蝇">
            <template #input>
              <van-stepper v-model="createForm.flyCount" :min="0" :max="5" />
            </template>
          </van-field>

          <div class="dialog-footer">
            <van-button type="primary" block native-type="submit" :loading="creating">
              创建房间
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 加入房间弹窗 -->
    <van-popup v-model:show="showJoinDialog" position="center" round>
      <div class="join-dialog">
        <div class="dialog-header">
          <span class="dialog-title">加入房间</span>
        </div>
        <van-form @submit="joinGame">
          <van-field
            v-model="joinCode"
            label="房间号"
            placeholder="请输入6位房间号"
            maxlength="6"
            :rules="[{ required: true, message: '请输入房间号' }]"
          />
          <div class="dialog-footer">
            <van-button type="default" @click="showJoinDialog = false">取消</van-button>
            <van-button type="primary" native-type="submit" :loading="joining">
              加入
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showLoadingToast, closeToast } from 'vant'
import { useMahjongStore } from '@/store/modules/mahjong'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const mahjongStore = useMahjongStore()
const userStore = useUserStore()

// 弹窗控制
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)

// 创建表单
const createForm = reactive({
  ruleType: 1,       // 1=敲麻, 2=百搭
  flowerMode: 8,     // 8/20/36花
  playerCount: 4,
  totalRounds: 8,
  maxScore: 100,     // 0表示无封顶
  baseScore: 1,
  flyCount: 0
})

// 加入房间
const joinCode = ref('')

// 状态
const creating = ref(false)
const joining = ref(false)
const loading = ref(false)
const finished = ref(false)

// 数据
const activeGame = ref<any>(null)
const historyGames = ref<any[]>([])

onMounted(() => {
  loadActiveGame()
})

// 加载进行中的游戏
async function loadActiveGame() {
  try {
    const game = await mahjongStore.getActiveGame()
    activeGame.value = game
  } catch (e) {
    // 没有进行中的游戏
  }
}

// 加载历史记录
async function loadHistory() {
  try {
    const games = await mahjongStore.getMyGames()
    historyGames.value = games.filter((g: any) => g.gameStatus === 3)
    finished.value = true
  } catch (e) {
    console.error('加载历史记录失败', e)
  } finally {
    loading.value = false
  }
}

// 创建游戏
async function createGame() {
  creating.value = true
  showLoadingToast({ message: '创建中...', forbidClick: true })

  try {
    const game = await mahjongStore.createGame({
      ...createForm,
      maxScore: createForm.maxScore === 0 ? null : createForm.maxScore
    })
    closeToast()
    showCreateDialog.value = false
    router.push(`/mobile/mahjong/room/${game.id}`)
  } catch (e: any) {
    closeToast()
    showToast(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

// 加入游戏
async function joinGame() {
  joining.value = true
  showLoadingToast({ message: '加入中...', forbidClick: true })

  try {
    const game = await mahjongStore.joinGame(joinCode.value.toUpperCase())
    closeToast()
    showJoinDialog.value = false
    router.push(`/mobile/mahjong/room/${game.id}`)
  } catch (e: any) {
    closeToast()
    showToast(e.message || '加入失败')
  } finally {
    joining.value = false
  }
}

// 继续游戏
function continueGame() {
  if (activeGame.value) {
    router.push(`/mobile/mahjong/room/${activeGame.value.id}`)
  }
}

// 查看游戏
function viewGame(game: any) {
  router.push(`/mobile/mahjong/room/${game.id}`)
}

// 获取我的得分
function getMyScore(game: any): number {
  const userId = userStore.userInfo?.id
  if (game.player1Id === userId) return game.player1Score
  if (game.player2Id === userId) return game.player2Score
  if (game.player3Id === userId) return game.player3Score
  if (game.player4Id === userId) return game.player4Score
  return 0
}

// 格式化日期
function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.mahjong-lobby {
  min-height: 100vh;
  background: linear-gradient(180deg, #1a472a 0%, #0d2818 100%);
}

.lobby-content {
  padding: 16px;
}

.lobby-header {
  text-align: center;
  padding: 32px 0;
}

.logo-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.logo-text {
  font-size: 36px;
  font-weight: bold;
  color: #ffd93d;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.5);
}

.logo-sub {
  font-size: 16px;
  color: #aaa;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.active-game-card {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-title {
  color: #fff;
  font-weight: 500;
}

.card-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.game-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #aaa;
  font-size: 14px;
}

.history-section {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 16px;
}

.section-header {
  margin-bottom: 12px;
}

.section-title {
  color: #fff;
  font-weight: 500;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.history-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.game-code {
  color: #fff;
  font-weight: 500;
}

.game-type {
  color: #aaa;
  font-size: 12px;
}

.history-result {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.score {
  color: #ff4757;
  font-weight: bold;
}

.score.win {
  color: #4cd137;
}

.date {
  color: #666;
  font-size: 12px;
}

/* 弹窗样式 */
.create-dialog,
.join-dialog {
  padding: 16px;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.dialog-title {
  font-size: 18px;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  padding: 0 16px;
}

.join-dialog {
  min-width: 300px;
}

.join-dialog .dialog-footer {
  justify-content: flex-end;
}
</style>
