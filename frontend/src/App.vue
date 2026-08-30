<script setup>
import { computed, onMounted, ref } from 'vue'
import AppShell from './components/AppShell.vue'
import DashboardView from './views/DashboardView.vue'
import TaskListView from './views/TaskListView.vue'
import CreateTaskView from './views/CreateTaskView.vue'
import TaskDetailView from './views/TaskDetailView.vue'
import TaskManagementView from './views/TaskManagementView.vue'
import RuleConfigView from './views/RuleConfigView.vue'
import RuleFeedbackView from './views/RuleFeedbackView.vue'
import ModelConfigView from './views/ModelConfigView.vue'
import ProviderConfigView from './views/ProviderConfigView.vue'

const route = ref(parseRoute())

window.addEventListener('hashchange', () => {
  route.value = parseRoute()
})

onMounted(() => {
  if (!location.hash) location.hash = '#/dashboard'
})

const currentView = computed(() => {
  if (route.value.name === 'tasks') return TaskListView
  if (route.value.name === 'create') return CreateTaskView
  if (route.value.name === 'detail') return TaskDetailView
  if (route.value.name === 'task-admin') return TaskManagementView
  if (route.value.name === 'rules') return RuleConfigView
  if (route.value.name === 'rule-feedback') return RuleFeedbackView
  if (route.value.name === 'providers') return ProviderConfigView
  if (route.value.name === 'models') return ModelConfigView
  return DashboardView
})

const currentProps = computed(() => {
  if (route.value.taskId) return { taskId: route.value.taskId, initialTab: route.value.query?.tab }
  if (route.value.name === 'task-admin' && route.value.query?.keyword) {
    return { initialKeyword: route.value.query.keyword }
  }
  return {}
})

function parseRoute() {
  const hash = location.hash.replace(/^#\/?/, '')
  const [path, queryString = ''] = hash.split('?')
  const query = Object.fromEntries(new URLSearchParams(queryString))
  const parts = path.split('/').filter(Boolean)
  if (parts[0] === 'tasks' && parts[1]) return { name: 'detail', taskId: parts[1], query }
  return { name: parts[0] || 'dashboard', query }
}
</script>

<template>
  <AppShell :active="route.name">
    <component :is="currentView" v-bind="currentProps" />
  </AppShell>
</template>
