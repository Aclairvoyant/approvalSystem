<template>
  <ol v-if="items.length" class="timeline">
    <li v-for="item in items" :key="item.key" class="timeline-item">
      <div class="timeline-rail">
        <UserAvatar :src="item.avatar" :name="item.name" :size="32" />
      </div>

      <div class="timeline-body">
        <div class="timeline-head">
          <span class="timeline-name">{{ item.name || '未知用户' }}</span>
          <span class="timeline-action">{{ item.action }}</span>
          <time class="timeline-time">{{ formatDateTime(item.time) }}</time>
        </div>

        <p v-if="item.text" class="timeline-text">{{ item.text }}</p>

        <!-- 审批章：盖在最后一条批准 / 驳回记录上，后面再有评论也不会消失 -->
        <div v-if="item.stamp" class="stamp" :class="`stamp--${item.stamp}`" aria-hidden="true">
          {{ item.stamp === 'approved' ? '已批准' : '已驳回' }}
        </div>

        <template v-if="item.comment">
          <ul v-if="item.comment.replies?.length" class="replies">
            <li v-for="reply in item.comment.replies" :key="reply.id" class="reply">
              <div class="reply-head">
                <span class="timeline-name">{{ reply.userName }}</span>
                <time class="timeline-time">{{ relativeTime(reply.createdAt) }}</time>
                <button
                  v-if="reply.userId === currentUserId"
                  type="button"
                  class="text-btn text-btn--danger"
                  @click="emit('delete', reply)"
                >
                  删除
                </button>
              </div>
              <p class="timeline-text">{{ reply.content }}</p>
            </li>
          </ul>
          <div class="comment-actions">
            <button type="button" class="text-btn" @click="emit('reply', item.comment)">回复</button>
            <button
              v-if="item.comment.userId === currentUserId"
              type="button"
              class="text-btn text-btn--danger"
              @click="emit('delete', item.comment)"
            >
              删除
            </button>
          </div>
        </template>
      </div>
    </li>
  </ol>
  <p v-else class="timeline-empty">还没有处理记录</p>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ApplicationComment, OperationLogDTO } from '@/services/api'
import UserAvatar from '@/components/UserAvatar.vue'
import { formatDateTime, relativeTime } from '@/utils/format'
import { operationTypeText } from '@/utils/status'

const props = defineProps<{
  logs: OperationLogDTO[]
  comments: ApplicationComment[]
  currentUserId: number
}>()

const emit = defineEmits<{
  reply: [comment: ApplicationComment]
  delete: [comment: ApplicationComment]
}>()

interface TimelineEntry {
  key: string
  time: string
  name?: string
  avatar?: string
  action: string
  text?: string
  stamp?: 'approved' | 'rejected'
  comment?: ApplicationComment
}

const OPERATION_APPROVE = 2
const OPERATION_REJECT = 3

const items = computed<TimelineEntry[]>(() => {
  const decisions = props.logs.filter(log => log.operationType === OPERATION_APPROVE || log.operationType === OPERATION_REJECT)
  const lastDecisionId = decisions.length ? decisions[decisions.length - 1].id : null

  const entries: TimelineEntry[] = props.logs.map(log => ({
    key: `log-${log.id}`,
    time: log.createdAt,
    name: log.operatorName,
    action: log.operationTypeDesc || operationTypeText(log.operationType),
    text: log.operationDetail,
    stamp: log.id === lastDecisionId
      ? (log.operationType === OPERATION_APPROVE ? 'approved' : 'rejected')
      : undefined,
  }))

  props.comments
    .filter(comment => !comment.parentId)
    .forEach(comment => {
      entries.push({
        key: `comment-${comment.id}`,
        time: comment.createdAt,
        name: comment.userName,
        avatar: comment.userAvatar,
        action: '评论',
        text: comment.content,
        comment,
      })
    })

  return entries.sort((a, b) => a.time.localeCompare(b.time))
})
</script>

<style scoped>
.timeline {
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 18px;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

/* 头像之间的竖线 */
.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 36px;
  bottom: 4px;
  left: 15.5px;
  width: 1px;
  background: var(--rule);
}

.timeline-rail {
  flex-shrink: 0;
}

.timeline-body {
  position: relative;
  flex: 1;
  min-width: 0;
  padding-top: 5px;
}

.timeline-head,
.reply-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 6px;
}

.timeline-name {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.timeline-action {
  color: var(--ink-2);
  font-size: 14px;
}

.timeline-time {
  margin-left: auto;
  color: var(--ink-3);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.timeline-text {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.replies {
  margin: 10px 0 0;
  padding: 2px 0 2px 12px;
  border-left: 2px solid var(--rule);
  list-style: none;
}

.reply + .reply {
  margin-top: 10px;
}

.comment-actions {
  display: flex;
  gap: 16px;
  margin-top: 6px;
}

.text-btn {
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: 13px;
  cursor: pointer;
}

.reply-head .text-btn {
  margin-left: 8px;
}

.text-btn--danger {
  color: var(--danger);
}

.stamp {
  position: absolute;
  top: -6px;
  right: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border: 3px double currentColor;
  border-radius: 50%;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 2px;
  opacity: 0.85;
  pointer-events: none;
  transform: rotate(-15deg);
  animation: stamp-in 0.35s ease-out;
}

.stamp--approved {
  color: var(--ok);
}

.stamp--rejected {
  color: var(--danger);
}

@keyframes stamp-in {
  from {
    opacity: 0;
    transform: scale(1.4) rotate(-15deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .stamp {
    animation: none;
  }
}

.timeline-empty {
  padding: 12px 0;
  color: var(--ink-3);
  font-size: 13px;
  text-align: center;
}
</style>
