<script lang="ts" setup>
import { onUnmounted, ref, watch } from 'vue'
import { t } from '@/i18n'
import { useVoiceStream } from '../composables/useVoiceStream'

defineProps<{ deviceBusy?: boolean }>()
const { recording, connecting, transcript, error, startRecording, stopRecording } = useVoiceStream()

// M17:录音中持续视觉(计时) + 松手后"识别中"过渡
const recordSeconds = ref(0)
const transcribing = ref(false)
let recordTimer: ReturnType<typeof setInterval> | null = null
let transcribeTimer: ReturnType<typeof setTimeout> | null = null

function clearRecordTimer() {
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
}
function clearTranscribeTimer() {
  if (transcribeTimer) {
    clearTimeout(transcribeTimer)
    transcribeTimer = null
  }
}

watch(recording, (val) => {
  if (val) {
    recordSeconds.value = 0
    clearRecordTimer()
    recordTimer = setInterval(() => {
      recordSeconds.value += 1
    }, 1000)
  }
  else {
    clearRecordTimer()
  }
})

// 识别结果/错误到达时结束"识别中"过渡
watch([transcript, error], ([tr, err]) => {
  if (tr || err) {
    transcribing.value = false
    clearTranscribeTimer()
  }
})

async function onTouchStart() {
  if (connecting.value)
    return
  try {
    await startRecording()
  }
  catch (e) {
    // MP-4:connectVoiceWs 失败（ticket/WS）不再抛成未捕获 rejection，写入 error 提示用户
    const err = e as { errMsg?: string, message?: string }
    error.value = err?.errMsg || err?.message || String(e)
  }
}

async function onTouchEnd() {
  const wasRecording = recording.value
  await stopRecording()
  if (wasRecording && !transcript.value && !error.value) {
    transcribing.value = true
    clearTranscribeTimer()
    // 8s 未出结果则退出过渡态,避免永久"识别中"
    transcribeTimer = setTimeout(() => {
      transcribing.value = false
    }, 8000)
  }
}

onUnmounted(() => {
  clearRecordTimer()
  clearTranscribeTimer()
})
</script>

<template>
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.voiceStreamTitle') }}
    </view>
    <text class="hint-text">
      {{ t('v2.detail.voiceStreamHint') }}
    </text>
    <wd-button
      type="primary"
      round
      block
      size="large"
      :loading="connecting"
      :disabled="deviceBusy"
      :custom-style="recording ? 'background: var(--danger) !important; border-color: var(--danger) !important;' : ''"
      custom-class="!h-[88rpx] !text-[30rpx]"
      @touchstart.prevent="onTouchStart"
      @touchend.prevent="onTouchEnd"
      @touchcancel.prevent="onTouchEnd"
    >
      {{ recording ? `${t('v2.detail.voiceStreamRecording')} ${recordSeconds}s` : t('v2.detail.voiceStreamHold') }}
    </wd-button>
    <view v-if="transcribing" class="transcribing-row">
      <wd-loading size="28rpx" />
      <text class="transcribing-text">{{ t('v2.detail.voiceStreamTranscribing') }}</text>
    </view>
    <text v-if="transcript" class="transcript">{{ transcript }}</text>
    <text v-if="error" class="error-text">{{ error }}</text>
  </view>
</template>

<style lang="scss" scoped>
.hint-text { display: block; color: var(--muted); font-size: 24rpx; margin-bottom: 20rpx; }
.transcribing-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}
.transcribing-text { color: var(--muted); font-size: 24rpx; }
.transcript { display: block; margin-top: 16rpx; color: var(--text); font-size: 28rpx; }
.error-text { display: block; margin-top: 12rpx; color: var(--danger); font-size: 24rpx; }
</style>
