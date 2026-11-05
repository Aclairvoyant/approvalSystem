<template>
  <section class="m-card m-card--pad composer">
    <div v-if="replyTo" class="reply-to">
      <span>回复 <b>{{ replyTo.userName }}</b></span>
      <button type="button" class="cancel" @click="emit('cancelReply')">取消</button>
    </div>
    <van-field
      ref="field"
      v-model="content"
      class="composer-field"
      type="textarea"
      :placeholder="replyTo ? `回复 ${replyTo.userName}` : '写一条评论'"
      rows="2"
      autosize
      maxlength="500"
      show-word-limit
    />
    <div class="composer-foot">
      <van-button size="small" type="primary" :loading="submitting" :disabled="!content.trim()" @click="submit">
        发送
      </van-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { showFailToast } from 'vant'
import { commentAPI, type ApplicationComment } from '@/services/api'

const props = defineProps<{
  applicationId: number
  replyTo: ApplicationComment | null
}>()

const emit = defineEmits<{
  posted: []
  cancelReply: []
}>()

const content = ref('')
const submitting = ref(false)
const field = ref<{ focus: () => void } | null>(null)

watch(() => props.replyTo, (value) => {
  if (value) nextTick(() => field.value?.focus())
})

async function submit(): Promise<void> {
  const text = content.value.trim()
  if (!text) return

  submitting.value = true
  try {
    await commentAPI.createComment({
      applicationId: props.applicationId,
      content: text,
      parentId: props.replyTo?.id,
    })
    content.value = ''
    emit('posted')
  } catch (error: any) {
    showFailToast(error.message || '评论失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.reply-to {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--ink-2);
  font-size: 13px;
}

.reply-to b {
  color: var(--ink);
  font-weight: 600;
}

.cancel {
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: 13px;
}

.composer-field {
  padding: 10px 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
}

.composer-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.composer-foot .van-button {
  min-width: 72px;
}
</style>
