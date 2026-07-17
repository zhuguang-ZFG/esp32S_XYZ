<script lang="ts" setup>
import { t } from '@/i18n'
import { useVoiceStream } from '../composables/useVoiceStream'

defineProps<{ deviceBusy?: boolean }>()
const { recording, connecting, transcript, error, startRecording, stopRecording } = useVoiceStream()

async function onTouchStart() {
  if (connecting.value)
    return
  await startRecording()
}

async function onTouchEnd() {
  await stopRecording()
}
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
      custom-class="!h-[88rpx] !text-[30rpx]"
      @touchstart.prevent="onTouchStart"
      @touchend.prevent="onTouchEnd"
      @touchcancel.prevent="onTouchEnd"
    >
      {{ recording ? t('v2.detail.voiceStreamRecording') : t('v2.detail.voiceStreamHold') }}
    </wd-button>
    <text v-if="transcript" class="transcript">{{ transcript }}</text>
    <text v-if="error" class="error-text">{{ error }}</text>
  </view>
</template>

<style lang="scss" scoped>
.bento-card {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: var(--r);
  padding: 28rpx;
}
.bento-title { font-size: 30rpx; font-weight: 600; margin-bottom: 12rpx; }
.hint-text { display: block; color: var(--muted); font-size: 24rpx; margin-bottom: 20rpx; }
.transcript { display: block; margin-top: 16rpx; color: var(--text); font-size: 28rpx; }
.error-text { display: block; margin-top: 12rpx; color: #f87171; font-size: 24rpx; }
</style>
