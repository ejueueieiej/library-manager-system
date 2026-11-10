import request, { TOKEN_KEY } from '@/utils/request'

/**
 * 单轮问答（5.0 P0，非流式）
 *
 * 两个和普通接口不一样的地方：
 *   1. timeout 单独放大到 60s —— axios 实例默认 10s，模型生成经常超过；
 *   2. silent: true —— 错误提示由页面渲染在对话里，避免和全局 ElMessage 弹两次。
 */
export function chat(content) {
  return request.post('/ai/chat', { content }, { timeout: 60000, silent: true })
}

/**
 * 流式问答（5.0 P1，SSE）
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
export async function streamChat({ content, signal, onMeta, onDelta, onDone }) {
  const token = localStorage.getItem(TOKEN_KEY)
  const resp = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {})
    },
    body: JSON.stringify({ content }),
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
