<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, RefreshCw, Save, X } from 'lucide-vue-next'
import { api } from '../api'

const providers = ref([])
const formOpen = ref(false)
const message = ref('')
const form = reactive(defaultProvider())

function defaultProvider() {
  return {
    id: null,
    providerCode: '',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    enabled: true
  }
}

async function load() {
  providers.value = await api.providers()
}

function openCreate() {
  Object.assign(form, defaultProvider())
  message.value = ''
  formOpen.value = true
}

function openEdit(provider) {
  Object.assign(form, provider)
  message.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
}

async function saveProvider() {
  await api.saveProvider(form)
  message.value = form.id ? '供应商已更新' : '供应商已新增'
  formOpen.value = false
  await load()
}

async function toggleProvider(provider) {
  await api.setProviderEnabled(provider.id, !provider.enabled)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page-title">
    <div>
      <h2>模型供应商</h2>
      <div class="subtitle">维护模型供应商、接口地址和启用状态</div>
    </div>
    <div style="display:flex;gap:8px">
      <button class="btn" @click="load"><RefreshCw :size="16" />刷新</button>
      <button class="btn primary" @click="openCreate"><Plus :size="16" />新增供应商</button>
    </div>
  </div>

  <section class="panel">
    <div class="panel-header"><h3>模型供应商</h3><span class="muted">{{ providers.length }} 个</span></div>
    <table class="table provider-table">
      <thead><tr><th>供应商</th><th>类型</th><th>Base URL</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="provider in providers" :key="provider.id">
          <td><strong>{{ provider.providerCode }}</strong></td>
          <td>{{ provider.providerType }}</td>
          <td><span class="muted">{{ provider.baseUrl }}</span></td>
          <td><span class="badge" :class="provider.enabled ? 'green' : 'gray'">{{ provider.enabled ? '启用' : '停用' }}</span></td>
          <td>
            <button class="btn" @click="openEdit(provider)">编辑</button>
            <button class="btn" @click="toggleProvider(provider)">{{ provider.enabled ? '停用' : '启用' }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!providers.length" class="empty">暂无模型供应商</div>
  </section>

  <Transition name="slide-page">
    <div v-if="formOpen" class="form-page-backdrop">
      <aside class="form-page">
        <div class="form-page-header">
          <div>
            <h3>{{ form.id ? '编辑供应商' : '新增供应商' }}</h3>
            <div class="muted">配置 OpenAI-compatible 供应商基础信息</div>
          </div>
          <button class="btn ghost" @click="closeForm"><X :size="18" /></button>
        </div>
        <div class="form-page-body grid cols-2">
          <div class="field"><label>供应商编码</label><input v-model="form.providerCode" class="control" placeholder="例如 openai-prod"></div>
          <div class="field"><label>供应商类型</label><select v-model="form.providerType" class="control"><option>OPENAI_COMPATIBLE</option><option>DEEPSEEK</option></select></div>
          <div class="field" style="grid-column:1/-1"><label>Base URL</label><input v-model="form.baseUrl" class="control" placeholder="例如 https://api.example.com/v1"></div>
          <label class="switch"><input v-model="form.enabled" type="checkbox">启用供应商</label>
          <span class="muted">{{ message }}</span>
        </div>
        <div class="form-page-footer">
          <button class="btn" @click="closeForm">取消</button>
          <button class="btn primary" @click="saveProvider"><Save :size="16" />保存供应商</button>
        </div>
      </aside>
    </div>
  </Transition>
</template>
