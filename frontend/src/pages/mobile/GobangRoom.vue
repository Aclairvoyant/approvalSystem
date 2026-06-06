<template>
  <div ref="pageRoot" class="gobang-room">
    <van-nav-bar :title="roomTitle" left-arrow fixed placeholder @click-left="handleBack">
      <template #right>
        <div class="nav-actions">
          <van-icon name="replay" size="20" @click="refreshRoom" />
          <van-icon name="question-o" size="20" @click="showRules = true" />
        </div>
      </template>
    </van-nav-bar>

    <div v-if="gobangStore.isGameWaiting" class="waiting-view">
      <div class="waiting-panel">
        <van-loading type="spinner" size="42" color="#0f766e" />
        <h3>等待对方加入</h3>
        <div class="room-code">
          <span>房间号</span>
          <strong>{{ gobangStore.currentGame?.gameCode }}</strong>
          <van-button size="small" type="primary" plain icon="description" @click="copyRoomCode">
            复制
          </van-button>
        </div>
        <div class="waiting-players">
          <div class="player">
            <van-image round width="44" height="44" :src="blackAvatar" />
            <span>{{ blackName }}</span>
            <van-tag color="#111827" text-color="#fff">黑棋</van-tag>
          </div>
          <div class="player muted">
            <van-image round width="44" height="44" :src="whiteAvatar" />
            <span>{{ whiteName }}</span>
            <van-tag color="#f3f4f6" text-color="#374151">白棋</van-tag>
          </div>
        </div>
        <van-button
          v-if="gobangStore.isRoomOwner"
          type="danger"
          plain
          block
          @click="handleCancelGame"
        >
          取消房间
        </van-button>
      </div>
    </div>

    <div v-else class="room-content">
      <section class="players-band">
        <div class="player" :class="{ active: gobangStore.currentTurnColor === GobangColor.BLACK }">
          <van-image round width="46" height="46" :src="blackAvatar" />
          <div class="player-text">
            <strong>{{ blackName }}</strong>
            <span>黑棋</span>
          </div>
          <van-tag v-if="winnerColor === GobangColor.BLACK" type="success">胜</van-tag>
        </div>
        <div class="turn-chip" :class="{ finished: gobangStore.isGameFinished }">
          {{ turnText }}
        </div>
        <div class="player" :class="{ active: gobangStore.currentTurnColor === GobangColor.WHITE }">
          <van-image round width="46" height="46" :src="whiteAvatar" />
          <div class="player-text">
            <strong>{{ whiteName }}</strong>
            <span>白棋</span>
          </div>
          <van-tag v-if="winnerColor === GobangColor.WHITE" type="success">胜</van-tag>
        </div>
      </section>

      <section class="board-section">
        <GobangBoard
          :board="gobangStore.displayBoard"
          :disabled="boardDisabled"
          :current-turn="gobangStore.currentTurnColor"
          :my-color="gobangStore.myColor"
          :last-move="gobangStore.displayLastMove"
          :winning-line="gobangStore.displayWinningLine"
          :analysis-points="gobangStore.displayAnalysisPoints"
          @place-stone="handlePlaceStone"
        />
      </section>

      <section class="actions-section">
        <div class="status-line">
          <span>{{ statusText }}</span>
          <div class="status-tools">
            <van-tag v-if="gobangStore.isConnected" type="success">实时连接</van-tag>
            <van-tag v-else type="warning">未连接</van-tag>
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

      <section ref="replayRoot" class="replay-section">
        <div class="replay-header">
          <div>
            <strong>复盘</strong>
            <span>{{ replaySummary }}</span>
          </div>
          <div class="review-actions">
            <van-tag v-if="gobangStore.aiReview" plain type="primary">{{ reviewSourceText }}</van-tag>
            <van-button
              size="small"
              plain
              type="primary"
              :loading="gobangStore.isReviewLoading"
              :disabled="!hasReplayMoves"
              @click="handleAiReview"
            >
              AI复盘
            </van-button>
          </div>
          <van-progress :percentage="gobangStore.replayProgress" :show-pivot="false" />
        </div>
        <div class="replay-controls">
          <van-button icon="replay" size="small" :disabled="!hasReplayMoves" @click="restartReplay" />
          <van-button icon="arrow-left" size="small" :disabled="!canStepBack" @click="stepReplay(-1)" />
          <van-button
            :icon="isReplayPlaying ? 'pause' : 'play'"
            size="small"
            type="primary"
            :disabled="!hasReplayMoves"
            @click="toggleReplay"
          />
          <van-button icon="arrow" size="small" :disabled="!canStepForward" @click="stepReplay(1)" />
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
                <van-icon name="guide-o" />
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
      @confirm="handleUndoResponse(true)"
      @cancel="handleUndoResponse(false)"
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
      <div class="result-content">
        <van-icon :name="gobangStore.isWinner ? 'medal-o' : 'smile-o'" size="58" color="#0f766e" />
        <p>{{ resultText }}</p>
      </div>
    </van-dialog>

    <van-action-sheet
      v-model:show="showMoreActions"
      :actions="moreActions"
      cancel-text="取消"
      close-on-click-action
      @select="handleMoreAction"
    />

    <van-popup v-model:show="showRules" position="bottom" round class="rules-popup">
      <div class="rules-content">
        <h3>五子棋规则</h3>
        <ul>
          <li>黑棋先行，双方交替落子。</li>
          <li>横、竖或斜向先连成五子的玩家获胜。</li>
          <li>悔棋需要对方同意，同一步只能申请一次。</li>
          <li>对局结束后可以留在房间内查看全局回放和 AI 复盘。</li>
        </ul>
        <van-button type="primary" block @click="showRules = false">知道了</van-button>
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
import { GobangColor, type GobangAiPoint } from '@/services/gobangApi'
import { useGobangStore } from '@/store/modules/gobang'
import { useUserStore } from '@/store/modules/user'

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

const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'
const pageRoot = ref<HTMLElement | null>(null)
const replayRoot = ref<HTMLElement | null>(null)
const reviewRoot = ref<HTMLElement | null>(null)
const showRules = ref(false)
const showUndoDialog = ref(false)
const showResultDialog = ref(false)
const showMoreActions = ref(false)
const isReplayPlaying = ref(false)
const reduceMotion = ref(false)

let replayTimeline: gsap.core.Timeline | null = null
let pageCtx: ReturnType<typeof gsap.context> | null = null
let media: ReturnType<typeof gsap.matchMedia> | null = null

const gameId = computed(() => Number(route.params.id))

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
const blackAvatar = computed(() => gobangStore.currentGame?.blackPlayerAvatar || defaultAvatar)
const whiteAvatar = computed(() => {
  return gobangStore.currentGame?.whitePlayerAvatar ||
    gobangStore.currentGame?.invitedPlayerAvatar ||
    defaultAvatar
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
      color: '#dc2626',
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

watch(
  () => gobangStore.isGameFinished,
  isFinished => {
    if (isFinished && !gobangStore.isReplayMode) {
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
    router.push('/mobile/game')
  }
}

async function loadReplayMoves(): Promise<void> {
  await gobangStore.fetchReplayMoves(gameId.value)
  buildReplayTimeline()
}

async function refreshRoom(): Promise<void> {
  await gobangStore.fetchGameDetail(gameId.value)
  await loadReplayMoves()
  showToast({ type: 'success', message: '已刷新' })
}

function handleBack(): void {
  router.push('/mobile/game')
}

function copyRoomCode(): void {
  const code = gobangStore.currentGame?.gameCode
  if (!code) return

  navigator.clipboard.writeText(code)
    .then(() => showToast({ type: 'success', message: '房间号已复制' }))
    .catch(() => showToast({ type: 'fail', message: '复制失败' }))
}

async function handleCancelGame(): Promise<void> {
  try {
    await showConfirmDialog({
      title: '取消房间',
      message: '确定要取消这个五子棋房间吗？'
    })
    await gobangStore.cancelGame()
    router.push('/mobile/game')
  } catch {
    // User cancelled.
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

function handleUndoResponse(accepted: boolean): void {
  gobangStore.respondUndo(accepted)
  showUndoDialog.value = false
}

async function ensureFreshReplayMoves(): Promise<boolean> {
  await loadReplayMoves()
  return gobangStore.replayMoves.length > 0
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
  if (!hasReplayMoves.value && !await ensureFreshReplayMoves()) return
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
  if (!hasReplayMoves.value && !await ensureFreshReplayMoves()) return

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

<style scoped lang="scss">
.gobang-room {
  min-height: 100vh;
  background: #f5f7f8;
  padding-bottom: 20px;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.waiting-view {
  min-height: calc(100vh - 46px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
}

.waiting-panel,
.players-band,
.board-section,
.actions-section,
.replay-section {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 8px rgba(15, 23, 42, 0.06);
}

.waiting-panel {
  width: 100%;
  padding: 24px 18px;
  text-align: center;

  h3 {
    margin: 16px 0;
    color: #111827;
    font-size: 18px;
  }
}

.room-code {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 12px;
  margin-bottom: 18px;
  background: #f3f4f6;
  border-radius: 8px;

  span {
    color: #6b7280;
    font-size: 13px;
  }

  strong {
    color: #0f766e;
    font-size: 22px;
    letter-spacing: 3px;
  }
}

.waiting-players {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 18px;
}

.player {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;

  &.muted {
    opacity: 0.72;
  }

  > span {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
    color: #111827;
  }
}

.room-content {
  padding: 12px;
}

.players-band {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 12px;
  margin-bottom: 12px;

  .player {
    padding: 8px;
    border: 1px solid #eef0f2;
    border-radius: 8px;

    &.active {
      border-color: #0f766e;
      background: #ecfdf5;
    }
  }
}

.player-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #111827;
    font-size: 14px;
  }

  span {
    color: #6b7280;
    font-size: 12px;
  }
}

.turn-chip {
  min-width: 72px;
  padding: 6px 8px;
  border-radius: 999px;
  text-align: center;
  color: #0f766e;
  background: #ccfbf1;
  font-size: 12px;
  font-weight: 600;

  &.finished {
    color: #6b7280;
    background: #f3f4f6;
  }
}

.board-section {
  padding: 12px;
  margin-bottom: 12px;
}

.actions-section,
.replay-section {
  padding: 14px;
  margin-bottom: 12px;
}

.status-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #374151;
  font-size: 14px;

  > span {
    min-width: 0;
    flex: 1;
  }
}

.status-tools {
  display: flex;
  align-items: center;
  gap: 8px;
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

  > div {
    min-width: 86px;
    display: flex;
    flex-direction: column;
    gap: 2px;

    strong {
      font-size: 15px;
      color: #111827;
    }

    span {
      color: #6b7280;
      font-size: 12px;
    }
  }

  .van-progress {
    grid-column: 1 / -1;
  }
}

.review-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.replay-controls {
  display: flex;
  gap: 8px;

  .van-button {
    flex: 1;
  }
}

.review-panel {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;

  &.severity-critical {
    border-color: rgba(220, 38, 38, 0.25);
    background: #fef2f2;
  }

  &.severity-warning {
    border-color: rgba(37, 99, 235, 0.22);
    background: #eff6ff;
  }

  &.severity-info {
    border-color: rgba(15, 118, 110, 0.24);
    background: #f0fdfa;
  }

  p {
    margin: 8px 0 0;
    color: #374151;
    font-size: 13px;
    line-height: 1.6;
  }
}

.review-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  color: #111827;
  font-size: 14px;
}

.review-title-main {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.win-rate-pill {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 7px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 11px;

  strong {
    font-size: 12px;
  }
}

.best-rate-line {
  margin-top: 7px;
  color: #dc2626;
  font-size: 12px;
  font-weight: 600;
}

.review-empty {
  color: #6b7280;
  font-size: 13px;
  text-align: center;
}

.suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;

  button {
    border: 1px solid #d1d5db;
    border-radius: 8px;
    padding: 6px 8px;
    background: #fff;
    color: #374151;
    font-size: 12px;
    display: inline-flex;
    align-items: center;
    gap: 6px;

    span {
      color: #0f766e;
      font-weight: 600;
    }

    strong {
      color: #111827;
      font-weight: 600;
    }
  }
}

.dialog-copy {
  padding: 18px;
  text-align: center;
  color: #374151;
}

.result-content {
  padding: 26px 18px;
  text-align: center;

  p {
    margin: 12px 0 0;
    color: #374151;
  }
}

.rules-popup {
  height: 52%;
}

.rules-content {
  height: 100%;
  padding: 18px;
  display: flex;
  flex-direction: column;

  h3 {
    margin: 0 0 12px;
    text-align: center;
    color: #111827;
    font-size: 18px;
  }

  ul {
    flex: 1;
    margin: 0;
    padding-left: 20px;
    color: #374151;
    line-height: 1.9;
    font-size: 14px;
  }
}

@media (max-width: 360px) {
  .room-content {
    padding: 8px;
  }

  .players-band {
    gap: 6px;
    padding: 8px;
  }

  .turn-chip {
    min-width: 62px;
    font-size: 11px;
  }

  .replay-controls {
    gap: 6px;
  }
}
</style>
