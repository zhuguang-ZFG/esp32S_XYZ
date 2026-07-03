import { CHAT_COMPLETION_TIMEOUT_MS } from '@/config/timeouts'
import { http } from '@/http/request/alova'
import { getBearerToken, getChatBaseUrl } from '@/utils'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export interface ChatCompletionChunk {
  choices: Array<{
    delta: {
      content?: string
    }
    finish_reason?: string
  }>
}

/**
 * 解析 SSE 缓冲区：按行拆分，处理 `data:` 行并通过回调返回增量内容。
 * 返回保留到下次调用的不完整尾行，以及是否已收到结束标记。
 * 两端（微信 onChunkReceived / 非微信 fetch reader）共用此逻辑，避免分叉。
 */
function parseSSEBuffer(
  buffer: string,
  onChunk: (text: string, done: boolean) => void,
): { rest: string, done: boolean } {
  const lines = buffer.split('\n')
  const rest = lines.pop() || '' // 保留不完整的最后一行

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed.startsWith('data:'))
      continue

    const dataStr = trimmed.slice(5).trim()
    if (dataStr === '[DONE]') {
      onChunk('', true)
      return { rest: '', done: true }
    }

    try {
      const data: ChatCompletionChunk = JSON.parse(dataStr)
      const content = data.choices?.[0]?.delta?.content || ''
      const done = !!data.choices?.[0]?.finish_reason
      if (content)
        onChunk(content, done)
      if (done) {
        onChunk('', true)
        return { rest: '', done: true }
      }
    }
    catch {
      // 忽略解析失败的 chunk（可能是被切分的不完整 JSON）
    }
  }
  return { rest, done: false }
}

// #ifdef MP-WEIXIN
/**
 * 微信小程序流式：uni.request(enableChunked) + onChunkReceived 逐块解码。
 */
function startWeixinStream(
  url: string,
  token: string,
  payload: Record<string, unknown>,
  onChunk: (text: string, done: boolean) => void,
  onError?: (errMsg: string) => void,
): { abort: () => void } {
  let buffer = ''
  const requestTask = uni.request({
    url,
    method: 'POST',
    header: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream',
    },
    data: payload,
    timeout: CHAT_COMPLETION_TIMEOUT_MS,
    enableChunked: true,
    success: (res) => {
      if (res.statusCode && res.statusCode !== 200)
        onError?.(`HTTP ${res.statusCode}`)
    },
    fail: (err) => {
      const errMsg = err.errMsg || '未知错误'
      if (onError)
        onError(errMsg)
      else
        onChunk('', true) // 向后兼容：无 onError 时仍通知完成
    },
  })

  ;(requestTask as any).onChunkReceived?.((res: any) => {
    const chunk = new TextDecoder('utf-8').decode(new Uint8Array(res.data))
    buffer += chunk
    const result = parseSSEBuffer(buffer, onChunk)
    buffer = result.rest
  })

  return {
    abort: () => {
      requestTask.abort?.()
    },
  }
}
// #endif

// #ifndef MP-WEIXIN
/**
 * 非微信端（H5 / App）流式：fetch + response.body.getReader() 读取 SSE，
 * 通过 AbortController 支持中断。修复此前非微信端只拿首包、静默无后续 chunk 的问题。
 */
function startFetchStream(
  url: string,
  token: string,
  payload: Record<string, unknown>,
  onChunk: (text: string, done: boolean) => void,
  onError?: (errMsg: string) => void,
): { abort: () => void } {
  const controller = new AbortController()

  ;(async () => {
    try {
      const resp = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : '',
          'Accept': 'text/event-stream',
        },
        body: JSON.stringify(payload),
        signal: controller.signal,
      })
      if (!resp.ok) {
        onError?.(`HTTP ${resp.status}`)
        return
      }
      if (!resp.body) {
        onError?.('response body unavailable')
        return
      }

      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      while (true) {
        const { value, done } = await reader.read()
        if (done)
          break
        buffer += decoder.decode(value, { stream: true })
        const result = parseSSEBuffer(buffer, onChunk)
        buffer = result.rest
        if (result.done)
          return
      }
      onChunk('', true) // 流结束但未见 [DONE]/finish_reason，兜底通知完成
    }
    catch (err) {
      if ((err as Error)?.name === 'AbortError')
        return // 主动中断不算错误
      onError?.((err as Error)?.message || 'stream failed')
    }
  })()

  return {
    abort: () => controller.abort(),
  }
}
// #endif

/**
 * 流式 SSE 对话，通过回调逐字返回内容。
 * 微信端走 uni.request(enableChunked)，非微信端走 fetch + ReadableStream reader。
 */
export function chatCompletionStream(
  messages: ChatMessage[],
  onChunk: (text: string, done: boolean) => void,
  onError?: (errMsg: string) => void,
  model = 'lima-1.3',
  temperature = 0.7,
  max_tokens = 2048,
): { abort: () => void } {
  const baseUrl = getChatBaseUrl(model).replace(/\/$/, '')
  const token = getBearerToken()
  const url = `${baseUrl}/v1/chat/completions`
  const payload: Record<string, unknown> = {
    model,
    messages: messages.filter(m => m.role !== 'system'),
    temperature,
    max_tokens,
    stream: true,
  }

  // #ifdef MP-WEIXIN
  return startWeixinStream(url, token, payload, onChunk, onError)
  // #endif

  // #ifndef MP-WEIXIN
  return startFetchStream(url, token, payload, onChunk, onError)
  // #endif
}

/**
 * 非流式对话（兼容旧版）
 */
export async function chatCompletion(
  messages: ChatMessage[],
  model = 'lima-1.3',
  temperature = 0.7,
  max_tokens = 2048,
): Promise<string> {
  const baseUrl = getChatBaseUrl(model).replace(/\/$/, '')
  const token = getBearerToken()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token)
    headers.Authorization = `Bearer ${token}`

  interface ChatCompletionResponse {
    choices?: Array<{ message?: { content?: string } }>
  }

  const res = await http.Post<ChatCompletionResponse>(
    `${baseUrl}/v1/chat/completions`,
    {
      model,
      messages: messages.filter(m => m.role !== 'system'),
      temperature,
      max_tokens,
    },
    { meta: { ignoreAuth: true, toast: false }, timeout: CHAT_COMPLETION_TIMEOUT_MS, headers },
  )
  return res.choices?.[0]?.message?.content?.trim() || ''
}
