<template>
  <div class="game-tasks">
    <van-nav-bar title="飞行棋任务" left-arrow fixed placeholder @click-left="router.back()" />

    <van-tabs v-model:active="activeTab" sticky offset-top="46">
      <van-tab title="预设任务" name="preset">
        <van-dropdown-menu class="filters">
          <van-dropdown-item v-model="categoryFilter" :options="categoryOptions" @change="presetList.reset" />
          <van-dropdown-item v-model="difficultyFilter" :options="difficultyOptions" @change="presetList.reset" />
        </van-dropdown-menu>

        <van-pull-refresh v-model="presetList.refreshing.value" class="m-page" @refresh="presetList.refresh">
          <van-list
            v-model:loading="presetList.loading.value"
            v-model:error="presetList.error.value"
            :finished="presetList.finished.value"
            error-text="加载失败，点击重试"
            @load="presetList.load"
          >
            <van-empty
              v-if="presetList.finished.value && presetList.list.value.length === 0"
              class="m-empty"
              description="没有符合条件的任务"
            />
            <article v-for="task in presetList.list.value" :key="task.id" class="m-card task-card">
              <TaskSummary :task="task" />
            </article>
          </van-list>
        </van-pull-refresh>
      </van-tab>

      <van-tab title="我的任务" name="custom">
        <van-pull-refresh v-model="customList.refreshing.value" class="m-page" @refresh="customList.refresh">
          <van-button class="create-btn" type="primary" icon="plus" block @click="openCreate">新建任务</van-button>

          <van-list
            v-model:loading="customList.loading.value"
            v-model:error="customList.error.value"
            :finished="customList.finished.value"
            error-text="加载失败，点击重试"
            @load="customList.load"
          >
            <van-empty
              v-if="customList.finished.value && customList.list.value.length === 0"
              class="m-empty"
              description="还没有自己的任务，新建一个吧"
            />
            <van-swipe-cell v-for="task in customList.list.value" :key="task.id" class="m-card task-card">
              <TaskSummary :task="task" />
              <template #right>
                <van-button square type="danger" text="删除" class="m-swipe-btn" @click="handleDeleteTask(task)" />
              </template>
            </van-swipe-cell>
          </van-list>
        </van-pull-refresh>
      </van-tab>
    </van-tabs>

    <van-action-sheet v-model:show="showCreateDialog" title="新建任务" :closeable="true">
      <van-form class="m-sheet" @submit="handleCreateTask">
        <van-cell-group inset>
          <van-field
            v-model.trim="newTask.title"
            label="标题"
            placeholder="例如：给对方唱一首歌"
            maxlength="50"
            :rules="[{ required: true, message: '请输入标题' }]"
          />
          <van-field v-model="newTask.description" label="说明" type="textarea" rows="2" autosize maxlength="200" />
          <van-field v-model="newTask.requirement" label="完成标准" type="textarea" rows="2" autosize maxlength="200" placeholder="选填" />
          <van-field label="类别">
            <template #input>
              <van-radio-group v-model="newTask.category" direction="horizontal" class="radio-row">
                <van-radio v-for="option in categoryOptions.slice(1)" :key="option.value" :name="option.value">
                  {{ option.text }}
                </van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field label="难度">
            <template #input>
              <van-rate v-model="newTask.difficulty" :count="3" color="var(--ink)" void-color="var(--rule-strong)" />
            </template>
          </van-field>
          <van-field v-model.number="newTask.points" label="积分" type="digit" maxlength="3" placeholder="10" />
          <van-field v-model.number="newTask.timeLimit" label="限时（秒）" type="digit" maxlength="4" placeholder="不限时" />
        </van-cell-group>

        <div class="m-sheet-footer">
          <van-button block type="primary" native-type="submit" :loading="creating">创建</van-button>
        </div>
      </van-form>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { defineComponent, h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { gameTaskApi, type GameTask } from '@/services/api'
import { usePagedList } from '@/composables/usePagedList'

const router = useRouter()
const activeTab = ref('preset')

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

const categoryText = (category: string): string =>
  categoryOptions.find(option => option.value === category)?.text || '其他'

// 筛选变化时 reset() 会让旧请求作废，迟到的旧结果不会写进新列表
const presetList = usePagedList<GameTask>((pageNum, pageSize) =>
  gameTaskApi.getPresetTasks({
    category: categoryFilter.value || undefined,
    difficulty: difficultyFilter.value || undefined,
    pageNum,
    pageSize,
  })
)
const customList = usePagedList<GameTask>((pageNum, pageSize) => gameTaskApi.getUserCustomTasks(pageNum, pageSize))

/** 任务卡片内容；难度限定在 1–3，避免异常数据让星星数量为负 */
const TaskSummary = defineComponent({
  props: { task: { type: Object as () => GameTask, required: true } },
  setup(props) {
    return () => {
      const difficulty = Math.min(3, Math.max(1, Math.round(props.task.difficulty || 1)))
      return h('div', { class: 'task-summary' }, [
        h('div', { class: 'task-head' }, [
          h('span', { class: 'task-category' }, categoryText(props.task.category)),
          h('span', { class: 'task-difficulty', 'aria-label': `难度 ${difficulty}` }, [
            h('b', '★'.repeat(difficulty)),
            '★'.repeat(3 - difficulty),
          ]),
        ]),
        h('h3', { class: 'm-title task-title' }, props.task.title),
        props.task.description ? h('p', { class: 'task-desc' }, props.task.description) : null,
        h('div', { class: 'm-meta task-meta' }, [
          h('span', `${props.task.points} 积分`),
          props.task.timeLimit ? h('span', `限时 ${props.task.timeLimit} 秒`) : null,
          h('span', `用过 ${props.task.usageCount || 0} 次`),
        ]),
      ])
    }
  },
})

const showCreateDialog = ref(false)
const creating = ref(false)
const newTask = reactive({
  title: '',
  description: '',
  requirement: '',
  category: 'romantic',
  difficulty: 1,
  points: 10 as number | '',
  timeLimit: '' as number | '',
})

function openCreate(): void {
  Object.assign(newTask, {
    title: '',
    description: '',
    requirement: '',
    category: 'romantic',
    difficulty: 1,
    points: 10,
    timeLimit: '',
  })
  showCreateDialog.value = true
}

async function handleCreateTask(): Promise<void> {
  creating.value = true
  try {
    await gameTaskApi.createCustomTask({
      title: newTask.title,
      description: newTask.description.trim(),
      requirement: newTask.requirement.trim(),
      category: newTask.category,
      difficulty: newTask.difficulty,
      points: Number(newTask.points) || 10,
      timeLimit: Number(newTask.timeLimit) || undefined,
    })
    showSuccessToast('已创建')
    showCreateDialog.value = false
    activeTab.value = 'custom'
    customList.reset()
  } catch (error: any) {
    showFailToast(error.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function handleDeleteTask(task: GameTask): Promise<void> {
  try {
    await showConfirmDialog({ title: '删除任务', message: `确定删除"${task.title}"吗？` })
  } catch {
    return
  }
  try {
    await gameTaskApi.deleteCustomTask(task.id)
    showSuccessToast('已删除')
    customList.refresh()
  } catch (error: any) {
    showFailToast(error.message || '删除失败')
  }
}
</script>

<style scoped>
.game-tasks {
  min-height: 100%;
  background: var(--bg);
}

.filters :deep(.van-dropdown-menu__bar) {
  box-shadow: none;
  border-bottom: 1px solid var(--rule);
}

.create-btn {
  margin-bottom: 10px;
}

.task-card {
  overflow: hidden;
}

.task-card + .task-card {
  margin-top: 8px;
}

.task-card :deep(.task-summary) {
  padding: 14px 16px;
}

.task-card :deep(.task-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-3);
  font-size: 12px;
}

.task-card :deep(.task-category) {
  color: var(--ink-2);
  font-weight: 500;
}

.task-card :deep(.task-difficulty) {
  color: var(--rule-strong);
  letter-spacing: 1px;
}

.task-card :deep(.task-difficulty b) {
  color: var(--ink);
  font-weight: 400;
}

.task-card :deep(.task-title) {
  margin-top: 6px;
}

.task-card :deep(.task-desc) {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.55;
}

.task-card :deep(.task-meta) {
  margin-top: 8px;
}

.radio-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}
</style>
