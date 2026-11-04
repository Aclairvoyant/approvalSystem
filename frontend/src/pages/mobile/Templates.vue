<template>
  <div class="templates-page">
    <van-nav-bar title="申请模板" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <van-icon name="plus" size="20" aria-label="添加模板" @click="openCreate" />
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" class="m-page" @refresh="loadTemplates">
      <van-empty v-if="!loading && templates.length === 0" class="m-empty" description="把常用的申请存成模板，下次一键带入">
        <van-button type="primary" size="small" @click="openCreate">添加模板</van-button>
      </van-empty>

      <div v-else class="template-list">
        <!-- 只有创建者能编辑和删除，对象共享过来的模板只能套用 -->
        <van-swipe-cell
          v-for="template in templates"
          :key="template.id"
          class="m-card"
          :disabled="!isMine(template)"
        >
          <div class="template-row">
            <div class="template-main">
              <div class="m-title">{{ template.title }}</div>
              <p class="template-desc">{{ template.description }}</p>
              <div class="m-meta">
                <span>{{ isMine(template) ? (template.shared ? '我创建 · 双方共享' : '我创建 · 仅自己') : '对象共享' }}</span>
                <span class="tabular">用过 {{ template.usageCount || 0 }} 次</span>
              </div>
            </div>
            <van-button size="small" plain type="primary" :loading="usingId === template.id" @click="useTemplate(template)">
              套用
            </van-button>
          </div>
          <template #right>
            <van-button square type="primary" text="编辑" class="m-swipe-btn" @click="openEdit(template)" />
            <van-button square type="danger" text="删除" class="m-swipe-btn" @click="deleteTemplate(template)" />
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <van-action-sheet v-model:show="showEditor" :title="editingId ? '编辑模板' : '添加模板'">
      <van-form class="m-sheet" @submit="saveTemplate">
        <van-cell-group inset>
          <van-field v-model="form.title" label="标题" maxlength="100" placeholder="例如：周末加班" required />
          <van-field v-model="form.description" label="说明" type="textarea" rows="3" maxlength="500" required />
          <van-field v-model="form.remark" label="备注" maxlength="200" />
          <van-field label="共享给对象">
            <template #input>
              <van-switch v-model="form.shared" size="20" />
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
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { applicationTemplateAPI, type ApplicationTemplate } from '@/services/api'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const templates = ref<ApplicationTemplate[]>([])
const loading = ref(false)
const refreshing = ref(false)
const saving = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const usingId = ref<number | null>(null)

const form = reactive({
  title: '',
  description: '',
  remark: '',
  shared: true,
})

onMounted(() => {
  loadTemplates()
})

function isMine(template: ApplicationTemplate) {
  return template.creatorId === userStore.userId
}

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await applicationTemplateAPI.list()
  } catch (error: any) {
    showFailToast(error.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', description: '', remark: '', shared: true })
  showEditor.value = true
}

function openEdit(template: ApplicationTemplate) {
  editingId.value = template.id
  Object.assign(form, {
    title: template.title,
    description: template.description,
    remark: template.remark || '',
    shared: template.shared,
  })
  showEditor.value = true
}

async function saveTemplate() {
  if (!form.title.trim() || !form.description.trim()) {
    showFailToast('请填写标题和说明')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      description: form.description.trim(),
      remark: form.remark.trim(),
      shared: form.shared,
    }
    if (editingId.value) {
      await applicationTemplateAPI.update(editingId.value, payload)
    } else {
      await applicationTemplateAPI.create(payload)
    }
    showEditor.value = false
    showSuccessToast('已保存')
    loadTemplates()
  } catch (error: any) {
    showFailToast(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function useTemplate(template: ApplicationTemplate) {
  usingId.value = template.id
  try {
    const used = await applicationTemplateAPI.use(template.id)
    await router.push({
      path: '/mobile/applications',
      query: {
        title: used.title,
        description: used.description,
        remark: used.remark || '',
      },
    })
  } catch (error: any) {
    showFailToast(error.message || '套用失败')
  } finally {
    usingId.value = null
  }
}

async function deleteTemplate(template: ApplicationTemplate) {
  try {
    await showConfirmDialog({ title: '删除模板', message: `确定删除"${template.title}"吗？` })
  } catch {
    return
  }
  try {
    await applicationTemplateAPI.delete(template.id)
    showSuccessToast('已删除')
    loadTemplates()
  } catch (error: any) {
    showFailToast(error.message || '删除失败')
  }
}
</script>

<style scoped>
.templates-page {
  min-height: 100%;
  background: var(--bg);
}

.template-list .m-card + .m-card {
  margin-top: 8px;
}

.template-list :deep(.van-swipe-cell) {
  overflow: hidden;
}

.template-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}

.template-main {
  flex: 1;
  min-width: 0;
}

.template-desc {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.m-meta {
  margin-top: 6px;
}
</style>
