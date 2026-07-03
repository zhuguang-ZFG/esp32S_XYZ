import { ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { getAudioDownloadId } from '@/api/voiceprint'
import { t } from '@/i18n'

/**
 * 声纹页音频试听（P3.1 从 index.vue 提取）。
 *
 * 独立管理 InnerAudioContext 生命周期与当前播放项，避免与 CRUD 逻辑纠缠。
 * 组件卸载 / 弹窗关闭时调用 stopAudio 释放实例。
 */
export function useAudioPlayer() {
  const toast = useToast()
  const audioRef = ref<UniApp.InnerAudioContext | null>(null)
  const playingAudioId = ref<string>('')

  function stopAudio() {
    if (audioRef.value) {
      audioRef.value.stop()
      audioRef.value.destroy()
      audioRef.value = null
    }
    playingAudioId.value = ''
  }

  async function playAudio(audioId: string, event: Event) {
    event.stopPropagation() // 阻止事件冒泡，防止关闭下拉框

    if (!audioId) {
      toast.warning(t('voiceprint.audioNotExist'))
      return
    }

    // 正在播放同一个音频 → 停止（toggle）
    if (playingAudioId.value === audioId) {
      stopAudio()
      return
    }

    stopAudio()

    try {
      playingAudioId.value = audioId
      const audioMeta = await getAudioDownloadId(audioId)

      if (!audioMeta.url) {
        toast.error(t('voiceprint.getAudioFailed'))
        playingAudioId.value = ''
        return
      }

      audioRef.value = uni.createInnerAudioContext()
      audioRef.value.src = audioMeta.url
      audioRef.value.autoplay = true

      audioRef.value.onEnded(() => {
        playingAudioId.value = ''
      })
      audioRef.value.onError((error) => {
        console.error('audio play error', error)
        toast.error(t('voiceprint.audioPlayFailed'))
        playingAudioId.value = ''
      })
    }
    catch (error) {
      toast.error(t('voiceprint.audioPlayFailed'))
      playingAudioId.value = ''
    }
  }

  return {
    playingAudioId,
    playAudio,
    stopAudio,
  }
}
