import { computed, ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { t } from '@/i18n'
import { buildProvisioningAudioDataUri, estimateDurationSeconds } from './afskAudio'

interface WiFiNetwork {
  ssid: string
  rssi: number
  authmode: number
  channel: number
}

/**
 * 超声波配网的音频生成与播放（P3.1 从 ultrasonic-config.vue 提取）。
 *
 * DSP 编码委托给 afskAudio.ts（纯函数），此处只负责 InnerAudioContext 生命周期、
 * 播放状态与 toast 反馈。getter 传入当前网络/密码，随 props 变化。
 */
export function useUltrasonicAudio(selectedNetwork: () => WiFiNetwork | null, password: () => string) {
  const toast = useToast()

  const generating = ref(false)
  const playing = ref(false)
  const audioGenerated = ref(false)
  const autoLoop = ref(true)
  const audioFilePath = ref('')
  const audioContext = ref<any>(null)

  const canGenerate = computed(() => {
    const net = selectedNetwork()
    if (!net)
      return false
    if (net.authmode > 0 && !password())
      return false
    return true
  })

  const audioLengthText = computed(() => {
    const net = selectedNetwork()
    if (!net)
      return '0秒'
    const duration = estimateDurationSeconds(net.ssid, password())
    return `${t('deviceConfig.about')}${duration}${t('deviceConfig.seconds')}`
  })

  async function generateAndPlay() {
    const net = selectedNetwork()
    if (!canGenerate.value || !net)
      return

    generating.value = true
    try {
      const dataUri = buildProvisioningAudioDataUri(net.ssid, password())
      // data URI 前缀固定 "data:audio/wav;base64,"，超过 1MB 视为过大
      const base64Len = dataUri.length - 'data:audio/wav;base64,'.length
      if (base64Len > 1024 * 1024)
        throw new Error(t('deviceConfig.audioFileTooLarge'))

      audioFilePath.value = dataUri
      audioGenerated.value = true
      toast.success(t('deviceConfig.soundWaveGenerationSuccess'))

      setTimeout(async () => {
        await playAudio()
      }, 800)
    }
    catch (error: any) {
      console.error(`${t('deviceConfig.audioGenerationFailed')}:`, error)
      toast.error(`${t('deviceConfig.soundWaveGenerationFailed')}: ${error?.message || error}`)
    }
    finally {
      generating.value = false
    }
  }

  async function playAudio() {
    if (!audioFilePath.value) {
      toast.error(t('deviceConfig.pleaseGenerateAudioFirst'))
      return
    }
    try {
      await cleanupAudio()
      await new Promise(resolve => setTimeout(resolve, 200))
      playing.value = true

      const innerAudioContext = uni.createInnerAudioContext()
      audioContext.value = innerAudioContext
      innerAudioContext.src = audioFilePath.value
      innerAudioContext.loop = autoLoop.value
      innerAudioContext.volume = 0.8
      innerAudioContext.autoplay = false

      innerAudioContext.onPlay(() => {
        toast.success(t('deviceConfig.startPlayingConfigSoundWave'))
      })
      innerAudioContext.onEnded(() => {
        if (!autoLoop.value) {
          playing.value = false
          cleanupAudio()
        }
      })
      innerAudioContext.onError((error: any) => {
        console.error(`${t('deviceConfig.audioPlaybackFailed')}:`, error)
        playing.value = false
        let errorMsg = t('deviceConfig.audioPlaybackFailed')
        if (error.errCode === -99)
          errorMsg = t('deviceConfig.audioResourceBusy')
        else if (error.errCode === 10004)
          errorMsg = t('deviceConfig.audioFormatNotSupported')
        else if (error.errCode === 10003)
          errorMsg = t('deviceConfig.audioFileError')
        toast.error(errorMsg)
        cleanupAudio()
      })
      innerAudioContext.onStop(() => {
        playing.value = false
      })

      setTimeout(() => {
        if (audioContext.value)
          audioContext.value.play()
      }, 300)
    }
    catch (error: any) {
      console.error(`${t('deviceConfig.audioPlaybackError')}:`, error)
      playing.value = false
      await cleanupAudio()
      toast.error(`${t('deviceConfig.playbackFailed')}: ${error?.message}`)
    }
  }

  async function cleanupAudio() {
    if (audioContext.value) {
      try {
        audioContext.value.pause()
        audioContext.value.destroy()
      }
      catch {
        // ignore cleanup errors
      }
      finally {
        audioContext.value = null
      }
    }
  }

  async function stopAudio() {
    playing.value = false
    await cleanupAudio()
    toast.success(t('deviceConfig.stoppedPlaying'))
  }

  return {
    generating,
    playing,
    audioGenerated,
    autoLoop,
    canGenerate,
    audioLengthText,
    generateAndPlay,
    playAudio,
    stopAudio,
  }
}
