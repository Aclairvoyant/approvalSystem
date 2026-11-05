<template>
  <van-action-sheet v-model:show="visible" title="修改申请" :closeable="true">
    <van-form class="m-sheet" @submit="submit">
      <van-cell-group inset>
        <van-field
          v-model="form.title"
          label="标题"
          maxlength="100"
          required
          :rules="[{ required: true, message: '请输入标题' }]"
        />
        <van-field
          v-model="form.description"
          label="说明"
          type="textarea"
          rows="3"
          autosize
          maxlength="1000"
          required
          :rules="[{ required: true, message: '请输入说明' }]"
        />
        <van-field v-model="form.remark" label="备注" placeholder="选填" maxlength="200" />
      </van-cell-group>
      <div class="m-sheet-footer">
        <van-button block type="primary" native-type="submit" :loading="saving">保存修改</van-button>
      </div>
    </van-form>
  </van-action-sheet>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import { applicationAPI, type Application } from '@/services/api'

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const saving = ref(false)
const editing = ref<Application | null>(null)
const form = reactive({ title: '', description: '', remark: '' })

function open(application: Application): void {
  editing.value = application
  Object.assign(form, {
    title: application.title || '',
    description: application.description || '',
    remark: application.remark || '',
  })
  visible.value = true
}

defineExpose({ open })

async function submit(): Promise<void> {
  if (!editing.value) return
  saving.value = true
  try {
    await applicationAPI.updateApplication(editing.value.id, {
      // 修改接口不会更改审批人，这里原样带上
      approverId: editing.value.approverId,
      title: form.title.trim(),
      description: form.description.trim(),
      remark: form.remark.trim() || undefined,
    })
    showSuccessToast('已保存')
    visible.value = false
    emit('saved')
  } catch (error: any) {
    showFailToast(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>
