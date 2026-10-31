import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { CanvasEdge, CanvasNode, Presence, Workflow } from '@/api/types'
export const useCanvasStore = defineStore('canvas', () => {
  const workflow = ref<Workflow | null>(null)
  const nodes = ref<CanvasNode[]>([]), edges = ref<CanvasEdge[]>([])
  const members = ref<Presence[]>([])
  const connected = ref(false), dirty = ref(false)
  function reset() { workflow.value = null; nodes.value = []; edges.value = []; members.value = []; connected.value = false; dirty.value = false }
  return { workflow, nodes, edges, members, connected, dirty, reset }
})
