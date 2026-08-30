<script setup>
const props = defineProps({ taskId: [String, Number] })
</script>

<template>
  <section class="panel pdf-preview-panel">
    <div class="panel-header"><h3>PDF 原文</h3></div>
    <iframe v-if="props.taskId" class="pdf-frame" :src="`/api/reviews/${props.taskId}/file`"></iframe>
    <div v-else class="empty">暂无文件</div>
  </section>
</template>
