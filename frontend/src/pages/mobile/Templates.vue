<template>
  <div class="templates-page">
    <van-nav-bar title="申请模板" fixed placeholder>
      <template #right>
        <van-icon name="plus" size="20" @click="openCreate" />
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="loadTemplates">
      <van-empty v-if="!loading && templates.length === 0" description="把常用申请保存成模板">
        <van-button type="primary" size="small" round @click="openCreate">添加模板</van-button>
      </van-empty>

      <div v-else class="template-list">
        <van-swipe-cell v-for="template in templates" :key="template.id">
          <div class="template-card">
            <div class="template-main">
              <div class="template-title">{{ template.title }}</div>
              <div class="template-desc">{{ template.description }}</div>
              <div class="template-meta">
                <span>{{ template.shared ? '双方共享' : '仅自己' }}</span>
                <span>使用 {{ template.usageCount || 0 }} 次</span>
              </div>
            </div>
            <van-button size="small" type="primary" plain @click="useTemplate(template)">套用</van-button>
          </div>
          <template #right>
            <van-button square type="primary" text="编辑" class="swipe-btn" @click="openEdit(template)" />
            <van-button square type="danger" text="删除" class="swipe-btn" @click="deleteTemplate(template)" />
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <van-action-sheet v-model:show="showEditor" :title="editingId ? '编辑模板' : '添加模板'">
      <van-form class="editor" @submit="saveTemplate">
        <van-cell-group inset>
          <van-field v-model="form.title" label="标题" required />
          <van-field v-model="form.description" label="说明" type="textarea" rows="3" required />
          <van-field v-model="form.remark" label="备注" />
          <van-field label="共享给对象">
            <template #input>
              <van-switch v-model="form.shared" size="20" />
            </template>
          </van-field>
        </van-cell-group>
        <div class="form-actions">
          <van-button round block type="primary" native-type="submit" :loading="saving">保存</van-button>
        </div>
      </van-form>
    </van-action-sheet>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { applicationTemplateAPI, type ApplicationTemplate } from '@/services/api'

const router = useRouter()
const templates = ref<ApplicationTemplate[]>([])
const loading = ref(false)
const refreshing = ref(false)
const saving = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  title: '',
  description: '',
  remark: '',
  shared: true,
})

onMounted(() => {
  loadTemplates()
})

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
      remark: form.remark,
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
  const used = await applicationTemplateAPI.use(template.id)
  router.push({
    path: '/mobile/applications',
    query: {
      title: used.title,
      description: used.description,
      remark: used.remark || '',
    },
  })
}

async function deleteTemplate(template: ApplicationTemplate) {
  await applicationTemplateAPI.delete(template.id)
  showSuccessToast('已删除')
  loadTemplates()
}
</script>

<style scoped>
.templates-page {
  min-height: 100vh;
  background: #f6f7f9;
  padding-bottom: 72px;
}

.template-list {
  padding: 12px;
}

.template-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 8px;
}

.template-main {
  flex: 1;
  min-width: 0;
}

.template-title {
  font-weight: 600;
  color: #202124;
}

.template-desc {
  margin-top: 6px;
  color: #666;
  font-size: 13px;
  line-height: 1.4;
}

.template-meta {
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
