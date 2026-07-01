<script lang="ts" setup>
import type { VoiceIntent } from '@/api/voice/voice'
import { computed } from 'vue'
import { t } from '@/i18n'
import { useVoiceCommand } from '../composables/useVoiceCommand'

const props = defineProps<{
  deviceId: string
}>()
const emit = defineEmits<{
  dispatched: [taskId: string, capability: string]
  error: [msg: string]
}>()

const {
  status,
  result,
  errorMsg,
  editableText,
  startRecording,
  stopRecording,
  cancelRecording,
  confirm,
  reset,
} = useVoiceCommand(() => props.deviceId)

// 是否显示确认对话框
const showConfirm = computed(() => status.value === 'confirming')

// 当前意图（可被一键切换修改）
const currentCapability = computed<string>(() => result.value?.intent.capability || '')

// 意图的人类可读描述
function intentLabel(intent: VoiceIntent | null): string {
  if (!intent)
    return ''
  const cap = intent.capability
  const text = editableText.value
  if (cap === 'draw_generated')
    return `${t('voice.targetDraw')} · ${t('voice.actionDraw')}${text}`
  if (cap === 'write_text')
    return `${t('voice.targetWrite')} · ${t('voice.actionWrite')}${text}`
  if (cap === 'home')
    return t('voice.actionHome')
  return `${cap}`
}

// 是否可在绘图机/写字机间切换（仅 draw/write 意图）
const canSwitch = computed(() => ['draw_generated', 'write_text'].includes(currentCapability.value))

// 一键切换绘图机 ↔ 写字机（修正误判）
function toggleCapability() {
  if (!result.value)
    return
  const cap = result.value.intent.capability
  if (cap === 'draw_generated')
    result.value.intent.capability = 'write_text'
  else if (cap === 'write_text')
    result.value.intent.capability = 'draw_generated'
}

// 确认派发
async function onConfirm() {
  try {
    const r = await confirm()
    if (r)
      emit('dispatched', r.taskId, r.capability)
  }
  catch (e: any) {
    emit('error', errorMsg.value || String(e))
  }
}

const buttonLabel = computed(() => {
  switch (status.value) {
    case 'recording':
      return t('voice.recording')
    case 'transcribing':
      return t('voice.transcribing')
    case 'dispatching':
      return t('voice.dispatching')
    default:
      return t('voice.holdToSpeak')
  }
})

const isBusy = computed(() => ['transcribing', 'dispatching'].includes(status.value))
</script>

<template>
  <view class="bento-card">
    <view class="bento-title">
      {{ t('voice.title') }}
    </view>
    <text class="hint-text">
      {{ t('voice.hint') }}
    </text>

    <!-- 按住说话按钮 -->
    <wd-button
      type="primary" round block size="large"
      :loading="isBusy"
      :disabled="isBusy"
      custom-class="!h-[96rpx] !text-[30rpx]"
      @touchstart="startRecording"
      @touchend="stopRecording"
      @touchcancel="cancelRecording"
    >
      {{ buttonLabel }}
    </wd-button>

    <text v-if="errorMsg && status === 'idle'" class="error-text">
      {{ errorMsg }}
    </text>
  </view>

  <!-- 确认对话框（核心安全关卡）-->
  <wd-popup
    v-model="showConfirm"
    position="center"
    custom-style="width: 90%; max-width: 400px; border-radius: 16px;"
    safe-area-inset-bottom
    @close="reset"
  >
    <view>
      <view class="box-border w-full flex items-center justify-between border-b-[2rpx] border-[var(--border)] p-[32rpx_32rpx_24rpx]">
        <text class="w-full text-center text-[32rpx] text-[var(--text)] font-semibold">
          {{ t('voice.confirmTitle') }}
        </text>
      </view>

      <view class="p-[32rpx]">
        <!-- 识别文本（可编辑）-->
        <view class="mb-[28rpx]">
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            {{ t('voice.recognizedText') }}
          </text>
          <input
            v-model="editableText"
            class="box-border h-[80rpx] w-full border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[16rpx_20rpx] text-[28rpx] text-[var(--text)] leading-[1.4] outline-none focus:border-[#2dd4a7] focus:bg-[var(--surface)] placeholder:text-[var(--dim)]"
            type="text" :placeholder="t('voice.editTextPlaceholder')"
          >
        </view>

        <!-- 解析意图 -->
        <view class="mb-[28rpx]">
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            {{ t('voice.parsedIntent') }}
          </text>
          <view class="rounded-[12rpx] bg-[#14181f] p-[20rpx]">
            <text class="text-[28rpx] text-[#2dd4a7] font-medium">
              {{ intentLabel(result?.intent ?? null) }}
            </text>
          </view>
        </view>

        <!-- 一键切换绘图机 ↔ 写字机 -->
        <view v-if="canSwitch" class="mb-[8rpx]">
          <wd-button
            type="info" plain round size="small"
            custom-class="!text-[24rpx]"
            @click="toggleCapability"
          >
            {{ currentCapability === 'draw_generated' ? t('voice.switchToWrite') : t('voice.switchToDraw') }}
          </wd-button>
        </view>
      </view>

      <view class="flex gap-[16rpx] border-t-[2rpx] border-[var(--border)] p-[24rpx_32rpx_32rpx]">
        <wd-button type="info" custom-class="flex-1" @click="reset">
          {{ t('voice.cancel') }}
        </wd-button>
        <wd-button
          type="primary" custom-class="flex-1"
          :loading="status === 'dispatching'"
          @click="onConfirm"
        >
          {{ t('voice.confirmDispatch') }}
        </wd-button>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.bento-card {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: var(--r);
  padding: 28rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(24rpx);
}

.bento-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8rpx;
}

.hint-text {
  display: block;
  font-size: 24rpx;
  color: var(--dim);
  margin-bottom: 20rpx;
}

.error-text {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--danger);
}
</style>
