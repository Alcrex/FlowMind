export interface SseEvent { event: string; data: string }
/** Incremental UTF-8 SSE parser; chunks may split characters, CRLFs or event frames. */
export async function readSse(response: Response, onEvent: (event: SseEvent) => void): Promise<void> {
  if (!response.body) throw new Error('服务器未返回流式响应')
  const reader = response.body.getReader(), decoder = new TextDecoder()
  let buffer = '', event = 'message', data: string[] = []
  const line = (value: string) => {
    if (value === '') {
      if (data.length) onEvent({ event, data: data.join('\n') })
      event = 'message'; data = []; return
    }
    if (value.startsWith(':')) return
    const colon = value.indexOf(':'), field = colon < 0 ? value : value.slice(0, colon)
    let content = colon < 0 ? '' : value.slice(colon + 1)
    if (content.startsWith(' ')) content = content.slice(1)
    if (field === 'event') event = content
    if (field === 'data') data.push(content)
  }
  try {
    while (true) {
      const chunk = await reader.read()
      buffer += chunk.done ? decoder.decode() : decoder.decode(chunk.value, { stream: true })
      let end: number
      while ((end = buffer.indexOf('\n')) >= 0) { line(buffer.slice(0, end).replace(/\r$/, '')); buffer = buffer.slice(end + 1) }
      if (chunk.done) { if (buffer) line(buffer.replace(/\r$/, '')); line(''); break }
    }
  } catch (error) { await reader.cancel().catch(() => undefined); throw error }
  finally { reader.releaseLock() }
}
