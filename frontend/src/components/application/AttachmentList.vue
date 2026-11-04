<template>
  <div v-if="images.length" class="images">
    <van-image
      v-for="(img, index) in images"
      :key="img.attachmentId || img.fileUrl"
      width="76"
      height="76"
      radius="4"
      fit="cover"
      :src="img.fileUrl"
      @click="preview(index)"
    />
  </div>
  <ul v-if="files.length" class="files">
    <li v-for="file in files" :key="file.attachmentId || file.fileUrl">
      <van-icon name="description" />
      <a :href="file.fileUrl" target="_blank" rel="noopener">{{ file.fileName || '附件' }}</a>
    </li>
  </ul>
</template>

<script setup lang="ts">
import { showImagePreview } from 'vant'
import type { FileUploadResponse } from '@/services/api'

const props = defineProps<{
  images: FileUploadResponse[]
  files: FileUploadResponse[]
}>()

function preview(startPosition: number): void {
  showImagePreview({
    images: props.images.map(img => img.fileUrl || ''),
    startPosition,
  })
}
</script>

<style scoped>
.images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.files {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.files li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  color: var(--ink-2);
  font-size: 14px;
}

.files a {
  overflow: hidden;
  color: var(--accent);
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
