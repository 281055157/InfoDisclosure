<script setup>
import { reactive, ref } from 'vue'
import { Send, Upload } from 'lucide-vue-next'
import { api } from '../api'

const form = reactive({
  file: null,
  parameterFile: null,
  documentCategory: 'AUTO',
  declaredProductCode: '',
  declaredDocumentType: ''
})
const submitting = ref(false)
const message = ref('')

async function submit() {
  if (!form.file) {
    message.value = '请先选择 PDF 文件'
    return
  }
  submitting.value = true
  message.value = ''
  try {
    const data = new FormData()
    data.append('file', form.file)
    if (form.parameterFile) data.append('parameterFile', form.parameterFile)
    data.append('documentCategory', form.documentCategory)
    data.append('declaredProductCode', form.declaredProductCode)
    data.append('declaredDocumentType', form.declaredDocumentType)
    const result = await api.createTask(data)
    message.value = '任务已创建'
    location.hash = `#/tasks/${result.taskId}`
  } catch (e) {
    message.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-title">
    <div><h2>新建审核任务</h2><div class="subtitle">上传附件并创建异步审核任务</div></div>
  </div>

  <section class="panel">
    <div class="panel-header"><h3>任务信息</h3></div>
    <div class="panel-body grid cols-2">
      <div class="field">
        <label>协议/公告 PDF</label>
        <input class="control" type="file" accept="application/pdf" @change="form.file = $event.target.files[0]">
        <div class="muted"><Upload :size="14" />{{ form.file?.name || '请选择待审核文件' }}</div>
      </div>
      <div class="field">
        <label>公告参数表（可选）</label>
        <input class="control" type="file" accept=".xlsx,.xls" @change="form.parameterFile = $event.target.files[0]">
        <div class="muted">{{ form.parameterFile?.name || '公告类型会读取 B9' }}</div>
      </div>
      <div class="field"><label>文件类别</label><select v-model="form.documentCategory" class="control"><option>AUTO</option><option>PROTOCOL</option><option>ANNOUNCEMENT</option></select></div>
      <div class="field"><label>声明产品代码</label><input v-model="form.declaredProductCode" class="control" placeholder="默认使用文件名下划线前"></div>
      <div class="field"><label>声明文件类型</label><input v-model="form.declaredDocumentType" class="control" placeholder="协议默认取文件名下划线后，公告默认取 B9"></div>
      <div style="grid-column:1/-1;display:flex;gap:10px;align-items:center">
        <button class="btn primary" :disabled="submitting" @click="submit"><Send :size="16" />{{ submitting ? '提交中' : '提交审核' }}</button>
        <span class="muted">{{ message }}</span>
      </div>
    </div>
  </section>
</template>
