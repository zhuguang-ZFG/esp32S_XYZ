<script lang="ts" setup>
import { t } from '@/i18n'

defineProps<{
  writeTextLoading: boolean
  drawGeneratedLoading: boolean
  starterAssets: { id: string, label: string }[]
  defaultFontId: string
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
    <text class="hint-text">
      {{ t('v2.detail.defaultFont') }}: {{ defaultFontId }}
    </text>
    <wd-input
      v-model="writeTextInput"
      clearable
      :maxlength="40"
      :placeholder="t('v2.detail.writePlaceholder')"
      custom-class="!bg-[#14181f] !text-[#f0f4f8] !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
    />
    <wd-button
      type="primary" round block size="large"
      :loading="writeTextLoading"
      custom-class="!h-[88rpx] !text-[30rpx]"
      @click="emit('writeText')"
    >
      {{ writeTextLoading ? t('v2.detail.submitting') : t('v2.detail.startWrite') }}
    </wd-button>
  </view>

  <!-- 画图 -->
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.drawDemo') }}
    </view>
    <text class="hint-text">
      {{ t('v2.detail.drawDesc') }}
    </text>
    <wd-input
      v-model="drawPromptInput"
      clearable
      :maxlength="60"
      :placeholder="t('v2.detail.drawPlaceholder')"
      custom-class="!bg-[#14181f] !text-[#f0f4f8] !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
    />
    <wd-button
      type="primary" round block size="large"
      :loading="drawGeneratedLoading"
      custom-class="!h-[88rpx] !text-[30rpx]"
      @click="emit('drawPrompt')"
    >
      {{ drawGeneratedLoading ? t('v2.detail.submitting') : t('v2.detail.generateDraw') }}
    </wd-button>
    <view class="starter-row">
      <wd-button
        v-for="asset in starterAssets"
        :key="asset.id"
        type="info" plain round size="small"
        :disabled="drawGeneratedLoading"
        custom-class="starter-btn"
        @click="emit('drawStarter', asset.id)"
      >
        {{ asset.label }}
      </wd-button>
    </view>
  </view>
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
