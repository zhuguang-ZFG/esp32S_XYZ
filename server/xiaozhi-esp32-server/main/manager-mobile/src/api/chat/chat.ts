import { getEnvBaseUrl } from '@/utils'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export interface ChatCompletionRequest {
  model: string
  messages: ChatMessage[]
  temperature?: number
  max_tokens?: number
}

export interface ChatCompletionResponse {
  choices: Array<{
    message: ChatMessage
    finish_reason: string
  }>
}

function getBearerToken(): string | null {
  const rawToken = uni.getStorageSync('token') || ''
  try {
    const parsed = JSON.parse(rawToken)
    return parsed.token || rawToken
  } catch {
    return rawToken
  }
}

export async function chatCompletion(
  messages: ChatMessage[],
  model = 'lima-1.3',
  temperature = 0.7,
  max_tokens = 2048,
): Promise<string> {
  const baseUrl = getEnvBaseUrl().replace(/\/$/, '')
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
        const data = res.data as ChatCompletionResponse
        const content = data.choices?.[0]?.message?.content?.trim() || ''
        resolve(content)
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '请求失败'))
      },
    })
  })
}
