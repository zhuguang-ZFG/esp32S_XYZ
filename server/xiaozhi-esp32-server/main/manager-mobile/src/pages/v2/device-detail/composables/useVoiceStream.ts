import type { Ref } from 'vue'
import { onUnmounted, ref } from 'vue'
import { v2IssueVoiceTicket } from '@/api/v2'
import { buildVoiceWsUrl } from '@/utils'

export interface VoiceStreamState {
  recording: Ref<boolean>
  connecting: Ref<boolean>
  transcript: Ref<string>
  error: Ref<string>
}

const FRAME_SIZE = 1280
const SAMPLE_RATE = 16000

/** M2 realtime voice skeleton: ticket -> WS -> PCM frames -> stop. */
export function useVoiceStream(): VoiceStreamState & {
  startRecording: () => Promise<void>
  stopRecording: () => Promise<void>
} {
  const recording = ref(false)
  const connecting = ref(false)
  const transcript = ref('')
  const error = ref('')

  let socketTask: UniApp.SocketTask | null = null
  let recorder: UniApp.RecorderManager | null = null

  function cleanupSocket() {
    if (!socketTask)
      return
    try { socketTask.close({}) } catch { /* ignore */ }
    socketTask = null
  }

  function cleanupRecorder() {
    if (!recorder)
      return
    try { recorder.stop() } catch { /* ignore */ }
    recorder.onFrameRecorded(() => {})
    recorder.onStop(() => {})
    recorder.onError(() => {})
    recorder = null
  }

  async function connectVoiceWs(): Promise<void> {
    connecting.value = true
    error.value = ''
    try {
      const { ticket } = await v2IssueVoiceTicket()
      const url = buildVoiceWsUrl(ticket)
      await new Promise<void>((resolve, reject) => {
        let opened = false
        socketTask = uni.connectSocket({
          url,
          success: () => {},
          fail: err => reject(new Error(err.errMsg || 'connectSocket failed')),
        })
        socketTask.onOpen(() => {
          opened = true
          resolve()
        })
        socketTask.onError((evt) => {
          if (!opened)
            reject(new Error((evt as { errMsg?: string }).errMsg || 'voice ws error'))
        })
        socketTask.onMessage((msg) => {
          if (typeof msg.data !== 'string')
            return
          try {
            const payload = JSON.parse(msg.data) as { type?: string, text?: string }
            if (payload.type === 'transcript' && payload.text)
              transcript.value = payload.text
          }
          catch { /* ignore */ }
        })
      })
    }
    finally {
      connecting.value = false
    }
  }

  async function startRecording() {
    if (recording.value)
      return
    transcript.value = ''
    await connectVoiceWs()
    recorder = uni.getRecorderManager()
    recorder.onFrameRecorded((res) => {
      if (!socketTask || !recording.value)
        return
      const frame = res.frameBuffer as ArrayBuffer
      if (frame.byteLength > 0)
        socketTask.send({ data: frame })
    })
    recorder.onError((evt) => {
      error.value = (evt as { errMsg?: string }).errMsg || 'recorder error'
      void stopRecording()
    })
    recorder.start({
      format: 'PCM',
      sampleRate: SAMPLE_RATE,
      numberOfChannels: 1,
      frameSize: FRAME_SIZE,
    })
    recording.value = true
  }

  async function stopRecording() {
    if (!recording.value)
      return
    recording.value = false
    cleanupRecorder()
    if (socketTask) {
      try { socketTask.send({ data: 'stop' }) } catch { /* ignore */ }
    }
    cleanupSocket()
  }

  onUnmounted(() => { void stopRecording() })

  return { recording, connecting, transcript, error, startRecording, stopRecording }
}
