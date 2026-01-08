<template>
  <div class="chess-board">
    <!-- 基地区域 -->
    <div class="bases-row">
      <div class="base base-1">
        <div class="base-title">玩家1</div>
        <div class="base-slots">
          <div
            v-for="i in 2"
            :key="`base1-${i}`"
            class="base-slot"
            :class="{ 'has-piece': player1Pieces[i - 1] === 0 }"
          >
            <div
              v-if="player1Pieces[i - 1] === 0"
              class="piece piece-1"
              :class="{ 'can-move': canMovePiece(1, i - 1) }"
              @click="handlePieceClick(1, i - 1)"
            >
              {{ i }}
            </div>
          </div>
        </div>
      </div>

      <div class="destination">
        <div class="destination-title">终点</div>
        <div class="destination-pieces">
          <template v-for="(pos, idx) in player1Pieces" :key="`dest1-${idx}`">
            <div v-if="pos === 100" class="piece piece-1 at-dest">{{ idx + 1 }}</div>
          </template>
          <template v-for="(pos, idx) in player2Pieces" :key="`dest2-${idx}`">
            <div v-if="pos === 100" class="piece piece-2 at-dest">{{ idx + 1 }}</div>
          </template>
        </div>
      </div>

      <div class="base base-2">
        <div class="base-title">玩家2</div>
        <div class="base-slots">
          <div
            v-for="i in 2"
            :key="`base2-${i}`"
            class="base-slot"
            :class="{ 'has-piece': player2Pieces[i - 1] === 0 }"
          >
            <div
              v-if="player2Pieces[i - 1] === 0"
              class="piece piece-2"
              :class="{ 'can-move': canMovePiece(2, i - 1) }"
              @click="handlePieceClick(2, i - 1)"
            >
              {{ i }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 棋盘格子 -->
    <div class="board-grid">
      <div
        v-for="row in gridRows"
        :key="row.rowIndex"
        class="board-row"
        :class="{ 'reverse': row.reverse }"
      >
        <div
          v-for="cell in row.cells"
          :key="cell.position"
          class="cell"
          :class="{
            'task-cell': cell.isTask,
            'has-pieces': getPiecesAtPosition(cell.position).length > 0
          }"
        >
          <div class="cell-number">{{ cell.position }}</div>
          <div v-if="cell.isTask" class="task-label">
            <span class="task-icon">★</span>
            <span class="task-text">{{ cell.taskTitle || '任务' }}</span>
          </div>

          <!-- 显示在此格子上的棋子 -->
          <div class="cell-pieces">
            <div
              v-for="piece in getPiecesAtPosition(cell.position)"
              :key="`${piece.player}-${piece.index}`"
              class="piece"
              :class="[
                `piece-${piece.player}`,
                { 'can-move': canMovePiece(piece.player, piece.index) }
              ]"
              @click="handlePieceClick(piece.player, piece.index)"
            >
              {{ piece.index + 1 }}
            </div>
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

  props.player1Pieces.forEach((pos, idx) => {
    if (pos === position) pieces.push({ player: 1, index: idx })
  })
  props.player2Pieces.forEach((pos, idx) => {
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
  if (currentPosition === 100) {
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

<style scoped lang="scss">
.chess-board {
  width: 100%;
  max-width: 100%;
  padding: 8px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  border-radius: 12px;
}

.bases-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.base {
  flex: 1;
  padding: 8px;
  border-radius: 8px;
  text-align: center;

  &.base-1 {
    background: linear-gradient(135deg, #ffe4e1 0%, #ffcccb 100%);
    border: 2px solid #ff6b6b;
  }

  &.base-2 {
    background: linear-gradient(135deg, #e6f3ff 0%, #b8d4f0 100%);
    border: 2px solid #4a90d9;
  }

  .base-title {
    font-size: 12px;
    font-weight: bold;
    margin-bottom: 6px;
  }

  .base-slots {
    display: flex;
    justify-content: center;
    gap: 4px;
  }

  .base-slot {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.destination {
  flex: 1;
  padding: 8px;
  background: linear-gradient(135deg, #d4edda 0%, #90ee90 100%);
  border: 2px solid #28a745;
  border-radius: 8px;
  text-align: center;

  .destination-title {
    font-size: 12px;
    font-weight: bold;
    color: #155724;
    margin-bottom: 6px;
  }

  .destination-pieces {
    display: flex;
    justify-content: center;
    gap: 4px;
    min-height: 28px;
    flex-wrap: wrap;
  }
}

.board-grid {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.board-row {
  display: flex;
  gap: 4px;

  &.reverse {
    flex-direction: row-reverse;
  }
}

.cell {
  flex: 1;
  aspect-ratio: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 2px;
  transition: all 0.2s;

  &.task-cell {
    background: linear-gradient(135deg, #fff9e6 0%, #ffe066 100%);
    border-color: #ffc107;

    .cell-number {
      color: #856404;
    }
  }

  &.has-pieces {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }

  .cell-number {
    font-size: 8px;
    color: #999;
    position: absolute;
    top: 1px;
    left: 3px;
  }

  .task-label {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1px;
    margin-top: 6px;

    .task-icon {
      font-size: 10px;
      color: #ff8c00;
    }

    .task-text {
      font-size: 8px;
      color: #856404;
      font-weight: bold;
      text-align: center;
      line-height: 1;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .cell-pieces {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 2px;
    margin-top: auto;
  }
}

.piece {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  color: white;
  cursor: default;
  transition: all 0.2s;
  user-select: none;

  &.piece-1 {
    background: linear-gradient(135deg, #ff6b6b 0%, #ee5a5a 100%);
    border: 2px solid #cc0000;
  }

  &.piece-2 {
    background: linear-gradient(135deg, #4a90d9 0%, #3a7bc8 100%);
    border: 2px solid #1a5fb4;
  }

  &.can-move {
    cursor: pointer;
    animation: pulse 1s infinite;
    box-shadow: 0 0 10px currentColor;

    &.piece-1 {
      box-shadow: 0 0 10px #ff6b6b;
    }

    &.piece-2 {
      box-shadow: 0 0 10px #4a90d9;
    }
  }

  &.at-dest {
    width: 20px;
    height: 20px;
    font-size: 10px;
  }
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.15);
  }
}

// 响应式调整
@media (max-width: 360px) {
  .cell {
    .cell-number {
      font-size: 7px;
    }

    .task-label {
      .task-icon {
        font-size: 8px;
      }
      .task-text {
        font-size: 7px;
      }
    }
  }

  .piece {
    width: 20px;
    height: 20px;
    font-size: 10px;
  }

  .base .base-slot {
    width: 24px;
    height: 24px;
  }
}
</style>
