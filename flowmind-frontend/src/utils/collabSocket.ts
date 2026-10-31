import * as Y from 'yjs'
import type { CanvasEdge, CanvasNode, Presence, WorkflowDetail } from '@/api/types'

interface Message { type: string; senderId: number; senderName: string; senderColor: string; payload: Record<string, unknown> }
interface Callbacks {
  graph: (nodes: CanvasNode[], edges: CanvasEdge[]) => void
  presence: (members: Presence[]) => void
  cursor: (cursor: Presence) => void
  status: (connected: boolean, ready: boolean) => void
  refresh: () => void
  membersChanged: (ids: number[]) => void
  revoked: () => void
}
function encode(bytes: Uint8Array) { let value = ''; for (const b of bytes) value += String.fromCharCode(b); return btoa(value) }
function decode(value: string) { return Uint8Array.from(atob(value), c => c.charCodeAt(0)) }

/** A Y.Map per element and per field preserves concurrent edits to different nodes/fields. */
export class CollabSocket {
  private doc = new Y.Doc()
  private nodes = this.doc.getMap<Y.Map<unknown>>('nodes')
  private edges = this.doc.getMap<Y.Map<unknown>>('edges')
  private socket?: WebSocket
  private stopped = false
  private ready = false
  private initialized = false
  private retry = 0
  private reconnectTimer?: ReturnType<typeof setTimeout>
  private lastCursor = 0
  private sessionId = ''
  constructor(private workflowId: number, private token: string, private callbacks: Callbacks) {
    this.doc.on('update', (update: Uint8Array, origin: unknown) => {
      if (origin !== 'remote') this.send('YJS_UPDATE', { update: encode(update) })
      this.emitGraph()
    })
    this.connect()
  }
  private connect() {
    if (this.stopped) return
    const base = import.meta.env.VITE_WS_BASE_URL || `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`
    const socket = new WebSocket(`${base}/ws/collab/${this.workflowId}?token=${encodeURIComponent(this.token)}`)
    this.socket = socket
    socket.onopen = () => {
      this.retry = 0
      if (this.initialized) this.send('YJS_UPDATE', { update: encode(Y.encodeStateAsUpdate(this.doc)) })
    }
    socket.onmessage = event => {
      try { this.receive(JSON.parse(event.data) as Message) } catch { this.callbacks.status(false, false); socket.close(1007, 'Invalid collaboration message') }
    }
    socket.onclose = event => {
      this.ready = false; this.callbacks.status(false, false); this.callbacks.presence([])
      if (!this.stopped && event.code === 1008) this.callbacks.revoked()
      if (!this.stopped && event.code !== 1008 && event.code !== 1007) this.reconnectTimer = setTimeout(() => this.connect(), Math.min(1000 * 2 ** this.retry++, 10000))
    }
    socket.onerror = () => this.callbacks.status(false, false)
  }
  private receive(message: Message) {
    const payload = message.payload
    if (message.type === 'CONNECTED') this.sessionId = String(payload.sessionId)
    if (message.type === 'GRAPH_INIT') {
      const detail = payload as unknown as WorkflowDetail
      if (!this.initialized) this.setGraph(detail.nodes, detail.edges)
      this.initialized = true; this.ready = true; this.callbacks.status(true, true)
    } else if (message.type === 'SYNC_REQUEST') {
      if (this.initialized) this.send('YJS_UPDATE', { update: encode(Y.encodeStateAsUpdate(this.doc)), targetSessionId: payload.targetSessionId })
    } else if (message.type === 'YJS_UPDATE') {
      Y.applyUpdate(this.doc, decode(String(payload.update)), 'remote')
      this.initialized = true; this.ready = true; this.callbacks.status(true, true)
    } else if (message.type === 'PRESENCE_SYNC') {
      this.callbacks.presence(message.payload as unknown as Presence[])
    } else if (message.type === 'CURSOR_MOVE') {
      this.callbacks.cursor({ userId: message.senderId, realName: message.senderName, color: message.senderColor, x: Number(payload.x), y: Number(payload.y) })
    } else if (message.type === 'WORKFLOW_REFRESH') this.callbacks.refresh()
    else if (message.type === 'MEMBERS_CHANGED') this.callbacks.membersChanged(payload.collaboratorIds as number[])
    else if (message.type === 'GRAPH_DELTA') {
      if (Array.isArray(payload.nodes) && Array.isArray(payload.edges)) this.setGraph(payload.nodes as CanvasNode[], payload.edges as CanvasEdge[])
    }
  }
  private emitGraph() {
    this.callbacks.graph([...this.nodes.values()].map(value => value.toJSON() as CanvasNode), [...this.edges.values()].map(value => value.toJSON() as CanvasEdge))
  }
  private updateMap<T extends { elementKey: string }>(map: Y.Map<Y.Map<unknown>>, values: T[]) {
    const ids = new Set(values.map(value => value.elementKey))
    for (const key of map.keys()) if (!ids.has(key)) map.delete(key)
    for (const value of values) {
      let entry = map.get(value.elementKey)
      if (!entry) { entry = new Y.Map<unknown>(); map.set(value.elementKey, entry) }
      for (const [key, field] of Object.entries(value)) {
        if (key === 'id' || field === undefined) continue
        if (JSON.stringify(entry.get(key)) !== JSON.stringify(field)) entry.set(key, JSON.parse(JSON.stringify(field)))
      }
    }
  }
  setGraph(nodes: CanvasNode[], edges: CanvasEdge[]) {
    this.doc.transact(() => { this.updateMap(this.nodes, nodes); this.updateMap(this.edges, edges) }, 'local')
  }
  cursor(x: number, y: number) {
    const now = performance.now()
    if (!this.ready || now - this.lastCursor < 40) return
    this.lastCursor = now; this.send('CURSOR_MOVE', { x, y })
  }
  private send(type: string, payload: Record<string, unknown>) {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify({ type, workflowId: this.workflowId, payload, timestamp: Date.now(), sessionId: this.sessionId }))
  }
  close() {
    this.stopped = true; clearTimeout(this.reconnectTimer); this.socket?.close(); this.doc.destroy()
  }
}
