<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { nodeTypes, type CanvasNode } from '@/api/types'
const props = defineProps<{ data: CanvasNode; selected?: boolean }>()
const spec = computed(() => nodeTypes.find(item => item.type === props.data.nodeType)!)
</script>
<template><div :class="['flow-node', { selected }]" :style="{ '--node-color': spec.color }"><Handle v-if="data.nodeType !== 'INPUT'" type="target" :position="Position.Left"/><div class="node-type-label"><span>{{ spec.type.slice(0, 2) }}</span>{{ spec.label }}</div><strong>{{ data.labelName }}</strong><small>{{ spec.hint }}</small><template v-if="data.nodeType === 'CONDITION'"><Handle id="true" type="source" :position="Position.Right" :style="{ top: '40%' }"/><span class="handle-label true-label">是</span><Handle id="false" type="source" :position="Position.Right" :style="{ top: '75%' }"/><span class="handle-label false-label">否</span></template><Handle v-else-if="data.nodeType !== 'OUTPUT'" id="default" type="source" :position="Position.Right"/></div></template>
