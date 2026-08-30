<script setup>
import { AlertTriangle } from 'lucide-vue-next'

const props = defineProps({
  open: { type: Boolean, default: false },
  version: { type: Object, default: null }
})
const emit = defineEmits(['close', 'confirm'])
</script>

<template>
  <Teleport to="body">
    <div v-if="props.open" class="modal-backdrop rule-publish-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h3><AlertTriangle :size="18" /> 发布确认</h3>
          <button class="btn ghost" @click="emit('close')">关闭</button>
        </div>
        <div class="panel-body">
          <p>发布后该版本不可直接修改，并会切换为当前生效版本。</p>
          <p class="muted">版本：{{ props.version?.versionCode || '-' }}</p>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="emit('close')">取消</button>
          <button class="btn primary" @click="emit('confirm')">确认发布</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.rule-publish-backdrop {
  z-index: 90;
}
</style>
