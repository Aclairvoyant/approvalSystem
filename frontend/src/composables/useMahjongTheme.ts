import { computed, reactive } from 'vue'

/** 桌布：呢面三段渐变、底部手牌区、中间面板的底色 */
export interface FeltTheme {
  key: string
  name: string
  felt: [string, string, string]
  dock: [string, string]
  panel: string
}

/** 牌背：亮面、本色、暗面（牌面底边的厚度也用这个颜色） */
export interface BackTheme {
  key: string
  name: string
  colors: [string, string, string]
}

export const FELT_THEMES: FeltTheme[] = [
  { key: 'green', name: '翡翠绿', felt: ['#2c7b58', '#1c5c41', '#0d3a29'], dock: ['#123f2e', '#0b2c20'], panel: 'rgba(5, 24, 17, 0.78)' },
  { key: 'blue', name: '海湾蓝', felt: ['#2f72ad', '#1d5185', '#0b2747'], dock: ['#123a63', '#0a2240'], panel: 'rgba(4, 16, 34, 0.78)' },
  { key: 'red', name: '胭脂红', felt: ['#9b3644', '#6f2331', '#3a0f18'], dock: ['#5a1c28', '#360e17'], panel: 'rgba(30, 6, 12, 0.78)' },
  { key: 'purple', name: '暮光紫', felt: ['#6852ab', '#47387d', '#231a45'], dock: ['#3a2d66', '#211940'], panel: 'rgba(16, 10, 34, 0.78)' },
  { key: 'wood', name: '檀木棕', felt: ['#8f6c40', '#664a28', '#35240f'], dock: ['#4f3820', '#2e1f0f'], panel: 'rgba(28, 18, 6, 0.78)' },
  { key: 'night', name: '夜幕黑', felt: ['#3d424a', '#272b32', '#111317'], dock: ['#22262c', '#121418'], panel: 'rgba(6, 8, 10, 0.82)' }
]

export const BACK_THEMES: BackTheme[] = [
  { key: 'jade', name: '翠绿', colors: ['#55b98d', '#2f8c65', '#1f6248'] },
  { key: 'sapphire', name: '宝蓝', colors: ['#62a2ea', '#2f6fc0', '#1d4a87'] },
  { key: 'amber', name: '橙金', colors: ['#f3b862', '#d48a26', '#985b0f'] },
  { key: 'crimson', name: '朱红', colors: ['#ea7b72', '#c0392b', '#862417'] }
]

const STORAGE_KEY = 'mahjong.tableTheme'

function loadSaved(): { felt: string; back: string } {
  const fallback = { felt: FELT_THEMES[0].key, back: BACK_THEMES[0].key }
  if (typeof localStorage === 'undefined') return fallback
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    return {
      felt: FELT_THEMES.some(item => item.key === saved.felt) ? saved.felt : fallback.felt,
      back: BACK_THEMES.some(item => item.key === saved.back) ? saved.back : fallback.back
    }
  } catch {
    return fallback
  }
}

// 全局一份：几个页面（房间、预览）看到的是同一套主题
const state = reactive(loadSaved())

function persist(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ felt: state.felt, back: state.back }))
  } catch {
    // 隐私模式下存不了就算了，本次打开仍然生效
  }
}

/** 牌桌外观：桌布颜色和牌背颜色，选择保存在本机 */
export function useMahjongTheme() {
  const felt = computed(() => FELT_THEMES.find(item => item.key === state.felt) ?? FELT_THEMES[0])
  const back = computed(() => BACK_THEMES.find(item => item.key === state.back) ?? BACK_THEMES[0])

  /** 挂在房间根元素上的 CSS 变量，牌桌、牌面、弹层都从这里取色 */
  const cssVars = computed<Record<string, string>>(() => ({
    '--felt-1': felt.value.felt[0],
    '--felt-2': felt.value.felt[1],
    '--felt-3': felt.value.felt[2],
    '--mj-dock-1': felt.value.dock[0],
    '--mj-dock-2': felt.value.dock[1],
    '--mj-panel': felt.value.panel,
    '--mj-back-light': back.value.colors[0],
    '--mj-back': back.value.colors[1],
    '--mj-back-dark': back.value.colors[2]
  }))

  function setFelt(key: string): void {
    if (!FELT_THEMES.some(item => item.key === key)) return
    state.felt = key
    persist()
  }

  function setBack(key: string): void {
    if (!BACK_THEMES.some(item => item.key === key)) return
    state.back = key
    persist()
  }

  return {
    feltKey: computed(() => state.felt),
    backKey: computed(() => state.back),
    felt,
    back,
    cssVars,
    setFelt,
    setBack
  }
}
