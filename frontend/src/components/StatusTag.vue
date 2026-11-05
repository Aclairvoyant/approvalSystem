<template>
  <span class="status-tag" :class="`status-tag--${tone}`">{{ text }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { applicationStatusText, applicationStatusTone } from '@/utils/status'

const props = defineProps<{ status?: number }>()

const text = computed(() => applicationStatusText(props.status))
const tone = computed(() => applicationStatusTone(props.status))
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.status-tag--accent {
  background: var(--accent-soft);
  color: var(--accent);
}

.status-tag--ok {
  background: var(--ok-soft);
  color: var(--ok-ink);
}

.status-tag--danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.status-tag--neutral {
  background: var(--surface-2);
  color: var(--ink-2);
}
</style>
