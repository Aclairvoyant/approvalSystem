<template>
  <div class="game-tasks">
    <van-nav-bar
      title="任务管理"
      left-arrow
      @click-left="router.back()"
    />

    <!-- Tab切换 -->
    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="预设任务" name="preset">
        <!-- 分类筛选 -->
        <div class="filter-section">
          <van-dropdown-menu>
            <van-dropdown-item v-model="categoryFilter" :options="categoryOptions" />
            <van-dropdown-item v-model="difficultyFilter" :options="difficultyOptions" />
          </van-dropdown-menu>
        </div>

        <!-- 预设任务列表 -->
        <div v-if="loadingPreset && presetTasks.length === 0" class="loading-state">
          <van-loading type="spinner" size="24px">加载中...</van-loading>
        </div>
        <van-pull-refresh v-else v-model="refreshingPreset" @refresh="onRefreshPreset">
          <div v-if="presetTasks.length === 0" class="empty-state">
            <van-empty description="暂无任务" />
          </div>
          <div v-else>
            <div v-for="task in presetTasks" :key="task.id" class="task-card">
              <div class="task-header">
                <van-tag :type="getCategoryType(task.category)" size="medium">
                  {{ getCategoryText(task.category) }}
                </van-tag>
                <div class="difficulty">
                  <van-icon v-for="i in task.difficulty" :key="i" name="star" color="#ffd700" />
                  <van-icon v-for="i in (3 - task.difficulty)" :key="`e-${i}`" name="star-o" color="#ddd" />
                </div>
              </div>
              <h3 class="task-title">{{ task.title }}</h3>
              <p class="task-desc">{{ task.description }}</p>
              <div class="task-footer">
                <span class="points">
                  <van-icon name="fire-o" color="#ff976a" />
                  {{ task.points }}积分
                </span>
                <span class="usage">已使用 {{ task.usageCount }} 次</span>
              </div>
            </div>
            <div v-if="!finishedPreset" class="load-more">
              <van-button size="small" plain @click="loadMorePreset" :loading="loadingPreset">
                加载更多
              </van-button>
            </div>
          </div>
        </van-pull-refresh>
      </van-tab>

      <van-tab title="我的任务" name="custom">
        <!-- 创建按钮 -->
        <div class="create-section">
          <van-button type="primary" icon="plus" block @click="showCreateDialog = true">
            创建自定义任务
          </van-button>
        </div>

        <!-- 自定义任务列表 -->
        <div v-if="loadingCustom && customTasks.length === 0" class="loading-state">
          <van-loading type="spinner" size="24px">加载中...</van-loading>
        </div>
        <van-pull-refresh v-else v-model="refreshingCustom" @refresh="onRefreshCustom">
          <div v-if="customTasks.length === 0" class="empty-state">
            <van-empty description="暂无自定义任务">
              <van-button type="primary" size="small" @click="showCreateDialog = true">
                创建任务
              </van-button>
            </van-empty>
          </div>
          <div v-else>
            <van-swipe-cell v-for="task in customTasks" :key="task.id">
              <div class="task-card">
                <div class="task-header">
                  <van-tag :type="getCategoryType(task.category)" size="medium">
                    {{ getCategoryText(task.category) }}
                  </van-tag>
                  <div class="difficulty">
                    <van-icon v-for="i in task.difficulty" :key="i" name="star" color="#ffd700" />
                    <van-icon v-for="i in (3 - task.difficulty)" :key="`e-${i}`" name="star-o" color="#ddd" />
                  </div>
                </div>
                <h3 class="task-title">{{ task.title }}</h3>
                <p class="task-desc">{{ task.description }}</p>
                <div class="task-footer">
                  <span class="points">
                    <van-icon name="fire-o" color="#ff976a" />
                    {{ task.points }}积分
                  </span>
                  <span class="usage">已使用 {{ task.usageCount }} 次</span>
                </div>
              </div>
              <template #right>
                <van-button square type="danger" text="删除" @click="handleDeleteTask(task.id)" />
              </template>
            </van-swipe-cell>
            <div v-if="!finishedCustom" class="load-more">
              <van-button size="small" plain @click="loadMoreCustom" :loading="loadingCustom">
                加载更多
              </van-button>
            </div>
          </div>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>

    <!-- 创建任务弹窗 -->
    <van-popup
      v-model:show="showCreateDialog"
      position="bottom"
      round
      :style="{ height: '80%' }"
    >
      <div class="create-form">
        <div class="form-header">
          <span class="title">创建自定义任务</span>
          <van-icon name="cross" size="20" @click="showCreateDialog = false" />
        </div>

        <van-form @submit="handleCreateTask">
          <van-cell-group inset>
            <van-field
              v-model="newTask.title"
              label="任务标题"
              placeholder="请输入任务标题"
              :rules="[{ required: true, message: '请输入任务标题' }]"
            />
            <van-field
              v-model="newTask.description"
              label="任务描述"
              type="textarea"
              placeholder="请输入任务描述"
              rows="3"
              autosize
            />
            <van-field
              v-model="newTask.requirement"
              label="完成要求"
              type="textarea"
              placeholder="请输入完成要求（可选）"
              rows="2"
              autosize
            />
            <van-field name="category" label="任务类别">
              <template #input>
                <van-radio-group v-model="newTask.category" direction="horizontal">
                  <van-radio name="romantic">浪漫</van-radio>
                  <van-radio name="fun">趣味</van-radio>
                  <van-radio name="challenge">挑战</van-radio>
                  <van-radio name="intimate">亲密</van-radio>
                </van-radio-group>
              </template>
            </van-field>
            <van-field name="difficulty" label="难度等级">
              <template #input>
                <van-rate v-model="newTask.difficulty" :count="3" />
              </template>
            </van-field>
            <van-field
              v-model.number="newTask.points"
              label="积分"
              type="number"
              placeholder="10"
            />
            <van-field
              v-model.number="newTask.timeLimit"
              label="时间限制(秒)"
              type="number"
              placeholder="不限时"
            />
          </van-cell-group>

          <div class="form-actions">
            <van-button block type="primary" native-type="submit">
              创建任务
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showDialog, showLoadingToast, closeToast } from 'vant'
import { gameTaskApi, type GameTask } from '@/services/api'

const router = useRouter()

// Tab状态
const activeTab = ref('preset')

// 筛选选项
const categoryFilter = ref('')
const difficultyFilter = ref<number | ''>('')

const categoryOptions = [
  { text: '全部类别', value: '' },
  { text: '浪漫', value: 'romantic' },
  { text: '趣味', value: 'fun' },
  { text: '挑战', value: 'challenge' },
  { text: '亲密', value: 'intimate' }
]

const difficultyOptions = [
  { text: '全部难度', value: '' },
  { text: '简单', value: 1 },
  { text: '中等', value: 2 },
  { text: '困难', value: 3 }
]

// 预设任务
const presetTasks = ref<GameTask[]>([])
const loadingPreset = ref(false)
const finishedPreset = ref(false)
const refreshingPreset = ref(false)
const presetPageNum = ref(1)

// 自定义任务
const customTasks = ref<GameTask[]>([])
const loadingCustom = ref(false)
const finishedCustom = ref(false)
const refreshingCustom = ref(false)
const customPageNum = ref(1)

// 创建任务
const showCreateDialog = ref(false)
const newTask = reactive({
  title: '',
  description: '',
  requirement: '',
  category: 'romantic',
  difficulty: 1,
  points: 10,
  timeLimit: null as number | null
})

// 方法
function getCategoryType(category: string): 'primary' | 'success' | 'warning' | 'danger' {
  switch (category) {
    case 'romantic': return 'danger'
    case 'fun': return 'success'
    case 'challenge': return 'warning'
    case 'intimate': return 'primary'
    default: return 'primary'
  }
}

function getCategoryText(category: string): string {
  switch (category) {
    case 'romantic': return '浪漫'
    case 'fun': return '趣味'
    case 'challenge': return '挑战'
    case 'intimate': return '亲密'
    default: return '其他'
  }
}

// 预设任务加载
async function loadMorePreset() {
  if (loadingPreset.value || finishedPreset.value) return

  loadingPreset.value = true
  try {
    const response: any = await gameTaskApi.getPresetTasks({
      category: categoryFilter.value || undefined,
      difficulty: difficultyFilter.value || undefined,
      pageNum: presetPageNum.value,
      pageSize: 10
    })

    const newTasks = response?.records || []

    if (presetPageNum.value === 1) {
      presetTasks.value = newTasks
    } else {
      presetTasks.value = [...presetTasks.value, ...newTasks]
    }

    if (newTasks.length < 10) {
      finishedPreset.value = true
    } else {
      presetPageNum.value++
    }
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加载失败' })
    finishedPreset.value = true
  } finally {
    loadingPreset.value = false
  }
}

async function onRefreshPreset() {
  presetPageNum.value = 1
  finishedPreset.value = false
  await loadMorePreset()
  refreshingPreset.value = false
}

// 自定义任务加载
async function loadMoreCustom() {
  if (loadingCustom.value || finishedCustom.value) return

  loadingCustom.value = true
  try {
    const response: any = await gameTaskApi.getUserCustomTasks(customPageNum.value, 10)

    const newTasks = response.records || []
    if (customPageNum.value === 1) {
      customTasks.value = newTasks
    } else {
      customTasks.value = [...customTasks.value, ...newTasks]
    }

    if (newTasks.length < 10) {
      finishedCustom.value = true
    } else {
      customPageNum.value++
    }
  } catch (error: any) {
    showToast({ type: 'fail', message: error.message || '加载失败' })
    finishedCustom.value = true  // 出错时也标记完成，避免无限加载
  } finally {
    loadingCustom.value = false
  }
}

async function onRefreshCustom() {
  customPageNum.value = 1
  finishedCustom.value = false
  await loadMoreCustom()
  refreshingCustom.value = false
}

// 创建任务
async function handleCreateTask() {
  const toast = showLoadingToast({ message: '创建中...', forbidClick: true })

  try {
    await gameTaskApi.createCustomTask({
      title: newTask.title,
      description: newTask.description,
      requirement: newTask.requirement,
      category: newTask.category,
      difficulty: newTask.difficulty,
      points: newTask.points || 10,
      timeLimit: newTask.timeLimit || undefined
    })

    closeToast()
    showToast({ type: 'success', message: '创建成功' })

    // 重置表单
    newTask.title = ''
    newTask.description = ''
    newTask.requirement = ''
    newTask.category = 'romantic'
    newTask.difficulty = 1
    newTask.points = 10
    newTask.timeLimit = null

    showCreateDialog.value = false

    // 刷新列表
    customPageNum.value = 1
    finishedCustom.value = false
    customTasks.value = []
    await loadMoreCustom()
  } catch (error: any) {
    closeToast()
    showToast({ type: 'fail', message: error.response?.data?.message || '创建失败' })
  }
}

// 删除任务
async function handleDeleteTask(taskId: number) {
  try {
    await showDialog({
      title: '提示',
      message: '确定要删除这个任务吗？',
      showCancelButton: true
    })

    await gameTaskApi.deleteCustomTask(taskId)
    showToast({ type: 'success', message: '删除成功' })

    // 从列表中移除
    customTasks.value = customTasks.value.filter(t => t.id !== taskId)
  } catch (error) {
    // 用户取消或删除失败
  }
}

// 监听筛选变化
watch([categoryFilter, difficultyFilter], () => {
  presetPageNum.value = 1
  finishedPreset.value = false
  presetTasks.value = []
  loadMorePreset()
})

// 监听Tab切换
watch(activeTab, (newTab) => {
  if (newTab === 'preset' && presetTasks.value.length === 0) {
    loadMorePreset()
  } else if (newTab === 'custom' && customTasks.value.length === 0) {
    loadMoreCustom()
  }
})

// 初始化加载
onMounted(() => {
  loadMorePreset()
})
</script>

<style scoped lang="scss">
.game-tasks {
  min-height: 100vh;
  background-color: #f7f8fa;
  padding-bottom: 60px;
}

.filter-section {
  background: white;
  margin-bottom: 12px;
}

.create-section {
  padding: 12px;
}

.empty-state {
  padding: 40px 0;
}

.loading-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 60px 0;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 16px;
}

.task-card {
  background: white;
  padding: 16px;
  margin: 0 12px 12px;
  border-radius: 12px;

  .task-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .difficulty {
      display: flex;
      gap: 2px;
    }
  }

  .task-title {
    font-size: 16px;
    color: #323233;
    margin-bottom: 8px;
  }

  .task-desc {
    font-size: 14px;
    color: #969799;
    margin-bottom: 12px;
    line-height: 1.5;
  }

  .task-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;

    .points {
      display: flex;
      align-items: center;
      gap: 4px;
      color: #ff976a;
    }

    .usage {
      color: #c8c9cc;
    }
  }
}

.create-form {
  height: 100%;
  display: flex;
  flex-direction: column;

  .form-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #ebedf0;

    .title {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .van-form {
    flex: 1;
    overflow-y: auto;
    padding: 12px 0;
  }

  .form-actions {
    padding: 12px 16px;
    border-top: 1px solid #ebedf0;
  }
}
</style>
