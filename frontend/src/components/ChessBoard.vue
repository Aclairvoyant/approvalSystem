<template>
  <div class="chess-board">
    <!-- 基地和终点 -->
    <div class="bases-row">
      <div class="base base-1">
        <div class="base-title">玩家 1</div>
        <div class="base-slots">
          <div v-for="(pos, idx) in p1" :key="`base1-${idx}`" class="base-slot">
            <button
              v-if="pos === 0"
              type="button"
              class="piece piece-1"
              :class="{ 'can-move': canMovePiece(1, idx) }"
              :disabled="!canMovePiece(1, idx)"
              @click="handlePieceClick(1, idx)"
            >
              {{ idx + 1 }}
            </button>
          </div>
        </div>
      </div>

      <div class="destination">
        <div class="base-title">终点</div>
        <div class="destination-pieces">
          <template v-for="(pos, idx) in p1" :key="`dest1-${idx}`">
            <span v-if="pos === DESTINATION" class="piece piece-1 at-dest">{{ idx + 1 }}</span>
          </template>
          <template v-for="(pos, idx) in p2" :key="`dest2-${idx}`">
            <span v-if="pos === DESTINATION" class="piece piece-2 at-dest">{{ idx + 1 }}</span>
          </template>
        </div>
      </div>

      <div class="base base-2">
        <div class="base-title">玩家 2</div>
        <div class="base-slots">
          <div v-for="(pos, idx) in p2" :key="`base2-${idx}`" class="base-slot">
            <button
              v-if="pos === 0"
              type="button"
              class="piece piece-2"
              :class="{ 'can-move': canMovePiece(2, idx) }"
              :disabled="!canMovePiece(2, idx)"
              @click="handlePieceClick(2, idx)"
            >
              {{ idx + 1 }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 棋盘：蛇形路径，奇数行从右往左 -->
    <div class="board-grid">
      <div v-for="row in gridRows" :key="row.rowIndex" class="board-row" :class="{ 'is-reversed': row.reverse }">
        <div
          v-for="cell in row.cells"
          :key="cell.position"
          class="cell"
          :class="{ 'task-cell': cell.isTask, 'has-pieces': getPiecesAtPosition(cell.position).length > 0 }"
        >
          <div class="cell-number">{{ cell.position }}</div>
          <div v-if="cell.isTask" class="task-label">
            <span class="task-icon">★</span>
            <span class="task-text">{{ cell.taskTitle || '任务' }}</span>
          </div>

          <div class="cell-pieces">
            <button
              v-for="piece in getPiecesAtPosition(cell.position)"
              :key="`${piece.player}-${piece.index}`"
              type="button"
              class="piece"
              :class="[`piece-${piece.player}`, { 'can-move': canMovePiece(piece.player, piece.index) }]"
              :disabled="!canMovePiece(piece.player, piece.index)"
              @click="handlePieceClick(piece.player, piece.index)"
            >
              {{ piece.index + 1 }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

// 任务位置信息接口
interface TaskPositionInfo {
  position: number
  taskId: number | null
  title: string
}

interface Props {
  player1Pieces: number[]
  player2Pieces: number[]
  currentPlayer: number
  myPlayerNumber: number
  diceResult: number | null
  isMyTurn: boolean
  taskPositions?: number[]  // 自定义任务位置
  taskInfos?: TaskPositionInfo[]  // 任务详细信息（包含标题）
}

const props = withDefaults(defineProps<Props>(), {
  player1Pieces: () => [0, 0],
  player2Pieces: () => [0, 0],
  currentPlayer: 1,
  myPlayerNumber: 1,
  diceResult: null,
  isMyTurn: false,
  taskPositions: () => [5, 10, 15, 20, 25, 30, 35, 40, 45, 50],
  taskInfos: () => []
})

const emit = defineEmits<{
  (e: 'piece-click', player: number, pieceIndex: number): void
}>()

// 配置
const COLS = 7
const TOTAL_CELLS = 52
const DESTINATION = 100
// 与后端 FlightChessEngineImpl.PIECES_PER_PLAYER 保持一致；旧对局数据里多出来的两枚棋子永远不会动，不显示
const PIECES_PER_PLAYER = 2

const p1 = computed(() => props.player1Pieces.slice(0, PIECES_PER_PLAYER))
const p2 = computed(() => props.player2Pieces.slice(0, PIECES_PER_PLAYER))

// 计算网格行
const gridRows = computed(() => {
  const rows: Array<{
    rowIndex: number
    reverse: boolean
    cells: Array<{
      position: number
      isTask: boolean
      taskTitle: string | null
    }>
  }> = []

  let cellIndex = 1
  let rowIndex = 0

  while (cellIndex <= TOTAL_CELLS) {
    const cellsInRow = Math.min(COLS, TOTAL_CELLS - cellIndex + 1)
    const reverse = rowIndex % 2 === 1  // 奇数行反向（蛇形路径）

    const cells = []
    for (let i = 0; i < cellsInRow; i++) {
      const pos = cellIndex + i
      const isTask = props.taskPositions.includes(pos)
      // 优先从 taskInfos 获取任务标题
      const taskInfo = props.taskInfos.find(t => t.position === pos)
      const taskTitle = taskInfo?.title || null

      cells.push({
        position: pos,
        isTask,
        taskTitle
      })
    }

    // 只在数据里反转一次，CSS 不再 row-reverse，否则两次反转会抵消掉蛇形
    rows.push({
      rowIndex,
      reverse,
      cells: reverse ? cells.reverse() : cells
    })

    cellIndex += cellsInRow
    rowIndex++
  }

  return rows
})

// 获取某位置的所有棋子
function getPiecesAtPosition(position: number): Array<{ player: number; index: number }> {
  const pieces: Array<{ player: number; index: number }> = []

  p1.value.forEach((pos, idx) => {
    if (pos === position) pieces.push({ player: 1, index: idx })
  })
  p2.value.forEach((pos, idx) => {
    if (pos === position) pieces.push({ player: 2, index: idx })
  })

  return pieces
}

// 判断棋子是否可以移动
function canMovePiece(player: number, pieceIndex: number): boolean {
  if (!props.isMyTurn || player !== props.myPlayerNumber || props.diceResult === null) {
    return false
  }

  const pieces = player === 1 ? props.player1Pieces : props.player2Pieces
  const currentPosition = pieces[pieceIndex]

  // 在基地必须掷到5或6才能出
  if (currentPosition === 0) {
    return props.diceResult === 5 || props.diceResult === 6
  }

  // 已到终点不能移动
  if (currentPosition === DESTINATION) {
    return false
  }

  // 检查是否会超出太多
  const newPos = currentPosition + props.diceResult
  if (newPos > TOTAL_CELLS + 6) {
    return false
  }

  return true
}

// 处理棋子点击
function handlePieceClick(player: number, pieceIndex: number) {
  if (!canMovePiece(player, pieceIndex)) {
    return
  }
  emit('piece-click', player, pieceIndex)
}
</script>

<style scoped>
.chess-board {
  --p1: #e5534b;
  --p2: #3b7dd8;
  --gap: 4px;

  width: 100%;
}

.bases-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.base,
.destination {
  flex: 1;
  padding: 8px 4px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  text-align: center;
}

.base-1 {
  border-color: rgba(229, 83, 75, 0.45);
  background: #fdf1f1;
}

.base-2 {
  border-color: rgba(59, 125, 216, 0.45);
  background: #eff5fc;
}

.destination {
  background: var(--ok-soft);
}

.base-title {
  margin-bottom: 6px;
  color: var(--ink-2);
  font-size: 12px;
  font-weight: 600;
}

.base-slots,
.destination-pieces {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  min-height: 28px;
}

.base-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px dashed var(--rule-strong);
  border-radius: 50%;
}

.board-grid {
  display: flex;
  flex-direction: column;
  gap: var(--gap);
}

.board-row {
  display: flex;
  gap: var(--gap);
}

/* 最后一行不满 7 格时保持和其他行同样的格子大小，从路径方向的起点排起 */
.board-row.is-reversed {
  justify-content: flex-end;
}

.cell {
  position: relative;
  display: flex;
  flex: 0 0 calc((100% - var(--gap) * (7 - 1)) / 7);
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 2px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  aspect-ratio: 1;
}

.cell.task-cell {
  border-color: #ecd48f;
  background: #fff8e1;
}

.cell.has-pieces {
  border-color: var(--rule-strong);
}

.cell-number {
  position: absolute;
  top: 1px;
  left: 3px;
  color: var(--ink-3);
  font-size: 10px;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.task-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 100%;
  margin-top: 8px;
}

.task-icon {
  color: #b7791f;
  font-size: 11px;
  line-height: 1;
}

.task-text {
  max-width: 100%;
  overflow: hidden;
  color: #8a5a00;
  font-size: 10px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-pieces {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 2px;
  margin-top: auto;
}

.piece {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 2px solid #fff;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  user-select: none;
  cursor: default;
}

.piece:disabled {
  opacity: 1;
}

.piece-1 {
  background: var(--p1);
}

.piece-2 {
  background: var(--p2);
}

/* 可移动的棋子：外圈描边 + 轻微跳动，提示可以点 */
.piece.can-move {
  outline: 2px solid var(--ink);
  outline-offset: 1px;
  cursor: pointer;
  animation: nudge 1.1s ease-in-out infinite;
}

.piece.at-dest {
  width: 20px;
  height: 20px;
  font-size: 10px;
}

@keyframes nudge {
  50% {
    transform: translateY(-2px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .piece.can-move {
    animation: none;
  }
}

@media (max-width: 360px) {
  .piece {
    width: 18px;
    height: 18px;
    font-size: 10px;
  }

  .task-text {
    display: none;
  }
}
</style>
