<template>
  <div class="mj-table">
    <!-- 背景桌面 -->
    <div class="table-surface">
      <!-- 中央信息区 -->
      <div class="center-info">
        <div class="round-info">
          <span class="round-number">第{{ currentRound }}局</span>
          <span class="remaining">剩{{ wallRemaining }}张</span>
        </div>
        <div v-if="wildTile" class="wild-info">
          <span>百搭:</span>
          <MahjongTile :code="wildTile" size="small" />
        </div>
        <div class="current-turn" :class="{ active: currentTurn === mySeat }">
          {{ getTurnText() }}
        </div>
      </div>

      <!-- 弃牌区域 -->
      <div class="discard-zones">
        <MahjongDiscardArea
          class="discard-bottom"
          position="bottom"
          :tiles="player1Discards"
          :is-last="lastActionSeat === 1"
        />
        <MahjongDiscardArea
          class="discard-right"
          position="right"
          :tiles="player2Discards"
          :is-last="lastActionSeat === 2"
        />
        <MahjongDiscardArea
          class="discard-top"
          position="top"
          :tiles="player3Discards"
          :is-last="lastActionSeat === 3"
        />
        <MahjongDiscardArea
          class="discard-left"
          position="left"
          :tiles="player4Discards"
          :is-last="lastActionSeat === 4"
        />
      </div>

      <!-- 玩家手牌区域 -->
      <!-- 底部（自己）-->
      <div class="player-zone player-bottom">
        <MahjongHand
          ref="myHandRef"
          position="bottom"
          :tiles="myHand"
          :melds="player1Melds"
          :flowers="player1Flowers"
          :name="player1Name"
          :avatar="player1Avatar"
          :score="player1Score"
          :is-me="true"
          :is-my-turn="currentTurn === 1 && mySeat === 1"
          :is-dealer="dealerSeat === 1"
          :can-select="currentTurn === mySeat && availableActions.includes('DISCARD')"
          @select-tile="onSelectTile"
        />
      </div>

      <!-- 右边玩家 -->
      <div class="player-zone player-right" v-if="playerCount >= 2">
        <MahjongHand
          position="right"
          :hand-count="player2HandCount"
          :melds="player2Melds"
          :flowers="player2Flowers"
          :name="player2Name"
          :avatar="player2Avatar"
          :score="player2Score"
          :is-my-turn="currentTurn === 2"
          :is-dealer="dealerSeat === 2"
        />
      </div>

      <!-- 顶部玩家 -->
      <div class="player-zone player-top" v-if="playerCount >= 3">
        <MahjongHand
          position="top"
          :hand-count="player3HandCount"
          :melds="player3Melds"
          :flowers="player3Flowers"
          :name="player3Name"
          :avatar="player3Avatar"
          :score="player3Score"
          :is-my-turn="currentTurn === 3"
          :is-dealer="dealerSeat === 3"
        />
      </div>

      <!-- 左边玩家 -->
      <div class="player-zone player-left" v-if="playerCount >= 4">
        <MahjongHand
          position="left"
          :hand-count="player4HandCount"
          :melds="player4Melds"
          :flowers="player4Flowers"
          :name="player4Name"
          :avatar="player4Avatar"
          :score="player4Score"
          :is-my-turn="currentTurn === 4"
          :is-dealer="dealerSeat === 4"
        />
      </div>
    </div>

    <!-- 操作栏 -->
    <MahjongActionBar
      :available-actions="availableActions"
      :selected-tile="selectedTile"
      :chi-options="chiOptions"
      :an-kong-options="anKongOptions"
      :bu-kong-options="buKongOptions"
      :last-discard-tile="lastDiscardTile"
      @pass="$emit('pass')"
      @chi="(chiTiles) => $emit('chi', chiTiles)"
      @pong="$emit('pong')"
      @kong="(tile, type) => $emit('kong', tile, type)"
      @hu="$emit('hu')"
      @discard="onDiscard"
    />

    <!-- 结算弹窗 -->
    <van-popup
      :show="showResult"
      round
      position="center"
      :close-on-click-overlay="false"
      @update:show="$emit('update:showResult', $event)"
    >
      <div class="result-content">
        <h3>{{ resultTitle }}</h3>
        <div class="result-details">
          <div v-for="(change, seat) in scoreChanges" :key="seat" class="score-item">
            <span class="player-name">{{ getPlayerName(Number(seat)) }}</span>
            <span :class="['score-change', change > 0 ? 'positive' : 'negative']">
              {{ change > 0 ? '+' : '' }}{{ change }}
            </span>
          </div>
        </div>
        <van-button type="primary" block @click="$emit('nextRound')">
          {{ isGameOver ? '查看战绩' : '下一局' }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import MahjongTile from './MahjongTile.vue'
import MahjongHand from './MahjongHand.vue'
import MahjongDiscardArea from './MahjongDiscardArea.vue'
import MahjongActionBar from './MahjongActionBar.vue'

interface Meld {
  type: 'CHI' | 'PONG' | 'MING_KONG' | 'AN_KONG' | 'BU_KONG'
  tiles: string[]
  concealed?: boolean
}

interface Props {
  // 游戏信息
  currentRound?: number
  totalRounds?: number
  wallRemaining?: number
  wildTile?: string
  dealerSeat?: number
  currentTurn?: number
  mySeat?: number
  playerCount?: number

  // 我的手牌
  myHand?: string[]

  // 玩家1（底部/自己）
  player1Name?: string
  player1Avatar?: string
  player1Score?: number
  player1Melds?: Meld[]
  player1Flowers?: string[]
  player1Discards?: string[]

  // 玩家2（右边）
  player2Name?: string
  player2Avatar?: string
  player2Score?: number
  player2HandCount?: number
  player2Melds?: Meld[]
  player2Flowers?: string[]
  player2Discards?: string[]

  // 玩家3（顶部）
  player3Name?: string
  player3Avatar?: string
  player3Score?: number
  player3HandCount?: number
  player3Melds?: Meld[]
  player3Flowers?: string[]
  player3Discards?: string[]

  // 玩家4（左边）
  player4Name?: string
  player4Avatar?: string
  player4Score?: number
  player4HandCount?: number
  player4Melds?: Meld[]
  player4Flowers?: string[]
  player4Discards?: string[]

  // 操作相关
  availableActions?: string[]
  chiOptions?: string[][]
  anKongOptions?: string[]
  buKongOptions?: string[]
  lastDiscardTile?: string
  lastActionSeat?: number

  // 结算
  showResult?: boolean
  resultTitle?: string
  scoreChanges?: Record<string, number>
  isGameOver?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  currentRound: 1,
  totalRounds: 8,
  wallRemaining: 0,
  wildTile: '',
  dealerSeat: 1,
  currentTurn: 1,
  mySeat: 1,
  playerCount: 4,
  myHand: () => [],
  player1Name: '',
  player1Avatar: '',
  player1Score: 0,
  player1Melds: () => [],
  player1Flowers: () => [],
  player1Discards: () => [],
  player2Name: '',
  player2Avatar: '',
  player2Score: 0,
  player2HandCount: 0,
  player2Melds: () => [],
  player2Flowers: () => [],
  player2Discards: () => [],
  player3Name: '',
  player3Avatar: '',
  player3Score: 0,
  player3HandCount: 0,
  player3Melds: () => [],
  player3Flowers: () => [],
  player3Discards: () => [],
  player4Name: '',
  player4Avatar: '',
  player4Score: 0,
  player4HandCount: 0,
  player4Melds: () => [],
  player4Flowers: () => [],
  player4Discards: () => [],
  availableActions: () => [],
  chiOptions: () => [],
  anKongOptions: () => [],
  buKongOptions: () => [],
  lastDiscardTile: '',
  lastActionSeat: 0,
  showResult: false,
  resultTitle: '',
  scoreChanges: () => ({}),
  isGameOver: false
})

const emit = defineEmits<{
  pass: []
  chi: [chiTiles: string[]]
  pong: []
  kong: [tile: string, type: string]
  hu: []
  discard: [tile: string]
  nextRound: []
  'update:showResult': [value: boolean]
}>()

const myHandRef = ref()
const selectedTile = ref<string | null>(null)

// 获取当前回合文本
function getTurnText() {
  if (props.currentTurn === props.mySeat) {
    return '轮到你了'
  }
  return `${getPlayerName(props.currentTurn)} 思考中...`
}

// 获取玩家名称
function getPlayerName(seat: number): string {
  switch (seat) {
    case 1: return props.player1Name || '玩家1'
    case 2: return props.player2Name || '玩家2'
    case 3: return props.player3Name || '玩家3'
    case 4: return props.player4Name || '玩家4'
    default: return '未知'
  }
}

// 选择牌
function onSelectTile(tile: string, index: number) {
  selectedTile.value = tile
}

// 出牌
function onDiscard(tile: string) {
  emit('discard', tile)
  selectedTile.value = null
  myHandRef.value?.clearSelection()
}
</script>

<style scoped>
.mj-table {
  width: 100%;
  height: 100vh;
  background: #1a472a;
  position: relative;
  overflow: hidden;
}

.table-surface {
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at center, #2d5a3d 0%, #1a472a 100%),
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 50px,
      rgba(0, 0, 0, 0.03) 50px,
      rgba(0, 0, 0, 0.03) 51px
    ),
    repeating-linear-gradient(
      90deg,
      transparent,
      transparent 50px,
      rgba(0, 0, 0, 0.03) 50px,
      rgba(0, 0, 0, 0.03) 51px
    );
  position: relative;
}

/* 中央信息区 */
.center-info {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 12px;
  z-index: 10;
}

.round-info {
  display: flex;
  gap: 12px;
  color: #fff;
  font-size: 14px;
}

.round-number {
  color: #ffd93d;
  font-weight: bold;
}

.remaining {
  color: #aaa;
}

.wild-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #ff6b6b;
  font-size: 12px;
}

.current-turn {
  color: #aaa;
  font-size: 13px;
}

.current-turn.active {
  color: #4cd137;
  font-weight: bold;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

/* 弃牌区域 */
.discard-zones {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 300px;
  height: 300px;
}

.discard-bottom {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
}

.discard-top {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
}

.discard-left {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
}

.discard-right {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
}

/* 玩家区域 */
.player-zone {
  position: absolute;
}

.player-bottom {
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  padding: 8px;
}

.player-top {
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  padding: 8px;
}

.player-left {
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  padding: 8px;
}

.player-right {
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  padding: 8px;
}

/* 结算弹窗 */
.result-content {
  padding: 24px;
  min-width: 280px;
}

.result-content h3 {
  text-align: center;
  margin-bottom: 16px;
  color: #333;
}

.result-details {
  margin-bottom: 20px;
}

.score-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #eee;
}

.score-change.positive {
  color: #4cd137;
  font-weight: bold;
}

.score-change.negative {
  color: #ff4757;
  font-weight: bold;
}
</style>
