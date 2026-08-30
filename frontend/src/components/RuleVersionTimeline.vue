<script setup>
const props = defineProps({
  versions: { type: Array, default: () => [] },
  executions: { type: Array, default: () => [] },
  activeVersionId: { type: Number, default: null }
})
const emit = defineEmits(['select'])
</script>

<template>
  <div class="grid cols-2">
    <section class="panel">
      <div class="panel-header"><h3>版本时间线</h3><span class="muted">{{ props.versions.length }} 个版本</span></div>
      <div class="timeline">
        <button
          v-for="version in props.versions"
          :key="version.id"
          type="button"
          class="timeline-card version-button"
          @click="emit('select', version)"
        >
          <strong>{{ version.versionCode }}</strong>
          <span class="badge" :class="version.status === 'PUBLISHED' ? 'green' : 'orange'">{{ version.status }}</span>
          <span v-if="version.id === props.activeVersionId" class="badge purple">当前生效</span>
          <p class="muted">{{ version.changeSummary || version.description || '-' }}</p>
        </button>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h3>近期执行记录</h3><span class="muted">{{ props.executions.length }} 条</span></div>
      <table class="table compact">
        <thead><tr><th>状态</th><th>命中</th><th>问题</th><th>耗时</th></tr></thead>
        <tbody>
          <tr v-for="row in props.executions.slice(0, 8)" :key="row.id">
            <td>{{ row.executionStatus }}<div v-if="row.resultDetail || row.errorMessage" class="muted">{{ row.resultDetail || row.errorMessage }}</div></td>
            <td>{{ row.matched ? '是' : '否' }}</td>
            <td>{{ row.issueCount }}</td>
            <td>{{ row.durationMs ?? '-' }}ms</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!props.executions.length" class="empty">暂无执行记录</div>
    </section>
  </div>
</template>

<style scoped>
.version-button { width: 100%; text-align: left; background: white; border-top: 0; border-right: 0; border-bottom: 0; }
.version-button p { margin: 4px 0 0; }
</style>
