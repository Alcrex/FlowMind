<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { ElMessage } from 'element-plus'
import { marketApi } from '@/api/engineAndMarketApi'
import { categoryLabel, type WorkflowDetail } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
import '../engine/engine.css'
const route = useRoute(), router = useRouter(), auth = useAuthStore(), detail = ref<WorkflowDetail>(), loading = ref(false), busy = ref(false), error = ref('')
const nodes = computed(() => detail.value?.nodes.map(n => ({ id:n.elementKey,position:n.positionJson,label:n.labelName,type:n.nodeType === 'INPUT' ? 'input' : n.nodeType === 'OUTPUT' ? 'output' : 'default' })) ?? [])
const edges = computed(() => detail.value?.edges.map(e => ({ id:e.elementKey,source:e.sourceKey,target:e.targetKey,label:e.sourceHandle === 'default' ? '' : e.sourceHandle })) ?? [])
async function load() { loading.value = true; error.value = ''; detail.value = undefined; try { detail.value = await marketApi.detail(Number(route.params.id)) } catch { error.value = '模板不存在或已取消公开' } finally { loading.value = false } }
async function action(kind:'like'|'fork') {
  if (!auth.token) { await router.push({path:'/login',query:{redirect:route.fullPath}}); return }
  busy.value = true; error.value = ''
  try {
    if(kind === 'like') { await marketApi.like(Number(route.params.id)); if(detail.value) detail.value.workflow.likeCount++; ElMessage.success('点赞成功') }
    else { const result = await marketApi.fork(Number(route.params.id)); ElMessage.success('已克隆到个人空间'); await router.push(`/workflow/editor/${result.newWorkflowId}`) }
  } catch(e) { error.value = e instanceof Error ? e.message : '操作失败，请重试' } finally { busy.value = false }
}
watch(() => route.params.id,load,{immediate:true})
</script>
<template><div v-loading="loading"><div class="engine-heading"><div><span class="eyebrow">TEMPLATE PREVIEW / 模板预览</span><h1>{{ detail?.workflow.wfName ?? '模板详情' }}</h1><p>{{ detail?.workflow.description }}</p></div><el-button @click="router.push('/market')">返回广场</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button v-if="!detail && !loading" @click="load">重新加载</el-button><template v-if="detail"><section class="engine-panel"><div class="engine-summary"><el-tag>{{ categoryLabel(detail.workflow.category) }}</el-tag><span>作者：{{ detail.workflow.creatorName }}</span><span>{{ detail.workflow.nodeCount }} 个节点 / {{ detail.workflow.edgeCount }} 条连线</span><span>{{ detail.workflow.versionTag }}</span></div><p class="engine-hint">{{ detail.workflow.tags }}</p><div class="engine-actions"><el-button type="primary" :loading="busy" @click="action('fork')">一键 Fork · {{ detail.workflow.forkCount }}</el-button><el-button :disabled="busy" @click="action('like')">点赞 · {{ detail.workflow.likeCount }}</el-button></div></section><div class="engine-stage" style="height:460px"><VueFlow :nodes="nodes" :edges="edges" fit-view-on-init :nodes-draggable="false" :nodes-connectable="false" :elements-selectable="false"/></div><section class="engine-panel"><h3>节点配置预览</h3><el-collapse><el-collapse-item v-for="n in detail.nodes" :key="n.elementKey" :title="`${n.labelName} · ${n.nodeType}`" :name="n.elementKey"><pre class="engine-pre">{{ JSON.stringify(n.configJson,null,2) }}</pre></el-collapse-item></el-collapse></section></template></div></template>
