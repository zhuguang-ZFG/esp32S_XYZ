import type { TranscribeResult, VoiceIntent } from '@/api/voice/voice'
import { ref } from 'vue'
import { requestVoiceTicket } from '@/api/voice/voice'
import { t } from '@/i18n'
import { getEnvBaseUrl } from '@/utils'

export type StreamStatus = 'idle' | 'connecting' | 'streaming'

/**
 * 实时流语音指令 composable（M2）。
 *
 * 复用后端 /v1/voice WS（VAD→ASR→LLM→TTS），只取其中的 ASR `transcript` 帧，
 * 忽略 LLM `reply` / TTS `audio` 帧 —— 本模式只做语音转写，不做对话。
 *
 * 流程：requestVoiceTicket → connectSocket(/v1/voice?ticket=…) →
 *   recorder.start({format:'pcm', frameSize}) → onFrameRecorded 推送 PCM 帧 →
 *   收到 transcript 帧实时更新 liveText → 松开 → 用最终文本产出 TranscribeResult →
 *   交给调用方（复用 M1 确认对话框）。
 *
 * 意图解析：本模式不调后端 resolve（WS 只回 transcript），由调用方对最终文本做
 * 轻量前端判断，或直接复用 M1 确认对话框的编辑/切换能力修正。这里给出一个
 * 与 device_gateway.intent 一致的最小前端规则，保证「画X→draw / 写X→write」。
 */
export function useVoiceStream() {
  const status = ref<StreamStatus>('idle')
  // 实时累积的转写文本（边说边显）。
  const liveText = ref('')
  const errorMsg = ref('')

  const recorder = uni.getRecorderManager()
  let socketTask: UniApp.SocketTask | null = null
  let opened = false

  function ensureMicPermission(): Promise<boolean> {
    return new Promise((resolve) => {
      uni.authorize({
        scope: 'scope.record',
        success: () => resolve(true),
        fail: () => resolve(false),
      })
    })
  }

  function buildWsUrl(ticket: string): string {
    const base = getEnvBaseUrl().replace(/\/$/, '')
    const proto = base.startsWith('https') ? 'wss' : 'ws'
    const rest = base.replace(/^https?:\/\//, '')
    return `${proto}://${rest}/v1/voice?ticket=${encodeURIComponent(ticket)}`
  }

  /**
   * 最小前端意图规则（与后端 device_gateway.intent 的中文模式对齐）。
   * 注意：fallback 必须与后端 resolve_voice_task 一致 —— 未知输入默认 write_text，
   * 否则 M1（后端解析）与 M2（前端解析）对同一段语音会给出不同意图，用户困惑。
   */
  function frontendIntent(text: string): VoiceIntent {
    const s = text.trim()
    if (/^(?:归零|回零|home)$/i.test(s))
      return { capability: 'home', params: {}, source: 'voice', explanation: 'frontend: home' }
    if (/^画/.test(s)) {
      const prompt = s.replace(/^画/, '')
      return { capability: 'draw_generated', params: { prompt }, source: 'voice', explanation: 'frontend: draw_generated' }
    }
    if (/^写/.test(s)) {
      const writeText = s.replace(/^写/, '')
      return { capability: 'write_text', params: { text: writeText }, source: 'voice', explanation: 'frontend: write_text' }
    }
    // 与后端 intent.py 的 fallback 对齐：未知输入默认 write_text（用原文作为待写文字）。
    return { capability: 'write_text', params: { text: s }, source: 'voice', explanation: 'frontend: fallback write_text' }
  }

  function cleanupSocket() {
    if (socketTask) {
      socketTask.close({})
      socketTask = null
    }
    opened = false
  }

  recorder.onFrameRecorded((res: { frameBuffer: ArrayBuffer, isLastFrame: boolean }) => {
    if (status.value !== 'streaming' || !socketTask || !opened)
      return
    // 直接把 PCM 帧作为二进制推给 WS（后端按 16-bit mono 16kHz 处理）。
    socketTask.send({ data: res.frameBuffer })
  })

  recorder.onError(() => {
    errorMsg.value = t('voice.recordFailed')
    stop(true)
  })

  async function start(): Promise<boolean> {
    if (status.value !== 'idle')
      return false
    const granted = await ensureMicPermission()
    if (!granted) {
      errorMsg.value = t('voice.micDenied')
      return false
    }
    errorMsg.value = ''
    liveText.value = ''
    status.value = 'connecting'

    let ticket = ''
    try {
      const r = await requestVoiceTicket()
      ticket = r.ticket
    }
    catch {
      errorMsg.value = t('voice.ticketFailed')
      status.value = 'idle'
      return false
    }

    socketTask = uni.connectSocket({ url: buildWsUrl(ticket) }) as unknown as UniApp.SocketTask

    socketTask.onOpen(() => {
      opened = true
      status.value = 'streaming'
      // frameSize 单位为 KB。5KB ≈ 后端 FRAME_BYTES(1024B) 的整数倍，平衡延迟与回调频率。
      recorder.start({ format: 'pcm', sampleRate: 16000, numberOfChannels: 1, frameSize: 5 })
    })

    socketTask.onMessage(({ data }) => {
      try {
        const m = typeof data === 'string' ? JSON.parse(data) : data
        // 只取 transcript 帧；忽略 reply(LLM)/audio(TTS)/status 帧。
        if (m.type === 'transcript' && typeof m.text === 'string')
          liveText.value = m.text
      }
      catch {
        // 二进制/非 JSON 帧（TTS 音频等）直接忽略。
      }
    })

    socketTask.onError(() => {
      errorMsg.value = t('voice.streamError')
      stop(true)
    })

    socketTask.onClose(() => {
      opened = false
    })

    return true
  }

  /**
   * 停止流。abort=true 表示取消（丢弃结果）；否则产出最终 TranscribeResult。
   * @returns 最终转写结果（供确认对话框），取消或空结果时返回 null。
   */
  function stop(abort = false): TranscribeResult | null {
    if (status.value === 'idle')
      return null
    if (status.value === 'streaming')
      recorder.stop()
    cleanupSocket()

    if (abort) {
      status.value = 'idle'
      liveText.value = ''
      return null
    }

    const text = liveText.value.trim()
    status.value = 'idle'
    if (!text) {
      errorMsg.value = t('voice.emptyResult')
      return null
    }
    return { text, intent: frontendIntent(text) }
  }

  function dispose() {
    if (status.value === 'streaming')
      recorder.stop()
    cleanupSocket()
    status.value = 'idle'
  }

  return { status, liveText, errorMsg, start, stop, dispose }
}
