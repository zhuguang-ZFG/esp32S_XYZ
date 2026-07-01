import { http } from '@/http/request/alova'
import { getBearerToken, getEnvBaseUrl } from '@/utils'

/** 语音意图解析结果（后端 resolve_voice_task 输出）。 */
export interface VoiceIntent {
  capability: 'draw_generated' | 'write_text' | 'home' | string
  params: Record<string, unknown>
  source: string
  explanation: string
}

export interface TranscribeResult {
  text: string
  intent: VoiceIntent
}

export interface VoiceTicketResponse {
  ticket: string
  expires_in: number
}

/**
 * 按住说话：上传录音文件 → ASR 转写 + 意图解析。
 *
 * 用 uni.uploadFile（alova 不处理 multipart），需手动带 Authorization 头。
 * 后端返回 { code, data: { text, intent } }。
 */
export function transcribeAudio(filePath: string): Promise<TranscribeResult> {
  return new Promise((resolve, reject) => {
    const token = getBearerToken()
    uni.uploadFile({
      url: `${getEnvBaseUrl()}/device/v1/app/voice/transcribe`,
      filePath,
      name: 'audio',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`transcribe failed: ${res.statusCode}`))
          return
        }
        try {
          const parsed = JSON.parse(res.data)
          const data = parsed.data ?? parsed
          resolve(data as TranscribeResult)
        }
        catch (e) {
          reject(e)
        }
      },
      fail: reject,
    })
  })
}

/** 实时流：换取一次性 WS ticket，用于连接 /v1/voice?ticket=…。 */
export function requestVoiceTicket() {
  return http.Post<VoiceTicketResponse>(
    '/device/v1/app/voice/ticket',
    {},
    { meta: { ignoreAuth: false, toast: false, isExposeError: true } },
  )
}
