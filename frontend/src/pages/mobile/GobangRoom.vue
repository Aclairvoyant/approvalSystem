<template>
  <div ref="pageRoot" class="gobang-room">
    <van-nav-bar :title="roomTitle" left-arrow fixed placeholder @click-left="handleBack">
      <template #right>
        <div class="nav-actions">
          <van-icon name="replay" size="20" aria-label="刷新" @click="refreshRoom" />
          <van-icon name="question-o" size="20" aria-label="规则" @click="showRules = true" />
        </div>
      </template>
    </van-nav-bar>

    <div v-if="!gobangStore.currentGame" class="page-state">
      <van-loading size="24px" vertical>正在进入房间...</van-loading>
    </div>

    <div v-else-if="gobangStore.isGameWaiting" class="m-page">
      <section class="m-card waiting-panel">
        <h3>{{ canJoin ? `${blackName} 邀请你下五子棋` : '等对方加入' }}</h3>
        <div class="room-code">
          <strong class="tabular">{{ gobangStore.currentGame.gameCode }}</strong>
          <van-button size="small" plain type="primary" @click="copyRoomCode">复制</van-button>
        </div>
        <div class="waiting-players">
          <div class="player">
            <UserAvatar :src="gobangStore.currentGame.blackPlayerAvatar" :name="blackName" :size="40" />
            <span class="player-name">{{ blackName }}</span>
            <span class="stone stone-black" aria-label="黑棋"></span>
          </div>
          <div class="player" :class="{ muted: !gobangStore.currentGame.whitePlayerId }">
            <UserAvatar :src="whiteAvatarSrc" :name="whiteName" :size="40" />
            <span class="player-name">{{ whiteName }}</span>
            <span class="stone stone-white" aria-label="白棋"></span>
          </div>
        </div>
        <van-button v-if="canJoin" type="primary" block :loading="joining" @click="handleJoin">加入对局</van-button>
        <van-button v-if="gobangStore.isRoomOwner" plain block @click="handleCancelGame">取消房间</van-button>
      </section>
    </div>

    <div v-else class="m-page room-content">
      <section class="m-card players-band">
        <div class="player" :class="{ active: gobangStore.isGamePlaying && gobangStore.currentTurnColor === GobangColor.BLACK }">
          <UserAvatar :src="gobangStore.currentGame.blackPlayerAvatar" :name="blackName" :size="40" />
          <div class="player-text">
            <strong>{{ blackName }}</strong>
            <span><i class="stone stone-black"></i>黑棋<b v-if="winnerColor === GobangColor.BLACK" class="win">胜</b></span>
          </div>
        </div>
        <div class="turn-chip" :class="{ finished: gobangStore.isGameFinished }">
          {{ turnText }}
        </div>
        <div class="player" :class="{ active: gobangStore.isGamePlaying && gobangStore.currentTurnColor === GobangColor.WHITE }">
          <UserAvatar :src="whiteAvatarSrc" :name="whiteName" :size="40" />
          <div class="player-text">
            <strong>{{ whiteName }}</strong>
            <span><i class="stone stone-white"></i>白棋<b v-if="winnerColor === GobangColor.WHITE" class="win">胜</b></span>
          </div>
        </div>
      </section>

      <section class="m-card board-section">
        <GobangBoard
          :board="gobangStore.displayBoard"
          :disabled="boardDisabled || gobangStore.isPlacing"
          :current-turn="gobangStore.currentTurnColor"
          :my-color="gobangStore.myColor"
          :last-move="gobangStore.displayLastMove"
          :winning-line="gobangStore.displayWinningLine"
          :analysis-points="gobangStore.displayAnalysisPoints"
          @place-stone="handlePlaceStone"
        />
      </section>

      <section class="m-card actions-section">
        <div class="status-line">
          <span>{{ statusText }}</span>
          <div class="status-tools">
            <span class="conn" :class="{ off: !gobangStore.isConnected }">
              {{ gobangStore.isConnected ? '已连接' : '连接中断，正在重连' }}
            </span>
            <van-button
              class="more-action"
              icon="ellipsis"
              size="small"
              round
              plain
              :disabled="!gobangStore.isGamePlaying || gobangStore.isReplayMode"
              @click="showMoreActions = true"
            />
          </div>
        </div>
      </section>

      <section ref="replayRoot" class="m-card replay-section">
        <div class="replay-header">
          <div>
            <strong>复盘</strong>
            <span>{{ replaySummary }}</span>
          </div>
          <div class="review-actions">
            <span v-if="gobangStore.aiReview" class="review-source">{{ reviewSourceText }}</span>
            <van-button
              size="small"
              plain
              type="primary"
              :loading="gobangStore.isReviewLoading"
              :disabled="!replayAvailable"
              @click="handleAiReview"
            >
              AI 复盘
            </van-button>
          </div>
          <van-progress :percentage="gobangStore.replayProgress" :show-pivot="false" />
        </div>
        <div class="replay-controls">
          <van-button icon="replay" size="small" aria-label="从头回放" :disabled="!replayAvailable" @click="restartReplay" />
          <van-button icon="arrow-left" size="small" aria-label="上一步" :disabled="!canStepBack" @click="stepReplay(-1)" />
          <van-button
            :icon="isReplayPlaying ? 'pause' : 'play'"
            size="small"
            type="primary"
            :aria-label="isReplayPlaying ? '暂停' : '播放'"
            :disabled="!replayAvailable"
            @click="toggleReplay"
          />
          <van-button icon="arrow" size="small" aria-label="下一步" :disabled="!canStepForward" @click="stepReplay(1)" />
          <van-button size="small" plain :disabled="!gobangStore.isReplayMode" @click="exitReplay">
            返回棋局
          </van-button>
        </div>
        <div
          v-if="gobangStore.isReplayMode"
          ref="reviewRoot"
          class="review-panel"
          :class="reviewSeverityClass"
        >
          <template v-if="gobangStore.currentReplayInsight">
            <div class="review-title">
              <div class="review-title-main">
                <strong>{{ reviewSeverityText }}</strong>
              </div>
              <div v-if="currentWinRateText" class="win-rate-pill">
                <span>胜率预测</span>
                <strong>{{ currentWinRateText }}</strong>
              </div>
            </div>
            <div v-if="bestMoveWinRateText" class="best-rate-line">
              {{ bestMoveWinRateText }}
            </div>
            <p>{{ gobangStore.currentReplayInsight.summary }}</p>
            <div
              v-if="gobangStore.currentReplayInsight.suggestedPoints?.length"
              class="suggestion-list"
            >
              <button
                v-for="point in gobangStore.currentReplayInsight.suggestedPoints"
                :key="`${point.row}-${point.col}-${point.type}`"
                type="button"
              >
                <span>{{ suggestionLabel(point) }}</span>
                <strong>{{ point.row + 1 }} 行 {{ point.col + 1 }} 列</strong>
              </button>
            </div>
          </template>
          <div v-else class="review-empty">
            {{ gobangStore.isReviewLoading ? 'AI 复盘生成中' : '此步暂无重点建议' }}
          </div>
        </div>
      </section>
    </div>

    <van-dialog
      v-model:show="showUndoDialog"
      title="对方请求悔棋"
      show-cancel-button
      confirm-button-text="同意"
      cancel-button-text="拒绝"
      :close-on-click-overlay="false"
      :before-close="beforeUndoDialogClose"
    >
      <div class="dialog-copy">是否同意撤回最近一步？</div>
    </van-dialog>

    <van-dialog
      v-model:show="showResultDialog"
      :title="gobangStore.isWinner ? '你赢了' : '棋局结束'"
      show-cancel-button
      confirm-button-text="查看复盘"
      cancel-button-text="返回大厅"
      @confirm="handleResultReplay"
      @cancel="router.push('/mobile/game')"
    >
      <p class="result-content">{{ resultText }}</p>
    </van-dialog>

    <van-action-sheet
      v-model:show="showMoreActions"
      :actions="moreActions"
      cancel-text="取消"
      close-on-click-action
      @select="handleMoreAction"
    />

    <van-popup v-model:show="showRules" position="bottom" round closeable>
      <div class="rules-content">
        <div class="m-sheet-title">五子棋规则</div>
        <ul>
          <li>黑棋先行，双方交替落子。</li>
          <li>横、竖或斜向先连成五子的一方获胜。</li>
          <li>悔棋需要对方同意，同一步只能申请一次。</li>
          <li>对局结束后可以留在房间里回放全局，或生成 AI 复盘。</li>
        </ul>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { gsap } from 'gsap'
import GobangBoard from '@/components/GobangBoard.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { GobangColor, GobangGameStatus, type GobangAiPoint } from '@/services/gobangApi'
import { useGobangStore } from '@/store/modules/gobang'
import { useUserStore } from '@/store/modules/user'
import { copyText } from '@/utils/clipboard'

interface MoreAction {
  name: string
  key: 'undo' | 'surrender'
  subname?: string
  color?: string
  disabled?: boolean
}

const route = useRoute()
const router = useRouter()
const gobangStore = useGobangStore()
const userStore = useUserStore()

const pageRoot = ref<HTMLElement | null>(null)
const replayRoot = ref<HTMLElement | null>(null)
const reviewRoot = ref<HTMLElement | null>(null)
const showRules = ref(false)
const showUndoDialog = ref(false)
const showResultDialog = ref(false)
const showMoreActions = ref(false)
const isReplayPlaying = ref(false)
const reduceMotion = ref(false)
const joining = ref(false)

let replayTimeline: gsap.core.Timeline | null = null
let pageCtx: ReturnType<typeof gsap.context> | null = null
let media: ReturnType<typeof gsap.matchMedia> | null = null

// MobileLayout 以 path 作 key，换房间会重新挂载，这里读一次即可
const gameId = computed(() => Number(route.params.id))

// 被邀请的一方打开等待中的房间时可以直接加入
const canJoin = computed(() => {
  const game = gobangStore.currentGame
  return !!game &&
    gobangStore.isGameWaiting &&
    !game.whitePlayerId &&
    game.blackPlayerId !== userStore.userId &&
    (!game.invitedPlayerId || game.invitedPlayerId === userStore.userId)
})

const roomTitle = computed(() => {
  if (gobangStore.isGameWaiting) return '等待加入'
  if (gobangStore.isGameFinished) return '五子棋结束'
  return '五子棋'
})

const blackName = computed(() => gobangStore.currentGame?.blackPlayerName || '黑棋')
const whiteName = computed(() => {
  return gobangStore.currentGame?.whitePlayerName ||
    gobangStore.currentGame?.invitedPlayerName ||
    '等待中'
})
const whiteAvatarSrc = computed(() => {
  return gobangStore.currentGame?.whitePlayerAvatar ||
    gobangStore.currentGame?.invitedPlayerAvatar ||
    ''
})

const winnerColor = computed(() => {
  const winnerId = gobangStore.currentGame?.winnerId
  if (!winnerId) return null
  if (winnerId === gobangStore.currentGame?.blackPlayerId) return GobangColor.BLACK
  if (winnerId === gobangStore.currentGame?.whitePlayerId) return GobangColor.WHITE
  return null
})

const turnText = computed(() => {
  if (gobangStore.isGameFinished) return '已结束'
  return gobangStore.currentTurnColor === GobangColor.BLACK ? '黑棋回合' : '白棋回合'
})

const statusText = computed(() => {
  if (gobangStore.isReplayMode) return '正在复盘'
  if (gobangStore.isGameFinished) return resultText.value
  if (gobangStore.isMyTurn) return '轮到你落子'
  return '等待对方落子'
})

const resultText = computed(() => {
  if (!gobangStore.currentGame?.winnerId) return '本局已经结束'
  return gobangStore.isWinner ? '本局由你获胜' : '对方赢得本局'
})

const boardDisabled = computed(() => {
  return !gobangStore.isMyTurn || !gobangStore.isGamePlaying || gobangStore.isReplayMode
})

const hasReplayMoves = computed(() => gobangStore.replayMoves.length > 0)
// 对局进行中棋谱会增长，按钮不依赖已加载的棋谱，点击时再拉最新的
const replayAvailable = computed(() => gobangStore.isGamePlaying || gobangStore.isGameFinished)
const canStepBack = computed(() => gobangStore.isReplayMode && gobangStore.replayIndex > 0)
const canStepForward = computed(() => {
  return gobangStore.isReplayMode &&
    gobangStore.replayIndex < gobangStore.replayMoves.length
})

const replaySummary = computed(() => {
  if (!hasReplayMoves.value) return '暂无棋谱'
  return `${gobangStore.replayIndex}/${gobangStore.replayMoves.length} 步`
})

const reviewSourceText = computed(() => {
  if (!gobangStore.aiReview) return ''
  return isMimoReviewSource(gobangStore.aiReview.source) ? 'MiMo 复盘' : '本地复盘'
})

const reviewSeverityClass = computed(() => {
  const severity = gobangStore.currentReplayInsight?.severity || 'good'
  return `severity-${severity}`
})

const reviewSeverityText = computed(() => {
  const severity = gobangStore.currentReplayInsight?.severity
  if (severity === 'critical') return '关键点'
  if (severity === 'warning') return '注意点'
  if (severity === 'info') return '可优化'
  return '稳健'
})

const currentWinRateText = computed(() => {
  const winRate = normalizeRate(gobangStore.currentReplayInsight?.winRate)
  return winRate === null ? '' : `${winRate}%`
})

const bestMoveWinRateText = computed(() => {
  const winRate = normalizeRate(gobangStore.currentReplayInsight?.winRate)
  const bestMoveWinRate = normalizeRate(gobangStore.currentReplayInsight?.bestMoveWinRate)
  if (winRate === null || bestMoveWinRate === null) return ''

  const gap = bestMoveWinRate - winRate
  if (gap >= 8) return `最佳点胜率可到 ${bestMoveWinRate}%，本手少 ${gap} 个点`
  return ''
})

const moreActions = computed<MoreAction[]>(() => {
  const disabled = !gobangStore.isGamePlaying || gobangStore.isReplayMode
  return [
    {
      name: '申请悔棋',
      key: 'undo',
      subname: '同一步只可申请一次',
      disabled
    },
    {
      name: '认输',
      key: 'surrender',
      subname: '立即结束本局',
      color: 'var(--danger)',
      disabled
    }
  ]
})

watch(
  () => gobangStore.undoRequest,
  request => {
    showUndoDialog.value = !!request && request.requesterId !== userStore.userId
  }
)

// 只有在房间里看着对局结束时才弹结果；从历史记录打开已结束的对局不弹
watch(
  () => gobangStore.currentGame?.gameStatus,
  (status, previous) => {
    if (status === GobangGameStatus.FINISHED && previous === GobangGameStatus.PLAYING && !gobangStore.isReplayMode) {
      showResultDialog.value = true
      void loadReplayMoves()
    }
  }
)

watch(
  () => gobangStore.replayMoves.length,
  () => {
    buildReplayTimeline()
  }
)

watch(
  () => gobangStore.currentReplayInsight,
  () => {
    void animateReviewPanel()
  }
)

function animationDuration(seconds: number): number {
  return reduceMotion.value ? 0 : seconds
}

function timelineStepDuration(): number {
  return reduceMotion.value ? 0.01 : 0.45
}

function buildReplayTimeline(): void {
  replayTimeline?.kill()
  replayTimeline = gsap.timeline({
    paused: true,
    onComplete: () => {
      isReplayPlaying.value = false
    }
  })

  if (!gobangStore.replayMoves.length) return

  replayTimeline.add(() => gobangStore.setReplayIndex(0), 0)
  gobangStore.replayMoves.forEach((_, index) => {
    replayTimeline?.add(
      () => gobangStore.setReplayIndex(index + 1),
      (index + 1) * timelineStepDuration()
    )
  })
}

async function initRoom(): Promise<void> {
  try {
    await gobangStore.enterGameRoom(gameId.value)
    buildReplayTimeline()
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加载五子棋失败' })
    router.replace('/mobile/game')
  }
}

async function loadReplayMoves(): Promise<void> {
  try {
    await gobangStore.fetchReplayMoves(gameId.value)
    buildReplayTimeline()
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '棋谱加载失败' })
  }
}

async function refreshRoom(): Promise<void> {
  try {
    await gobangStore.fetchGameDetail(gameId.value)
    await loadReplayMoves()
    showToast({ type: 'success', message: '已刷新' })
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '刷新失败' })
  }
}

function handleBack(): void {
  router.push('/mobile/game')
}

async function copyRoomCode(): Promise<void> {
  const code = gobangStore.currentGame?.gameCode
  if (!code) return
  try {
    await copyText(code)
    showToast({ type: 'success', message: '房间号已复制' })
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '复制失败' })
  }
}

async function handleJoin(): Promise<void> {
  const code = gobangStore.currentGame?.gameCode
  if (!code) return
  joining.value = true
  try {
    await gobangStore.joinGame(code)
    await gobangStore.enterGameRoom(gameId.value)
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加入失败' })
  } finally {
    joining.value = false
  }
}

async function handleCancelGame(): Promise<void> {
  try {
    await showConfirmDialog({
      title: '取消房间',
      message: '确定要取消这个五子棋房间吗？'
    })
  } catch {
    return
  }
  try {
    await gobangStore.cancelGame()
    router.push('/mobile/game')
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '取消失败' })
  }
}

function handlePlaceStone(row: number, col: number): void {
  gobangStore.placeStone(row, col)
}

function handleRequestUndo(): void {
  gobangStore.requestUndo()
  showToast({ message: '已发送悔棋请求', position: 'top' })
}

async function handleSurrender(): Promise<void> {
  try {
    await showConfirmDialog({
      title: '认输',
      message: '认输后棋局会立即结束，确定继续吗？'
    })
    gobangStore.surrender()
  } catch {
    // User cancelled.
  }
}

function handleMoreAction(action: MoreAction): void {
  if (action.disabled) return
  if (action.key === 'undo') {
    handleRequestUndo()
    return
  }
  if (action.key === 'surrender') {
    void handleSurrender()
  }
}

// 发送失败（断线）时弹窗保持打开，可以重试
function beforeUndoDialogClose(action: string): boolean {
  return gobangStore.respondUndo(action === 'confirm')
}

async function ensureFreshReplayMoves(): Promise<boolean> {
  try {
    await gobangStore.ensureReplayMoves()
    buildReplayTimeline()
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '棋谱加载失败' })
    return false
  }
  if (gobangStore.replayMoves.length === 0) {
    showToast({ message: '还没有落子', position: 'top' })
    return false
  }
  return true
}

async function restartReplay(): Promise<void> {
  if (!await ensureFreshReplayMoves()) return
  gobangStore.startReplay()
  isReplayPlaying.value = false
  buildReplayTimeline()
  replayTimeline?.pause(0)
  animateReplayControls()
}

async function handleResultReplay(): Promise<void> {
  showResultDialog.value = false
  await restartReplay()
}

async function handleAiReview(): Promise<void> {
  if (!await ensureFreshReplayMoves()) return
  if (isMimoReviewSource(gobangStore.aiReview?.source)) {
    showToast({ message: 'AI复盘已生成', position: 'top' })
    return
  }

  try {
    const hasLocalReview = !!gobangStore.aiReview
    await showConfirmDialog({
      title: 'AI复盘',
      message: hasLocalReview
        ? '当前只有本地复盘，将调用 MiMo 模型重新分析本局棋谱，确认生成吗？'
        : '将调用 MiMo 模型分析本局棋谱，确认生成吗？',
      confirmButtonText: '生成',
      cancelButtonText: '稍后'
    })
    const review = await gobangStore.fetchAiReview(gameId.value)
    if (!review) return
    if (!isMimoReviewSource(review.source)) {
      showToast({ message: 'MiMo 暂不可用，已展示本地复盘', position: 'top' })
    }
    if (!gobangStore.isReplayMode) {
      gobangStore.startReplay()
      buildReplayTimeline()
      replayTimeline?.pause(0)
    }
  } catch {
    // User cancelled.
  }
}

async function toggleReplay(): Promise<void> {
  if (!gobangStore.isReplayMode && !await ensureFreshReplayMoves()) return

  if (!gobangStore.isReplayMode) {
    await restartReplay()
  }

  if (isReplayPlaying.value) {
    replayTimeline?.pause()
    isReplayPlaying.value = false
    return
  }

  if (gobangStore.replayIndex >= gobangStore.replayMoves.length) {
    replayTimeline?.restart()
  } else {
    replayTimeline?.time(gobangStore.replayIndex * timelineStepDuration())
    replayTimeline?.play()
  }
  isReplayPlaying.value = true
  animateReplayControls()
}

async function stepReplay(direction: -1 | 1): Promise<void> {
  if (!gobangStore.isReplayMode) {
    if (!await ensureFreshReplayMoves()) return
    gobangStore.startReplay()
  }
  isReplayPlaying.value = false
  replayTimeline?.pause()
  gobangStore.setReplayIndex(gobangStore.replayIndex + direction)
  animateReplayControls()
}

function exitReplay(): void {
  replayTimeline?.pause()
  isReplayPlaying.value = false
  gobangStore.stopReplay()
}

function suggestionLabel(point: GobangAiPoint): string {
  if (point.label) return point.label
  if (point.type === 'WINNING_MOVE') return '必杀点'
  if (point.type === 'BLOCK') return '防守点'
  if (point.type === 'BETTER_MOVE') return '更优点'
  return '建议点'
}

function isMimoReviewSource(source: unknown): boolean {
  return String(source || '').toLowerCase() === 'mimo'
}

function normalizeRate(value: unknown): number | null {
  const rate = Number(value)
  if (!Number.isFinite(rate)) return null
  return Math.max(0, Math.min(100, Math.round(rate)))
}

async function animateReplayControls(): Promise<void> {
  await nextTick()
  if (!replayRoot.value || !pageCtx) return

  pageCtx.add(() => {
    gsap.fromTo(
      replayRoot.value,
      { scale: 0.98, autoAlpha: 0.82 },
      {
        scale: 1,
        autoAlpha: 1,
        duration: animationDuration(0.18),
        ease: 'power1.out',
        overwrite: 'auto'
      }
    )
  })
}

async function animateReviewPanel(): Promise<void> {
  await nextTick()
  if (!reviewRoot.value || !pageCtx) return

  pageCtx.add(() => {
    gsap.fromTo(
      reviewRoot.value,
      { y: 6, autoAlpha: 0.72 },
      {
        y: 0,
        autoAlpha: 1,
        duration: animationDuration(0.18),
        ease: 'power1.out',
        overwrite: 'auto'
      }
    )
  })
}

onMounted(() => {
  if (pageRoot.value) {
    pageCtx = gsap.context(() => {}, pageRoot.value)
  }

  media = gsap.matchMedia()
  media.add(
    { reduceMotion: '(prefers-reduced-motion: reduce)' },
    context => {
      reduceMotion.value = Boolean(context.conditions?.reduceMotion)
    }
  )

  void initRoom()
})

onUnmounted(() => {
  replayTimeline?.kill()
  replayTimeline = null
  media?.revert()
  media = null
  pageCtx?.revert()
  pageCtx = null
  gobangStore.leaveGameRoom()
})
</script>

<style scoped>
.gobang-room {
  min-height: 100%;
  background: var(--bg);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.page-state {
  padding-top: 96px;
  text-align: center;
}

.waiting-panel {
  padding: 24px 16px;
  text-align: center;
}

.waiting-panel h3 {
  color: var(--ink);
  font-size: 18px;
}

.waiting-panel .van-button + .van-button {
  margin-top: 10px;
}

.room-code {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 16px 0 20px;
}

.room-code strong {
  color: var(--ink);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.waiting-players {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 20px;
}

.player {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.player.muted {
  opacity: 0.6;
}

.player-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stone {
  display: inline-block;
  flex-shrink: 0;
  width: 12px;
  height: 12px;
  margin-right: 4px;
  border-radius: 50%;
  vertical-align: -1px;
}

.stone-black {
  background: #1f1f1f;
}

.stone-white {
  border: 1px solid var(--rule-strong);
  background: #fff;
}

.room-content > * + * {
  margin-top: 10px;
}

.players-band {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 10px;
}

.players-band .player {
  padding: 8px;
  border: 1px solid transparent;
  border-radius: var(--radius);
}

.players-band .player.active {
  border-color: var(--rule);
  background: var(--surface-2);
}

.player-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.player-text strong,
.player-text span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-text strong {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.player-text span {
  color: var(--ink-3);
  font-size: 12px;
}

.win {
  margin-left: 6px;
  color: var(--ok-ink);
  font-weight: 600;
}

.turn-chip {
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.turn-chip.finished {
  color: var(--ink-3);
}

.board-section {
  padding: 10px;
}

.actions-section,
.replay-section {
  padding: 14px;
}

.status-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--ink);
  font-size: 14px;
}

.status-line > span {
  flex: 1;
  min-width: 0;
}

.status-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conn {
  color: var(--ink-3);
  font-size: 12px;
}

.conn.off {
  color: var(--danger);
}

.more-action {
  width: 30px;
  height: 30px;
  padding: 0;
}

.replay-header {
  display: grid;
  grid-template-columns: minmax(86px, 1fr) auto;
  align-items: start;
  gap: 10px;
  margin-bottom: 12px;
}

.replay-header > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.replay-header strong {
  color: var(--ink);
  font-size: 15px;
}

.replay-header span {
  color: var(--ink-3);
  font-size: 12px;
}

.replay-header .van-progress {
  grid-column: 1 / -1;
}

.review-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.review-source {
  color: var(--ink-3);
  font-size: 12px;
}

.replay-controls {
  display: flex;
  gap: 8px;
}

.replay-controls .van-button {
  flex: 1;
}

.replay-controls .van-button:last-child {
  flex: 1.8;
  white-space: nowrap;
}

/* 复盘说明：中性底色，用文字标明轻重，颜色只落在标题上 */
.review-panel {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface-2);
}

.review-panel p {
  margin-top: 6px;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.6;
}

.review-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: var(--ink);
  font-size: 14px;
}

.severity-critical .review-title-main strong {
  color: var(--danger);
}

.severity-warning .review-title-main strong {
  color: var(--accent);
}

.win-rate-pill {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  color: var(--ink-3);
  font-size: 12px;
}

.win-rate-pill strong {
  color: var(--ink);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.best-rate-line {
  margin-top: 6px;
  color: var(--danger);
  font-size: 12px;
}

.review-empty {
  color: var(--ink-3);
  font-size: 13px;
  text-align: center;
}

.suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.suggestion-list button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink-2);
  font-size: 12px;
}

.suggestion-list strong {
  color: var(--ink);
  font-weight: 600;
}

.dialog-copy {
  padding: 16px 24px 20px;
  color: var(--ink-2);
  text-align: center;
}

.result-content {
  padding: 8px 24px 20px;
  color: var(--ink-2);
  font-size: 14px;
  text-align: center;
}

.rules-content {
  padding-bottom: calc(20px + env(safe-area-inset-bottom));
}

.rules-content ul {
  margin: 0;
  padding: 4px 20px 0 36px;
  color: var(--ink);
  font-size: 14px;
  line-height: 1.9;
}

@media (max-width: 360px) {
  .players-band {
    gap: 4px;
    padding: 6px;
  }

  .replay-controls {
    gap: 6px;
  }
}
</style>
