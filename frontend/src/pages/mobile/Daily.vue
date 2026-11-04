<template>
  <div class="daily-page">
    <van-nav-bar title="日常事项" fixed placeholder>
      <template #right>
        <van-icon name="plus" size="20" aria-label="添加事项" @click="openCreate" />
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="activeStatus" sticky offset-top="46" @change="loadItems">
      <van-tab title="待完成" :name="1" />
      <van-tab title="已完成" :name="2" />
      <van-tab title="全部" :name="0" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" class="m-page" @refresh="loadItems">
      <van-empty v-if="!loading && items.length === 0" class="m-empty" description="还没有记录">
        <van-button type="primary" size="small" @click="openCreate">添加一件事</van-button>
      </van-empty>

      <div v-else class="item-list">
        <van-swipe-cell v-for="item in items" :key="item.id" class="m-card">
          <div class="item-row" :class="{ 'is-done': item.status !== 1 }">
            <button
              type="button"
              class="check"
              :disabled="item.status !== 1"
              :aria-label="item.status === 1 ? '标记完成' : '已完成'"
              @click="completeItem(item)"
            >
              <van-icon :name="item.status === 1 ? 'circle' : 'checked'" />
            </button>
            <div class="item-main">
              <div class="item-title">
                <span>{{ item.title }}</span>
                <span v-if="item.priority === 2" class="flag">重要</span>
              </div>
              <p v-if="item.content" class="item-content">{{ item.content }}</p>
              <div class="m-meta">
                <span>{{ dailyTypeText(item.itemType) }}</span>
                <span v-if="item.targetDate" :class="{ overdue: isOverdue(item) }">
                  {{ isOverdue(item) ? `已逾期 · ${item.targetDate}` : item.targetDate }}
                </span>
              </div>
            </div>
          </div>
          <template #right>
            <van-button square type="primary" text="编辑" class="m-swipe-btn" @click="openEdit(item)" />
            <van-button square text="归档" class="m-swipe-btn" @click="archiveItem(item)" />
            <van-button square type="danger" text="删除" class="m-swipe-btn" @click="deleteItem(item)" />
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <van-action-sheet v-model:show="showEditor" :title="editingId ? '编辑事项' : '添加事项'">
      <van-form class="m-sheet" @submit="saveItem">
        <van-cell-group inset>
          <van-field v-model="form.title" label="标题" placeholder="例如：周末买菜" maxlength="100" required />
          <van-field v-model="form.content" label="说明" type="textarea" rows="2" maxlength="500" placeholder="补充说明" />
          <van-field v-model="form.targetDate" label="日期" type="date" />
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.itemType" direction="horizontal" class="radio-row">
                <van-radio v-for="option in DAILY_TYPE_OPTIONS" :key="option.value" :name="option.value">
                  {{ option.text }}
                </van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field label="优先级">
            <template #input>
              <van-radio-group v-model="form.priority" direction="horizontal">
                <van-radio :name="1">普通</van-radio>
                <van-radio :name="2">重要</van-radio>
              </van-radio-group>
            </template>
          </van-field>
        </van-cell-group>
        <div class="m-sheet-footer">
          <van-button block type="primary" native-type="submit" :loading="saving">保存</van-button>
        </div>
      </van-form>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { dailyAPI, type DailyItem } from '@/services/api'
import { DAILY_TYPE_OPTIONS, dailyTypeText } from '@/utils/status'
import { todayValue } from '@/utils/format'

const items = ref<DailyItem[]>([])
const loading = ref(false)
const refreshing = ref(false)
const saving = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const activeStatus = ref(1)
let requestId = 0

const form = reactive({
  title: '',
  content: '',
  targetDate: '',
  itemType: 1,
  priority: 1,
})

onMounted(() => {
  loadItems()
})

async function loadItems() {
  // 快速切换标签时，只采用最后一次请求的结果
  const id = ++requestId
  loading.value = true
  try {
    const status = activeStatus.value === 0 ? undefined : activeStatus.value
    const result = await dailyAPI.list({ status })
    if (id === requestId) items.value = result
  } catch (error: any) {
    if (id === requestId) showFailToast(error.message || '加载失败')
  } finally {
    if (id === requestId) {
      loading.value = false
      refreshing.value = false
    }
  }
}

function isOverdue(item: DailyItem) {
  return item.status === 1 && !!item.targetDate && item.targetDate < todayValue()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', content: '', targetDate: '', itemType: 1, priority: 1 })
  showEditor.value = true
}

function openEdit(item: DailyItem) {
  editingId.value = item.id
  Object.assign(form, {
    title: item.title,
    content: item.content || '',
    targetDate: item.targetDate || '',
    itemType: item.itemType || 1,
    priority: item.priority || 1,
  })
  showEditor.value = true
}

async function saveItem() {
  if (!form.title.trim()) {
    showFailToast('请输入标题')
    return
  }

  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      targetDate: form.targetDate || undefined,
      itemType: form.itemType,
      priority: form.priority,
    }
    if (editingId.value) {
      await dailyAPI.update(editingId.value, payload)
    } else {
      await dailyAPI.create(payload)
    }
    showEditor.value = false
    showSuccessToast('已保存')
    loadItems()
  } catch (error: any) {
    showFailToast(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function runAction(action: () => Promise<unknown>, successText: string, failText: string) {
  try {
    await action()
    showSuccessToast(successText)
    loadItems()
  } catch (error: any) {
    showFailToast(error.message || failText)
  }
}

function completeItem(item: DailyItem) {
  return runAction(() => dailyAPI.complete(item.id), '已完成', '操作失败')
}

function archiveItem(item: DailyItem) {
  return runAction(() => dailyAPI.archive(item.id), '已归档', '归档失败')
}

async function deleteItem(item: DailyItem) {
  try {
    await showConfirmDialog({ title: '删除事项', message: `确定删除"${item.title}"吗？` })
  } catch {
    return
  }
  await runAction(() => dailyAPI.delete(item.id), '已删除', '删除失败')
}
</script>

<style scoped>
.daily-page {
  min-height: 100%;
  background: var(--bg);
}

.item-list .m-card + .m-card {
  margin-top: 8px;
}

.item-list :deep(.van-swipe-cell) {
  overflow: hidden;
}

.item-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px 14px 10px;
}

.check {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: 20px;
  cursor: pointer;
}

.is-done .check {
  color: var(--ok);
  cursor: default;
}

.item-main {
  flex: 1;
  min-width: 0;
  padding-top: 3px;
}

.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink);
  font-size: 15px;
  font-weight: 500;
}

.is-done .item-title > span:first-child {
  color: var(--ink-3);
  text-decoration: line-through;
}

.flag {
  color: var(--danger);
  font-size: 12px;
  font-weight: 500;
}

.item-content {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.5;
}

.m-meta {
  margin-top: 6px;
}

.overdue {
  color: var(--danger);
}

.radio-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}
</style>
