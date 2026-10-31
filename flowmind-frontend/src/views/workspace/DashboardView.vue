<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, ArrowRight, Connection, User, Reading, Lightning } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { workflowApi } from '@/api/workflowApi'
import { categoryLabel, formatTime, type Dashboard } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
const auth = useAuthStore(), router = useRouter()
const data = ref<Dashboard>(), loading = ref(false), failed = ref(false), chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | undefined, observer: ResizeObserver | undefined
const metrics = computed(() => [
  { label: '我的工作流', value: data.value?.myWorkflowCount, icon: Connection, tone: 'green', caption: '从想法到可执行的工作流' },
  { label: '协作工作流', value: data.value?.collabWorkflowCount, icon: User, tone: 'purple', caption: '与小组成员共同创作' },
  { label: '待完成实训', value: data.value?.pendingTaskCount, icon: Reading, tone: 'amber', caption: '当前进行中的课程任务' },
  { label: '可用算力', value: data.value?.remainingTokenQuota, icon: Lightning, tone: 'blue', caption: '剩余可用模型 Tokens' }
])
async function load() {
  loading.value = true; failed.value = false
  try {
    data.value = await workflowApi.dashboard()
    if (chartEl.value) {
      chart ??= echarts.init(chartEl.value)
      const counts = new Map<string, number>()
      data.value.recentWorkflows.forEach(w => counts.set(categoryLabel(w.category), (counts.get(categoryLabel(w.category)) ?? 0) + 1))
      chart.setOption({ animation: false, color: ['#347f67', '#9dbdab', '#c8b682', '#8c87b2', '#7da2bd'], tooltip: { trigger: 'item' }, legend: { bottom: 0, icon: 'circle', textStyle: { color: '#68766e' } }, series: [{ type: 'pie', radius: ['53%', '73%'], center: ['50%', '43%'], label: { show: false }, itemStyle: { borderWidth: 4, borderColor: '#fff', borderRadius: 6 }, data: [...counts].map(([name, value]) => ({ name, value })) }] })
    }
  } catch { failed.value = true } finally { loading.value = false }
}
onMounted(() => { void load(); observer = new ResizeObserver(() => chart?.resize()); if (chartEl.value) observer.observe(chartEl.value) })
onUnmounted(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><section v-loading="loading"><div class="page-heading"><div><p class="eyebrow">YOUR CREATIVE SPACE</p><h1>你好，{{ auth.user?.realName ?? '创作者' }} <span class="wave">✳</span></h1><p class="subtitle">把灵感连接起来，让下一个好想法成为现实。</p></div><el-button type="primary" :icon="Plus" size="large" @click="router.push('/workflows?create=1')">新建工作流</el-button></div>
<el-alert v-if="failed" title="工作台加载失败" type="error" show-icon :closable="false"><el-button text @click="load">重新加载</el-button></el-alert>
<div class="metric-grid"><article v-for="item in metrics" :key="item.label" class="metric-card"><div class="metric-top"><span>{{ item.label }}</span><span :class="['metric-icon', item.tone]"><el-icon><component :is="item.icon" /></el-icon></span></div><strong>{{ item.value?.toLocaleString() ?? '—' }}</strong><p>{{ item.caption }}</p></article></div>
<div class="dashboard-grid"><article class="panel recent-panel"><div class="panel-heading"><div><h2>最近编辑</h2><p>继续你的创作旅程</p></div><el-button text :icon="ArrowRight" @click="router.push('/workflows')">全部工作流</el-button></div><el-empty v-if="!data?.recentWorkflows.length" description="还没有工作流，创建你的第一个作品吧" /><div v-for="w in data?.recentWorkflows" :key="w.id" class="recent-row" @click="router.push(`/workflow/editor/${w.id}`)"><span class="workflow-glyph"><el-icon><Connection /></el-icon></span><div class="recent-info"><strong>{{ w.wfName }}</strong><small>{{ categoryLabel(w.category) }}<span>·</span>{{ w.nodeCount }} 个节点<span>·</span>{{ formatTime(w.updatedAt) }}</small></div><el-tag effect="plain" size="small">{{ w.versionTag }}</el-tag><el-icon><ArrowRight /></el-icon></div></article>
<aside class="panel distribution"><div class="panel-heading"><div><h2>创作分布</h2><p>最近编辑的工作流分类</p></div><span class="tiny-label">OVERVIEW</span></div><div ref="chartEl" class="distribution-chart" /><el-empty v-if="!data?.recentWorkflows.length" class="chart-empty" description="暂无分类数据" :image-size="55" /></aside></div>
<div class="inspiration-banner"><div><p class="eyebrow">BUILD TOGETHER</p><h2>一个想法，无限种连接。</h2><p>邀请小组成员，在同一张画布里共同构建。</p><el-button @click="router.push('/workflows?scope=COLLAB')">探索协作空间 <el-icon><ArrowRight /></el-icon></el-button></div><div class="banner-flow"><span>想法</span><i></i><span>智能</span><i></i><span>成果</span></div></div>
</section></template>
