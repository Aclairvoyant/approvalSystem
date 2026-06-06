<template>
  <div ref="boardRoot" class="gobang-board">
    <div class="board-grid" role="grid" aria-label="五子棋棋盘">
      <button
        v-for="cell in cells"
        :key="`${cell.row}-${cell.col}`"
        class="board-cell"
        :class="{
          occupied: cell.value !== GobangColor.EMPTY,
          disabled: isCellDisabled(cell.row, cell.col),
          pending: isPendingPoint(cell.row, cell.col),
          last: isLastMove(cell.row, cell.col)
        }"
        :data-row="cell.row"
        :data-col="cell.col"
        type="button"
        role="gridcell"
        :aria-label="getCellLabel(cell)"
        :disabled="isCellDisabled(cell.row, cell.col)"
        @click="handleCellClick(cell.row, cell.col)"
      >
        <span v-if="isStarPoint(cell.row, cell.col)" class="star-point" />
        <span
          v-for="point in getAnalysisPointsAt(cell.row, cell.col)"
          :key="`${point.row}-${point.col}-${point.type}-${point.label}`"
          class="analysis-marker"
          :class="getAnalysisClass(point)"
          :title="getAnalysisTitle(point)"
        >
          <span class="analysis-ping" />
          <span class="analysis-label">{{ getAnalysisShortLabel(point) }}</span>
        </span>
        <span
          v-if="cell.value !== GobangColor.EMPTY"
          class="stone"
          :class="getStoneClass(cell.value)"
          :data-stone-key="getStoneKey(cell.row, cell.col, cell.value)"
        >
          <span v-if="isLastMove(cell.row, cell.col)" class="last-dot" />
          <span v-if="isWinningCell(cell.row, cell.col)" class="winning-ring" />
        </span>
        <span
          v-else-if="isPendingPoint(cell.row, cell.col)"
          class="stone pending-stone"
          :class="getStoneClass(previewColor)"
          data-pending-stone="true"
        />
        <span
          v-for="ghost in getGhostsAt(cell.row, cell.col)"
          :key="ghost.id"
          class="stone ghost-stone"
          :class="getStoneClass(ghost.color)"
          :data-ghost-key="ghost.id"
        />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { gsap } from 'gsap'
import {
  GOBANG_BOARD_SIZE,
  GobangColor,
  type GobangAiPoint,
  type GobangCell,
  type GobangPoint
} from '@/services/gobangApi'

interface BoardCell {
  row: number
  col: number
  value: GobangCell
}

interface GhostStone {
  id: string
  row: number
  col: number
  color: GobangCell
}

interface Props {
  board: GobangCell[][]
  disabled?: boolean
  currentTurn?: number
  myColor?: number | null
  lastMove?: GobangPoint | null
  winningLine?: GobangPoint[]
  analysisPoints?: GobangAiPoint[]
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  currentTurn: GobangColor.BLACK,
  myColor: null,
  lastMove: null,
  winningLine: () => [],
  analysisPoints: () => []
})

const emit = defineEmits<{
  (event: 'place-stone', row: number, col: number): void
}>()

const boardRoot = ref<HTMLElement | null>(null)
const ghostStones = ref<GhostStone[]>([])
const pendingPoint = ref<GobangPoint | null>(null)
const prefersReducedMotion = ref(false)

let ctx: ReturnType<typeof gsap.context> | null = null
let media: ReturnType<typeof gsap.matchMedia> | null = null
let audioContext: AudioContext | null = null

const cells = computed<BoardCell[]>(() => {
  const items: BoardCell[] = []
  for (let row = 0; row < GOBANG_BOARD_SIZE; row += 1) {
    for (let col = 0; col < GOBANG_BOARD_SIZE; col += 1) {
      items.push({
        row,
        col,
        value: props.board[row]?.[col] ?? GobangColor.EMPTY
      })
    }
  }
  return items
})

const previewColor = computed(() => {
  if (props.myColor === GobangColor.BLACK || props.myColor === GobangColor.WHITE) {
    return props.myColor
  }
  return props.currentTurn === GobangColor.WHITE ? GobangColor.WHITE : GobangColor.BLACK
})

function getStoneClass(color: GobangCell | number): string {
  return color === GobangColor.BLACK ? 'black' : 'white'
}

function getStoneKey(row: number, col: number, color: GobangCell): string {
  return `stone-${row}-${col}-${color}`
}

function isLastMove(row: number, col: number): boolean {
  return props.lastMove?.row === row && props.lastMove?.col === col
}

function isWinningCell(row: number, col: number): boolean {
  return props.winningLine.some(point => point.row === row && point.col === col)
}

function isPendingPoint(row: number, col: number): boolean {
  return pendingPoint.value?.row === row && pendingPoint.value?.col === col
}

function isCellDisabled(row: number, col: number): boolean {
  return props.disabled || props.board[row]?.[col] !== GobangColor.EMPTY
}

function isStarPoint(row: number, col: number): boolean {
  return [3, 7, 11].includes(row) && [3, 7, 11].includes(col)
}

function getCellLabel(cell: BoardCell): string {
  if (cell.value === GobangColor.BLACK) return `第${cell.row + 1}行第${cell.col + 1}列，黑棋`
  if (cell.value === GobangColor.WHITE) return `第${cell.row + 1}行第${cell.col + 1}列，白棋`
  return `第${cell.row + 1}行第${cell.col + 1}列，空位`
}

function getGhostsAt(row: number, col: number): GhostStone[] {
  return ghostStones.value.filter(stone => stone.row === row && stone.col === col)
}

function getAnalysisPointsAt(row: number, col: number): GobangAiPoint[] {
  return props.analysisPoints.filter(point => point.row === row && point.col === col)
}

function getAnalysisClass(point: GobangAiPoint): string {
  const type = point.type || 'COMMENTARY'
  return `type-${String(type).toLowerCase().replace(/_/g, '-')}`
}

function getAnalysisShortLabel(point: GobangAiPoint): string {
  if (point.type === 'WINNING_MOVE') return '杀'
  if (point.type === 'BLOCK') return '挡'
  if (point.type === 'BETTER_MOVE') return '优'
  if (point.label) return point.label.slice(0, 1)
  return '议'
}

function getAnalysisTitle(point: GobangAiPoint): string {
  const label = point.label || getAnalysisFullLabel(point)
  return point.score ? `${label}，评分 ${point.score}` : label
}

function getAnalysisFullLabel(point: GobangAiPoint): string {
  if (point.type === 'WINNING_MOVE') return '必杀点'
  if (point.type === 'BLOCK') return '防守点'
  if (point.type === 'BETTER_MOVE') return '更优点'
  return '建议点'
}

function duration(seconds: number): number {
  return prefersReducedMotion.value ? 0 : seconds
}

function runInContext(animation: () => void): void {
  if (ctx) {
    ctx.add(animation)
    return
  }

  animation()
}

function animateStoneEnter(row: number, col: number, color: GobangCell): void {
  const target = boardRoot.value?.querySelector<HTMLElement>(
    `[data-stone-key="${getStoneKey(row, col, color)}"]`
  )
  if (!target) return

  runInContext(() => {
    gsap.fromTo(
      target,
      { scale: 0.62, autoAlpha: 0 },
      {
        scale: 1,
        autoAlpha: 1,
        duration: duration(0.22),
        ease: 'back.out(1.5)',
        overwrite: 'auto'
      }
    )
  })
}

function animatePendingStone(): void {
  const target = boardRoot.value?.querySelector<HTMLElement>('[data-pending-stone="true"]')
  if (!target) return

  runInContext(() => {
    gsap.fromTo(
      target,
      { scale: 0.72, autoAlpha: 0 },
      {
        scale: 0.92,
        autoAlpha: 1,
        duration: duration(0.16),
        ease: 'power1.out',
        overwrite: 'auto'
      }
    )
  })
}

function animateGhostExit(ghost: GhostStone): void {
  const target = boardRoot.value?.querySelector<HTMLElement>(`[data-ghost-key="${ghost.id}"]`)
  if (!target) {
    removeGhost(ghost.id)
    return
  }

  runInContext(() => {
    gsap.fromTo(
      target,
      { scale: 1, autoAlpha: 1 },
      {
        scale: 0.35,
        autoAlpha: 0,
        duration: duration(0.18),
        ease: 'power1.out',
        overwrite: 'auto',
        onComplete: () => removeGhost(ghost.id)
      }
    )
  })
}

function animateWinningLine(): void {
  if (!props.winningLine.length) return

  const targets = props.winningLine
    .map(point => {
      const color = props.board[point.row]?.[point.col] ?? GobangColor.EMPTY
      if (color === GobangColor.EMPTY) return null
      return boardRoot.value?.querySelector<HTMLElement>(
        `[data-stone-key="${getStoneKey(point.row, point.col, color)}"]`
      ) ?? null
    })
    .filter((target): target is HTMLElement => target !== null)

  if (!targets.length) return

  runInContext(() => {
    gsap.fromTo(
      targets,
      { scale: 1 },
      {
        scale: 1.14,
        duration: duration(0.18),
        ease: 'power1.out',
        repeat: prefersReducedMotion.value ? 0 : 1,
        yoyo: true,
        stagger: prefersReducedMotion.value ? 0 : 0.04,
        overwrite: 'auto'
      }
    )
  })
}

function animateAnalysisMarkers(): void {
  const targets = boardRoot.value?.querySelectorAll<HTMLElement>('.analysis-marker')
  if (!targets?.length) return

  runInContext(() => {
    gsap.fromTo(
      targets,
      { scale: 0.72, autoAlpha: 0 },
      {
        scale: 1,
        autoAlpha: 1,
        duration: duration(0.2),
        ease: 'back.out(1.35)',
        stagger: prefersReducedMotion.value ? 0 : 0.04,
        overwrite: 'auto'
      }
    )
  })
}

function addGhost(row: number, col: number, color: GobangCell): GhostStone {
  const ghost = {
    id: `ghost-${row}-${col}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    row,
    col,
    color
  }
  ghostStones.value.push(ghost)
  return ghost
}

function removeGhost(id: string): void {
  ghostStones.value = ghostStones.value.filter(stone => stone.id !== id)
}

function clearPendingPoint(): void {
  pendingPoint.value = null
}

async function handleCellClick(row: number, col: number): Promise<void> {
  if (isCellDisabled(row, col)) return

  if (!isPendingPoint(row, col)) {
    pendingPoint.value = { row, col }
    await nextTick()
    animatePendingStone()
    return
  }

  clearPendingPoint()
  emit('place-stone', row, col)
}

function ensureAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null
  if (audioContext) return audioContext

  const AudioContextClass = window.AudioContext ||
    (window as Window & typeof globalThis & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!AudioContextClass) return null

  audioContext = new AudioContextClass()
  return audioContext
}

function playStoneSound(): void {
  const context = ensureAudioContext()
  if (!context) return

  void context.resume()
  const now = context.currentTime
  const oscillator = context.createOscillator()
  const gain = context.createGain()

  oscillator.type = 'sine'
  oscillator.frequency.setValueAtTime(360, now)
  oscillator.frequency.exponentialRampToValueAtTime(150, now + 0.08)
  gain.gain.setValueAtTime(0.001, now)
  gain.gain.exponentialRampToValueAtTime(0.16, now + 0.01)
  gain.gain.exponentialRampToValueAtTime(0.001, now + 0.1)

  oscillator.connect(gain)
  gain.connect(context.destination)
  oscillator.start(now)
  oscillator.stop(now + 0.11)
}

watch(
  () => props.board,
  async (nextBoard, previousBoard) => {
    if (!previousBoard) return

    const added: Array<{ row: number; col: number; color: GobangCell }> = []
    const removed: GhostStone[] = []

    for (let row = 0; row < GOBANG_BOARD_SIZE; row += 1) {
      for (let col = 0; col < GOBANG_BOARD_SIZE; col += 1) {
        const oldValue = previousBoard[row]?.[col] ?? GobangColor.EMPTY
        const newValue = nextBoard[row]?.[col] ?? GobangColor.EMPTY

        if (oldValue === newValue) continue
        if (oldValue === GobangColor.EMPTY && newValue !== GobangColor.EMPTY) {
          added.push({ row, col, color: newValue })
        }
        if (oldValue !== GobangColor.EMPTY && newValue === GobangColor.EMPTY) {
          removed.push(addGhost(row, col, oldValue))
        }
      }
    }

    if (pendingPoint.value && nextBoard[pendingPoint.value.row]?.[pendingPoint.value.col] !== GobangColor.EMPTY) {
      clearPendingPoint()
    }

    await nextTick()
    if (added.length > 0) playStoneSound()
    added.forEach(item => animateStoneEnter(item.row, item.col, item.color))
    removed.forEach(ghost => animateGhostExit(ghost))
  },
  { deep: true }
)

watch(
  () => [props.disabled, props.currentTurn, props.myColor],
  () => clearPendingPoint()
)

watch(
  () => props.winningLine,
  async () => {
    await nextTick()
    animateWinningLine()
  },
  { deep: true }
)

watch(
  () => props.analysisPoints,
  async () => {
    await nextTick()
    animateAnalysisMarkers()
  },
  { deep: true }
)

onMounted(() => {
  if (boardRoot.value) {
    ctx = gsap.context(() => {}, boardRoot.value)
  }

  media = gsap.matchMedia()
  media.add(
    { reduceMotion: '(prefers-reduced-motion: reduce)' },
    context => {
      prefersReducedMotion.value = Boolean(context.conditions?.reduceMotion)
    }
  )
})

onUnmounted(() => {
  media?.revert()
  media = null
  ctx?.revert()
  ctx = null
  if (audioContext && audioContext.state !== 'closed') {
    void audioContext.close()
  }
  audioContext = null
})
</script>

<style scoped lang="scss">
.gobang-board {
  width: 100%;
  display: flex;
  justify-content: center;
}

.board-grid {
  width: 100%;
  max-width: min(92vw, 620px);
  aspect-ratio: 1;
  display: grid;
  grid-template-columns: repeat(15, minmax(0, 1fr));
  grid-template-rows: repeat(15, minmax(0, 1fr));
  padding: clamp(10px, 2.8vw, 18px);
  border: 1px solid #9f7a42;
  border-radius: 8px;
  background: #d9ae6c;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.3),
    inset 0 12px 22px rgba(255, 255, 255, 0.13),
    0 8px 18px rgba(97, 63, 25, 0.12);
  touch-action: manipulation;
}

.board-cell {
  position: relative;
  min-width: 0;
  min-height: 0;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: pointer;
  overflow: visible;

  &::before,
  &::after {
    content: '';
    position: absolute;
    z-index: 0;
    pointer-events: none;
    background: rgba(88, 55, 20, 0.62);
  }

  &::before {
    top: 50%;
    left: 0;
    right: 0;
    height: 1px;
    transform: translateY(-50%);
  }

  &::after {
    top: 0;
    bottom: 0;
    left: 50%;
    width: 1px;
    transform: translateX(-50%);
  }

  &[data-col='0']::before {
    left: 50%;
  }

  &[data-col='14']::before {
    right: 50%;
  }

  &[data-row='0']::after {
    top: 50%;
  }

  &[data-row='14']::after {
    bottom: 50%;
  }

  &.disabled {
    cursor: default;
  }

  &:focus-visible {
    outline: 2px solid #0f766e;
    outline-offset: -2px;
  }
}

.star-point {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 1;
  width: 17%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: rgba(66, 38, 12, 0.72);
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.stone {
  position: absolute;
  inset: 10%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  will-change: transform, opacity;
  z-index: 3;

  &.black {
    background:
      radial-gradient(circle at 34% 28%, #686868 0, #1f1f1f 34%, #050505 72%);
    box-shadow: 0 2px 5px rgba(0, 0, 0, 0.36);
  }

  &.white {
    background:
      radial-gradient(circle at 34% 28%, #ffffff 0, #f3f4f6 44%, #d1d5db 100%);
    box-shadow:
      0 2px 5px rgba(0, 0, 0, 0.24),
      inset 0 0 0 1px rgba(75, 85, 99, 0.18);
  }

  &.pending-stone {
    opacity: 0.58;
    transform: scale(0.92);
    pointer-events: none;
  }

  &.ghost-stone {
    z-index: 4;
    pointer-events: none;
  }
}

.last-dot {
  width: 28%;
  height: 28%;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.45);
}

.winning-ring {
  position: absolute;
  inset: -10%;
  border: 2px solid #c2410c;
  border-radius: 50%;
  pointer-events: none;
}

.analysis-marker {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 5;
  width: 17px;
  height: 17px;
  padding: 0;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  transform: translate(-50%, -50%);
  pointer-events: none;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.22);

  &.type-winning-move {
    background: #dc2626;
  }

  &.type-block {
    background: #2563eb;
  }

  &.type-better-move {
    background: #0f766e;
  }

  &.type-commentary {
    background: #7c3aed;
  }
}

.analysis-ping {
  position: absolute;
  inset: -4px;
  border: 1px solid currentColor;
  border-radius: inherit;
  opacity: 0.5;
}

.analysis-label {
  position: relative;
  z-index: 1;
  white-space: nowrap;
  line-height: 1;
}

@media (max-width: 360px) {
  .board-grid {
    padding: 8px;
  }

  .stone {
    inset: 8%;
  }

  .analysis-marker {
    width: 14px;
    height: 14px;
    font-size: 9px;
  }
}
</style>
