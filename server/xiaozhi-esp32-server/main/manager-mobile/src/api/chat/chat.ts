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
 * 流式 SSE 对话，通过回调逐字返回内容
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

  let buffer = ''
  const requestTask = uni.request({
    url: `${baseUrl}/v1/chat/completions`,
    method: 'POST',
    header: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream',
    },
    data: {
      model,
      messages: messages.filter(m => m.role !== 'system'),
      temperature,
      max_tokens,
      stream: true,
    },
    timeout: 120000,
    enableChunked: true,
    success: (res) => {
      // HTTP 错误状态码走 onError 而非静默
      if (res.statusCode && res.statusCode !== 200) {
        onError?.(`HTTP ${res.statusCode}`)
      }
    },
    fail: (err) => {
      const errMsg = err.errMsg || '未知错误'
      if (onError) {
        onError(errMsg)
      }
      else {
        // 向后兼容：无 onError 时仍通过 onChunk 通知完成
        onChunk('', true)
      }
    },
  })

  // #ifdef MP-WEIXIN
  ;(requestTask as any).onChunkReceived?.((res: any) => {
    const chunk = new TextDecoder('utf-8').decode(new Uint8Array(res.data))
    buffer += chunk
    processBuffer()
  })
  // #endif

  // 非微信小程序环境使用定时器轮询（fallback）
  // #ifndef MP-WEIXIN
  const pollTimer: ReturnType<typeof setInterval> | null = null
  // #endif

  function processBuffer() {
    const lines = buffer.split('\n')
    buffer = lines.pop() || '' // 保留不完整的最后一行

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:'))
        continue

      const dataStr = trimmed.slice(5).trim()
      if (dataStr === '[DONE]') {
        onChunk('', true)
        return
      }

      try {
        const data: ChatCompletionChunk = JSON.parse(dataStr)
        const content = data.choices?.[0]?.delta?.content || ''
        const done = !!data.choices?.[0]?.finish_reason
        if (content) {
          onChunk(content, done)
        }
        if (done) {
          onChunk('', true)
          return
        }
      }
      catch {
        // 忽略解析失败的 chunk
      }
    }
  }

  return {
    abort: () => {
      requestTask.abort?.()
      // #ifndef MP-WEIXIN
      if (pollTimer)
        clearInterval(pollTimer)
      // #endif
    },
  }
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

  return new Promise((resolve, reject) => {
    uni.request({
      url: `${baseUrl}/v1/chat/completions`,
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
      },
      data: {
        model,
        messages: messages.filter(m => m.role !== 'system'),
        temperature,
        max_tokens,
      },
      timeout: 120000,
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode}`))
          return
        }
        const data = res.data as any
        const content = data.choices?.[0]?.message?.content?.trim() || ''
        resolve(content)
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '请求失败'))
      },
    })
  })
}
