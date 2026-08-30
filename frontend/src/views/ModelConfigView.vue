<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Play, Plus, RefreshCw, Save, X } from 'lucide-vue-next'
import { api } from '../api'

const models = ref([])
const providers = ref([])
const testResult = ref('')
const formOpen = ref(false)
const form = reactive(defaultModel())

const providerOptions = computed(() => providers.value.filter(provider => provider.enabled))

function defaultModel() {
  return {
    id: null,
    providerCode: '',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    modelCode: '',
    modelName: '',
    priority: 10,
    enabled: true,
    timeoutSeconds: 120,
    maxRetries: 1,
    temperature: 0.1,
    responseFormat: 'json_object',
    apiKeyEnv: ''
  }
}

async function load() {
  const [modelRows, providerRows] = await Promise.all([api.models(), api.providers()])
  models.value = modelRows
  providers.value = providerRows
}

function openCreate() {
  Object.assign(form, defaultModel())
  if (providerOptions.value.length) {
    form.providerCode = providerOptions.value[0].providerCode
    applySelectedProvider()
  }
  formOpen.value = true
}

function openEdit(model) {
  Object.assign(form, model)
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
}

async function saveModel() {
  await api.saveModel(form)
  formOpen.value = false
  await load()
}

async function test(model) {
  const result = await api.testModel(model.id)
  testResult.value = `${model.modelName}: ${result.ok ? '成功' : '失败'} ${result.message || ''}`
}

function applySelectedProvider() {
  const provider = providers.value.find(row => row.providerCode === form.providerCode)
  if (!provider) return
  form.providerType = provider.providerType
  form.baseUrl = provider.baseUrl
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <div>
      <h2>模型链路</h2>
      <div class="subtitle">维护模型优先级、超时、重试和降级顺序</div>
    </div>
    <div style="display:flex;gap:8px">
      <button class="btn" @click="load"><RefreshCw :size="16" />刷新</button>
      <button class="btn primary" @click="openCreate"><Plus :size="16" />新增模型</button>
    </div>
  </div>

  <section class="panel">
    <div class="panel-header"><h3>模型链路</h3><span class="muted">{{ testResult || `${models.length} 个模型` }}</span></div>
    <table class="table model-table">
      <thead><tr><th>优先级</th><th>模型</th><th>供应商</th><th>超时/重试</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="model in models" :key="model.id">
          <td>{{ model.priority }}</td>
          <td><strong>{{ model.modelName }}</strong><div class="muted">{{ model.modelCode }}</div></td>
          <td>{{ model.providerCode }}<div class="muted">{{ model.providerType }}</div></td>
          <td>{{ model.timeoutSeconds }}s / {{ model.maxRetries }} 次</td>
          <td><span class="badge" :class="model.enabled ? 'green' : 'gray'">{{ model.enabled ? '启用' : '停用' }}</span></td>
          <td><button class="btn" @click="openEdit(model)">编辑</button><button class="btn" @click="test(model)"><Play :size="14" />测试</button></td>
        </tr>
      </tbody>
    </table>
    <div v-if="!models.length" class="empty">暂无模型配置</div>
  </section>

  <Transition name="slide-page">
    <div v-if="formOpen" class="form-page-backdrop">
      <aside class="form-page">
        <div class="form-page-header">
          <div>
            <h3>{{ form.id ? '编辑模型' : '新增模型' }}</h3>
            <div class="muted">配置模型调用参数和降级优先级</div>
          </div>
          <button class="btn ghost" @click="closeForm"><X :size="18" /></button>
        </div>
        <div class="form-page-body grid cols-2">
          <div class="field">
            <label>供应商</label>
            <select v-model="form.providerCode" class="control" @change="applySelectedProvider">
              <option value="">请选择供应商</option>
              <option v-for="provider in providerOptions" :key="provider.id" :value="provider.providerCode">
                {{ provider.providerCode }}
              </option>
            </select>
          </div>
          <div class="field"><label>供应商类型</label><input v-model="form.providerType" class="control" readonly></div>
          <div class="field" style="grid-column:1/-1"><label>Base URL</label><input v-model="form.baseUrl" class="control" readonly></div>
          <div class="field"><label>模型编码</label><input v-model="form.modelCode" class="control" placeholder="例如 gpt-4.1-mini-primary"></div>
          <div class="field"><label>模型名称</label><input v-model="form.modelName" class="control" placeholder="实际调用的 modelName"></div>
          <div class="field"><label>优先级</label><input v-model.number="form.priority" type="number" class="control"></div>
          <div class="field"><label>最大重试</label><input v-model.number="form.maxRetries" type="number" class="control"></div>
          <div class="field"><label>超时秒数</label><input v-model.number="form.timeoutSeconds" type="number" class="control"></div>
          <div class="field"><label>温度</label><input v-model.number="form.temperature" type="number" step="0.01" class="control"></div>
          <div class="field" style="grid-column:1/-1">
            <label>API Key 环境变量（可选）</label>
            <input v-model="form.apiKeyEnv" class="control" placeholder="留空表示不发送 Authorization 请求头">
            <div class="muted">内网模型无需鉴权时保持为空；需要鉴权时填写应用容器中的环境变量名。</div>
          </div>
          <label class="switch"><input v-model="form.enabled" type="checkbox">启用模型</label>
        </div>
        <div class="form-page-footer">
          <button class="btn" @click="closeForm">取消</button>
          <button class="btn primary" @click="saveModel"><Save :size="16" />保存模型</button>
        </div>
      </aside>
    </div>
  </Transition>
</template>
