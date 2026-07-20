<script lang="ts" setup>
import { t } from '@/i18n'

defineProps<{
  writeTextLoading: boolean
  drawGeneratedLoading: boolean
  starterAssets: { id: string, label: string }[]
  defaultFontId: string
  deviceBusy?: boolean
}>()
const emit = defineEmits<{
  writeText: []
  drawPrompt: []
  drawStarter: [starterId: string]
}>()
const writeTextInput = defineModel<string>('writeTextInput', { default: '你好' })
const drawPromptInput = defineModel<string>('drawPromptInput', { default: '星星' })
</script>

<template>
  <!-- 写字 -->
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.writeDemo') }}
    </view>
    <text v-if="deviceBusy" class="busy-hint">
      {{ t('v2.detail.deviceBusyHint') }}
    </text>
    <text class="hint-text">
      {{ t('v2.detail.defaultFont') }}: {{ defaultFontId }}
    </text>
    <wd-input
      v-model="writeTextInput"
      clearable
      :maxlength="40"
      :placeholder="t('v2.detail.writePlaceholder')"
      :disabled="deviceBusy"
      custom-class="dark-input !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
    />
    <wd-button
      type="primary" round block size="large"
      :loading="writeTextLoading"
      :disabled="deviceBusy"
      custom-class="!h-[88rpx] !text-[30rpx]"
      @click="emit('writeText')"
    >
      {{ deviceBusy ? t('v2.detail.deviceBusyLabel') : writeTextLoading ? t('v2.detail.submitting') : t('v2.detail.startWrite') }}
    </wd-button>
  </view>

  <!-- 画图 -->
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.drawDemo') }}
    </view>
    <text v-if="deviceBusy" class="busy-hint">
      {{ t('v2.detail.deviceBusyHint') }}
    </text>
    <text class="hint-text">
      {{ t('v2.detail.drawDesc') }}
    </text>
    <wd-input
      v-model="drawPromptInput"
      clearable
      :maxlength="60"
      :placeholder="t('v2.detail.drawPlaceholder')"
      :disabled="deviceBusy"
      custom-class="dark-input !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
    />
    <wd-button
      type="primary" round block size="large"
      :loading="drawGeneratedLoading"
      :disabled="deviceBusy"
      custom-class="!h-[88rpx] !text-[30rpx]"
      @click="emit('drawPrompt')"
    >
      {{ deviceBusy ? t('v2.detail.deviceBusyLabel') : drawGeneratedLoading ? t('v2.detail.submitting') : t('v2.detail.generateDraw') }}
    </wd-button>
    <view class="starter-row">
      <wd-button
        v-for="asset in starterAssets"
        :key="asset.id"
        type="info" round plain size="small"
        :disabled="drawGeneratedLoading || deviceBusy"
        custom-class="starter-btn"
        @click="emit('drawStarter', asset.id)"
      >
        {{ asset.label }}
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.hint-text {
  display: block;
  font-size: 24rpx;
  color: var(--dim);
  margin-bottom: 20rpx;
}

/* M18:busy 是等待态非错误态,danger → amber */
.busy-hint {
  display: block;
  font-size: 24rpx;
  color: var(--amber);
  margin-bottom: 12rpx;
}

.starter-row {
  margin-top: 20rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;

  .starter-btn {
    min-width: 120rpx;
  }
}
</style>
