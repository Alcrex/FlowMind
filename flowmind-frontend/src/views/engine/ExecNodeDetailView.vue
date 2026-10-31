<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { engineApi, type ExecDetail } from '@/api/engineAndMarketApi'
import { formatTime } from '@/api/types'
import './engine.css'
const route = useRoute(), router = useRouter(), detail = ref<ExecDetail>(), loading = ref(false), error = ref(''), chartElement = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined, observer: ResizeObserver | undefined
function dispose() { observer?.disconnect(); chart?.dispose(); chart = undefined }
async function load() {
  loading.value = true; error.value = ''; dispose(); detail.value = undefined
  try {
    detail.value = await engineApi.detail(Number(route.params.id)); await nextTick()
    if (chartElement.value) {
      chart = echarts.init(chartElement.value)
      let elapsed = 0; const offsets = detail.value.nodeLogs.map(n => { const value = elapsed; elapsed += n.costMs; return value })
      chart.setOption({ tooltip: { trigger:'axis', axisPointer: { type:'shadow' } }, grid:{ left:130,right:35,bottom:45,top:20 },
        xAxis:{ type:'value',name:'ms' },yAxis:{ type:'category',inverse:true,data:detail.value.nodeLogs.map(n => n.nodeName) },
        series:[{ name:'开始偏移',type:'bar',stack:'time',itemStyle:{ color:'transparent' },emphasis:{ disabled:true },data:offsets },
          { name:'节点耗时',type:'bar',stack:'time',data:detail.value.nodeLogs.map(n => ({ value:n.costMs,itemStyle:{ color:n.stepStatus === 'ERROR' ? '#c97760' : n.stepStatus === 'SKIPPED' ? '#cbd2c7' : '#7c9d6b' } })) }] })
      observer = new ResizeObserver(() => chart?.resize()); observer.observe(chartElement.value)
    }
  } catch { error.value = '无法加载运行详情，请重试' } finally { loading.value = false }
}
watch(() => route.params.id, load, { immediate:true }); onBeforeUnmount(dispose)
</script>
<template><div v-loading="loading"><div class="engine-heading"><div><span class="eyebrow">EXECUTION TRACE / 节点明细</span><h1>{{ detail?.record.wfName ?? '运行详情' }}</h1><p>{{ detail?.record.execNo }}</p></div><el-button @click="router.push('/engine/records')">返回记录</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button v-if="error" @click="load">重新加载</el-button><template v-if="detail"><section class="engine-panel"><div class="engine-summary"><el-tag :type="detail.record.execStatus === 'SUCCESS' ? 'success' : 'danger'">{{ detail.record.execStatus }}</el-tag><span>{{ detail.record.totalTokens }} Tokens</span><span>{{ detail.record.durationMs }} ms</span><span>{{ formatTime(detail.record.startedAt) }}</span></div><el-alert v-if="detail.record.errorSummary" :title="detail.record.errorSummary" type="error" :closable="false"/><h3>节点耗时瀑布图</h3><div ref="chartElement" class="engine-chart" aria-label="节点耗时瀑布图"/></section><div class="engine-grid"><section class="engine-panel"><h3>初始输入</h3><pre class="engine-pre">{{ JSON.stringify(detail.record.inputPayload,null,2) }}</pre></section><section class="engine-panel"><h3>最终输出</h3><pre class="engine-pre">{{ JSON.stringify(detail.record.finalOutput,null,2) }}</pre></section></div><section class="engine-panel"><h3>主从执行日志</h3><el-table :data="detail.nodeLogs" empty-text="暂无节点日志" row-key="id"><el-table-column type="expand"><template #default="{ row }"><div style="padding:20px"><h4>节点输入</h4><pre class="engine-pre">{{ row.nodeInput ?? '—' }}</pre><h4>节点输出</h4><pre class="engine-pre">{{ row.nodeOutput ?? '—' }}</pre></div></template></el-table-column><el-table-column prop="stepOrder" label="顺序" width="70"/><el-table-column prop="nodeName" label="节点"/><el-table-column prop="nodeType" label="类型"/><el-table-column prop="stepStatus" label="状态"/><el-table-column prop="tokenCost" label="Tokens"/><el-table-column prop="costMs" label="耗时 ms"/></el-table></section></template></div></template>
