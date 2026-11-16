import request, { TOKEN_KEY } from '@/utils/request'

/* ---------------- 会话管理 ---------------- */
export const listConversations = () => request.get('/ai/conversations')
export const listMessages = (id) => request.get(`/ai/conversations/${id}/messages`)
export const removeConversation = (id) => request.delete(`/ai/conversations/${id}`)
export const renameConversation = (id, title) => request.put(`/ai/conversations/${id}`, { title })

/**
 * AI 对话（5.0 P1，SSE）
 *
 * 为什么不用 EventSource / axios：
 *   - EventSource 只能 GET，且不能自定义 Authorization 头；
 *   - axios 在浏览器端拿不到"边下边读"的增量。
 * 所以：fetch + ReadableStream + 手动解析 SSE 帧。
 *
 * 事件协议见 docs/api/09-v5.0-AI对话SSE流式接口文档.md：
 *   meta  -> { model }
 *   delta -> { text }
 *   done  -> { chars }
 *   error -> { msg }
 */
export async function streamChat({ content, conversationId, signal, onMeta, onDelta, onDone }) {
  const token = localStorage.getItem(TOKEN_KEY)
  const resp = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {})
    },
    body: JSON.stringify({ content, conversationId }),
    signal
  })

  if (resp.status === 401) {
    const err = new Error('登录已过期，请重新登录')
    err.code = 401
    throw err
  }
  if (!resp.ok) {
    throw new Error('AI 服务异常（HTTP ' + resp.status + '）')
  }

  // 校验失败（空问题、AI 关闭等）时后端会返回 200 + JSON Result，
  // 还没开始写 SSE，这里直接按业务错误抛，别当成“连接中断”
  const contentType = resp.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const data = await resp.json().catch(() => null)
    const err = new Error(data?.msg || 'AI 服务返回了错误')
    err.code = data?.code
    throw err
  }

  if (!resp.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let sawDone = false

  function handleBlock(block) {
    let event = 'message'
    const data = []

    for (const line of block.split('\n')) {
      const clean = line.replace(/\r$/, '')
      if (clean.startsWith('event:')) event = clean.slice(6).trim()
      else if (clean.startsWith('data:')) data.push(clean.slice(5).trim())
    }
    if (!data.length) return

    let payload = null
    try {
      payload = JSON.parse(data.join('\n'))
    } catch {
      payload = null
    }

    if (event === 'meta') onMeta?.(payload)
    else if (event === 'delta') onDelta?.(payload?.text || '')
    else if (event === 'done') {
      sawDone = true
      onDone?.(payload)
    } else if (event === 'error') {
      throw new Error(payload?.msg || 'AI 出错')
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 帧之间用空行分隔；一次 read 可能读到半帧，也可能读到多帧
      let sep
      while ((sep = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        handleBlock(block)
      }
    }
  } catch (e) {
    // 主动 abort 时 read() 会抛 AbortError，不要和"连接中断"混为一谈
    if (e?.name !== 'AbortError') {
      try { await reader.cancel() } catch { /* 忽略 */ }
    }
    throw e
  }

  if (!sawDone) {
    throw new Error('连接中断，回答可能不完整')
  }
}
