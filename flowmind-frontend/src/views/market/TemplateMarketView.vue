<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { marketApi, type MarketQuery } from '@/api/engineAndMarketApi'
import { categories, categoryLabel, type Workflow } from '@/api/types'
import '../engine/engine.css'
const router = useRouter(), query = reactive<MarketQuery>({ sortBy:'popular',pageNum:1,pageSize:12 }), rows = ref<Workflow[]>([]), total = ref(0), loading = ref(false), error = ref('')
let sequence = 0
async function load(reset = false) {
  if (reset) query.pageNum = 1
  if (query.minNodeCount != null && query.maxNodeCount != null && query.minNodeCount > query.maxNodeCount) { error.value = '节点数区间无效'; return }
  const current = ++sequence; loading.value = true; error.value = ''
  try { const page = await marketApi.list({ ...query, category:query.category || undefined }); if(current === sequence) { rows.value = page.records; total.value = page.total } }
  catch { if(current === sequence) error.value = '模板加载失败，请重试' } finally { if(current === sequence) loading.value = false }
}
function reset() { Object.keys(query).forEach(k => delete (query as unknown as Record<string,unknown>)[k]); Object.assign(query,{sortBy:'popular',pageNum:1,pageSize:12}); void load() }
onMounted(() => load())
</script>
<template><div class="engine-heading"><div><span class="eyebrow">TEMPLATE MARKET / 模板生态</span><h1>从一个好想法开始</h1><p>探索公开工作流，查看完整拓扑，一键克隆到你的空间。</p></div><el-button type="primary" @click="router.push('/engine/text-to-flow')">一句话创建</el-button></div><section class="engine-panel"><el-form class="engine-filter" inline @submit.prevent="load(true)"><el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="名称、描述、标签"/></el-form-item><el-form-item label="分类"><el-select v-model="query.category" clearable placeholder="全部分类"><el-option v-for="c in categories" :key="c.value" :value="c.value" :label="c.label"/></el-select></el-form-item><el-form-item label="标签"><el-input v-model="query.tag" clearable placeholder="例如 Java"/></el-form-item><el-form-item label="最少节点"><el-input-number v-model="query.minNodeCount" :min="0" :controls="false"/></el-form-item><el-form-item label="最多节点"><el-input-number v-model="query.maxNodeCount" :min="0" :controls="false"/></el-form-item><el-form-item label="排序"><el-select v-model="query.sortBy"><el-option label="最多克隆" value="popular"/><el-option label="最多点赞" value="likes"/><el-option label="最近更新" value="latest"/></el-select></el-form-item><el-form-item><el-button type="primary" native-type="submit">检索模板</el-button><el-button @click="reset">重置</el-button></el-form-item></el-form><el-alert v-if="error" :title="error" type="error" :closable="false"/><div v-loading="loading" style="min-height:180px"><div class="market-grid"><article v-for="w in rows" :key="w.id" class="market-card"><el-tag effect="plain">{{ categoryLabel(w.category) }}</el-tag><h3>{{ w.wfName }}</h3><p>{{ w.description || '暂无描述' }}</p><div class="engine-hint">{{ w.tags }}<br/>{{ w.nodeCount }} 个节点 · {{ w.forkCount }} 次克隆 · {{ w.likeCount }} 次点赞<br/>创建者：{{ w.creatorName }}</div><el-button @click="router.push(`/market/${w.id}`)">预览模板</el-button></article></div><el-empty v-if="!loading && !rows.length" description="没有符合条件的公开模板，试试调整筛选条件"/></div><div class="engine-pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[12,24,48]" layout="total, sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/></div></section></template>
