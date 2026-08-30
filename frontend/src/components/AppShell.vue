<script setup>
import { computed, ref, watch } from 'vue'
import { BarChart3, ChevronDown, FilePlus2, ListChecks, Settings2, SlidersHorizontal, Trash2 } from 'lucide-vue-next'

const props = defineProps({ active: String })

const links = [
  { key: 'dashboard', label: '工作台', hash: '#/dashboard', icon: BarChart3 },
  { key: 'tasks', label: '审核任务', hash: '#/tasks', icon: ListChecks },
  { key: 'create', label: '新建任务', hash: '#/create', icon: FilePlus2 },
  { key: 'task-admin', label: '任务管理', hash: '#/task-admin', icon: Trash2 }
]

const ruleLinks = [
  { key: 'rules', label: '规则列表', hash: '#/rules' },
  { key: 'rule-feedback', label: '规则反馈', hash: '#/rule-feedback' }
]

const modelLinks = [
  { key: 'providers', label: '模型供应商', hash: '#/providers' },
  { key: 'models', label: '模型链路', hash: '#/models' }
]

const ruleMenuOpen = ref(['rules', 'rule-feedback'].includes(props.active))
const modelMenuOpen = ref(['providers', 'models'].includes(props.active))

const activeLabel = computed(() => {
  if (props.active === 'detail') return '任务详情'
  const direct = links.find(x => x.key === props.active)
  if (direct) return direct.label
  const rule = ruleLinks.find(x => x.key === props.active)
  if (rule) return rule.label
  const model = modelLinks.find(x => x.key === props.active)
  return model ? model.label : '工作台'
})

watch(() => props.active, value => {
  if (['rules', 'rule-feedback'].includes(value)) {
    ruleMenuOpen.value = true
  }
  if (['providers', 'models'].includes(value)) {
    modelMenuOpen.value = true
  }
})

function go(hash) {
  location.hash = hash
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-title">信息披露附件<br>智能预审工作台</div>
      </div>
      <nav class="nav">
        <button v-for="link in links" :key="link.key" :class="{ active: active === link.key || (active === 'detail' && link.key === 'tasks') }" @click="go(link.hash)">
          <component :is="link.icon" :size="18" />
          <span>{{ link.label }}</span>
        </button>
        <div class="nav-group" :class="{ open: ruleMenuOpen }">
          <button class="nav-parent" :class="{ active: ['rules', 'rule-feedback'].includes(active) }" @click="ruleMenuOpen = !ruleMenuOpen">
            <Settings2 :size="18" />
            <span>规则管理</span>
            <ChevronDown class="nav-chevron" :size="16" />
          </button>
          <Transition name="submenu">
            <div v-if="ruleMenuOpen" class="nav-submenu">
              <button v-for="link in ruleLinks" :key="link.key" class="sub-nav-link" :class="{ active: active === link.key }" @click="go(link.hash)">
                <span>{{ link.label }}</span>
              </button>
            </div>
          </Transition>
        </div>
        <div class="nav-group" :class="{ open: modelMenuOpen }">
          <button class="nav-parent" :class="{ active: ['providers', 'models'].includes(active) }" @click="modelMenuOpen = !modelMenuOpen">
            <SlidersHorizontal :size="18" />
            <span>模型链路</span>
            <ChevronDown class="nav-chevron" :size="16" />
          </button>
          <Transition name="submenu">
            <div v-if="modelMenuOpen" class="nav-submenu">
              <button v-for="link in modelLinks" :key="link.key" class="sub-nav-link" :class="{ active: active === link.key }" @click="go(link.hash)">
                <span>{{ link.label }}</span>
              </button>
            </div>
          </Transition>
        </div>
      </nav>
    </aside>
    <main class="main">
      <header class="topbar">
        <div>信息披露预审 / <strong>{{ activeLabel }}</strong></div>
      </header>
      <section class="content">
        <slot />
      </section>
    </main>
  </div>
</template>
