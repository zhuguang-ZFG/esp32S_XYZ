import type { TranscribeResult } from '@/api/voice/voice'
import { ref, watch } from 'vue'
import { transcribeAudio } from '@/api/voice/voice'
import { v2SubmitTask } from '@/api/v2'
import { t } from '@/i18n'

export type VoiceStatus = 'idle' | 'recording' | 'transcribing' | 'confirming' | 'dispatching'

/**
 * 按住说话语音指令 composable。
 *
 * 状态机：idle → recording → transcribing → confirming →（用户确认）→ dispatching → idle
 * 录音 → 转写 → 暂存结果 → 用户在确认对话框编辑/切换 → confirm() 派发（复用 v2SubmitTask）。
 * 确认对话框是强制人工关卡，避免误识别直接驱动物理机器。
 *
 * @param deviceId 返回当前目标设备 id 的 getter（device-detail 单设备场景）。
 */
export function useVoiceCommand(deviceId: () => string) {
  const status = ref<VoiceStatus>('idle')
  const result = ref<TranscribeResult | null>(null)
  const errorMsg = ref('')
  // 可编辑文本：确认对话框里用户可纠正 ASR 识别错误。
  const editableText = ref('')

  // 转写结果到达后，同步初始化可编辑文本。
  watch(result, (next) => {
    editableText.value = next?.text ?? ''
  })

  const recorder = uni.getRecorderManager()

  recorder.onStop(async (res: { tempFilePath: string }) => {
    // 用户可能在录音中途取消：仅在 transcribing 态处理结果。
    if (status.value !== 'transcribing')
      return
    try {
      const r = await transcribeAudio(res.tempFilePath)
      if (!r?.text?.trim()) {
        errorMsg.value = t('voice.emptyResult')
        status.value = 'idle'
        return
      }
      result.value = r
      status.value = 'confirming'
    }
    catch {
      errorMsg.value = t('voice.transcribeFailed')
      status.value = 'idle'
    }
  })

  recorder.onError(() => {
    errorMsg.value = t('voice.recordFailed')
    status.value = 'idle'
  })

  function ensureMicPermission(): Promise<boolean> {
    return new Promise((resolve) => {
      uni.authorize({
        scope: 'scope.record',
        success: () => resolve(true),
        fail: () => resolve(false),
      })
    })
  }

  async function startRecording() {
    if (status.value !== 'idle')
      return
    const granted = await ensureMicPermission()
    if (!granted) {
      errorMsg.value = t('voice.micDenied')
      return
    }
    errorMsg.value = ''
    result.value = null
    // WAV 16kHz 16-bit mono，与后端 ASR 期望一致。
    recorder.start({ format: 'wav', sampleRate: 16000, numberOfChannels: 1 })
    status.value = 'recording'
  }

  function stopRecording() {
    if (status.value !== 'recording')
      return
    status.value = 'transcribing'
    recorder.stop()
  }

  function cancelRecording() {
    if (status.value === 'recording')
      recorder.stop()
    status.value = 'idle'
    result.value = null
    errorMsg.value = ''
  }

  /** 按 capability 从编辑后的文本构建派发参数。 */
  function buildParams(capability: string): Record<string, unknown> {
    const text = editableText.value.trim()
    if (capability === 'draw_generated')
      return { prompt: text }
    if (capability === 'write_text')
      return { text }
    // home / 其他控制类：沿用解析出的原始参数（通常为空）。
    return result.value?.intent.params ?? {}
  }

  /**
   * 确认派发。读取当前（可能被编辑/切换后的）意图与文本，调用 v2SubmitTask。
   * 成功返回 { taskId, capability }；失败设置 errorMsg 并抛出。
   */
  async function confirm(): Promise<{ taskId: string, capability: string } | null> {
    if (!result.value || status.value !== 'confirming')
      return null
    const id = deviceId()
    if (!id) {
      errorMsg.value = t('voice.noDevice')
      throw new Error('no device')
    }
    const capability = result.value.intent.capability
    status.value = 'dispatching'
    try {
      const r = await v2SubmitTask(id, capability, buildParams(capability))
      status.value = 'idle'
      result.value = null
      return { taskId: r.taskId, capability }
    }
    catch (e) {
      errorMsg.value = t('voice.dispatchFailed')
      status.value = 'confirming'
      throw e
    }
  }

  function reset() {
    status.value = 'idle'
    result.value = null
    errorMsg.value = ''
  }

  /**
   * 接入外部产出的转写结果（如实时流 M2），进入同一确认对话框流程。
   * 复用本 composable 的编辑/切换/派发逻辑，避免两套确认 UI。
   */
  function beginConfirm(next: TranscribeResult) {
    if (!next?.text?.trim())
      return
    result.value = next
    status.value = 'confirming'
  }

  return {
    status,
    result,
    errorMsg,
    editableText,
    startRecording,
    stopRecording,
    cancelRecording,
    confirm,
    beginConfirm,
    reset,
  }
}
