<template>
  <div class="daily-page">
    <van-nav-bar title="日常事项" fixed placeholder>
      <template #right>
        <van-icon name="plus" size="20" @click="openCreate" />
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="activeStatus" sticky offset-top="46" @change="loadItems">
      <van-tab title="待完成" :name="1" />
      <van-tab title="已完成" :name="2" />
      <van-tab title="全部" :name="0" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="loadItems">
      <van-empty v-if="!loading && items.length === 0" description="还没有记录">
        <van-button type="primary" size="small" round @click="openCreate">添加一件事</van-button>
      </van-empty>

      <div v-else class="item-list">
        <van-swipe-cell v-for="item in items" :key="item.id">
          <div class="item-card">
            <div class="item-main">
              <div class="item-title">
                <span>{{ item.title }}</span>
                <van-tag v-if="item.priority === 2" type="danger" plain>重要</van-tag>
              </div>
              <div v-if="item.content" class="item-content">{{ item.content }}</div>
              <div class="item-meta">
                <span>{{ typeText(item.itemType) }}</span>
                <span v-if="item.targetDate">{{ item.targetDate }}</span>
              </div>
            </div>
            <van-button
              v-if="item.status === 1"
              size="small"
              type="success"
              plain
              @click="completeItem(item)"
            >
              完成
            </van-button>
          </div>
          <template #right>
            <van-button square type="primary" text="编辑" class="swipe-btn" @click="openEdit(item)" />
            <van-button square type="warning" text="归档" class="swipe-btn" @click="archiveItem(item)" />
            <van-button square type="danger" text="删除" class="swipe-btn" @click="deleteItem(item)" />
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <van-action-sheet v-model:show="showEditor" :title="editingId ? '编辑事项' : '添加事项'">
      <van-form class="editor" @submit="saveItem">
        <van-cell-group inset>
          <van-field v-model="form.title" label="标题" placeholder="例如：周末买菜" required />
          <van-field v-model="form.content" label="说明" type="textarea" rows="2" placeholder="补充说明" />
          <van-field v-model="form.targetDate" label="日期" type="date" />
          <van-field label="类型">
            <template #input>
              <van-radio-group v-model="form.itemType" direction="horizontal">
                <van-radio :name="1">待办</van-radio>
                <van-radio :name="2">清单</van-radio>
                <van-radio :name="3">约会</van-radio>
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
        <div class="form-actions">
          <van-button round block type="primary" native-type="submit" :loading="saving">
            保存
          </van-button>
        </div>
      </van-form>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import { dailyAPI, type DailyItem } from '@/services/api'

const items = ref<DailyItem[]>([])
const loading = ref(false)
const refreshing = ref(false)
const saving = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const activeStatus = ref(1)

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
  loading.value = true
  try {
    const status = activeStatus.value === 0 ? undefined : activeStatus.value
    items.value = await dailyAPI.list({ status })
  } catch (error: any) {
    showFailToast(error.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
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
      content: form.content,
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

async function completeItem(item: DailyItem) {
  await dailyAPI.complete(item.id)
  showSuccessToast('已完成')
  loadItems()
}

async function archiveItem(item: DailyItem) {
  await dailyAPI.archive(item.id)
  showSuccessToast('已归档')
  loadItems()
}

async function deleteItem(item: DailyItem) {
  await dailyAPI.delete(item.id)
  showSuccessToast('已删除')
  loadItems()
}

function typeText(type: number) {
  return ({ 1: '待办', 2: '清单', 3: '约会', 4: '约定' } as Record<number, string>)[type] || '事项'
}
</script>

<style scoped>
.daily-page {
  min-height: 100vh;
  background: #f6f7f9;
  padding-bottom: 72px;
}

.item-list {
  padding: 12px;
}

.item-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 8px;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #202124;
}

.item-content {
  margin-top: 6px;
  color: #666;
  font-size: 13px;
  line-height: 1.4;
}

.item-meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}

.swipe-btn {
  height: 100%;
}

.editor {
  padding: 16px 0;
}

.form-actions {
  padding: 16px;
}
</style>
