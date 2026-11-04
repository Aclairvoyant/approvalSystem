<template>
  <span class="user-avatar" :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.42)}px` }">
    <img v-if="url && !failed" :src="url" :alt="name || ''" @error="failed = true" />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { avatarInitial, resolveAssetUrl } from '@/utils/asset'

const props = withDefaults(defineProps<{
  src?: string | null
  name?: string | null
  size?: number
}>(), {
  src: '',
  name: '',
  size: 40,
})

const failed = ref(false)
const url = computed(() => resolveAssetUrl(props.src))
const initial = computed(() => avatarInitial(props.name))

watch(() => props.src, () => {
  failed.value = false
})
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
  line-height: 1;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
