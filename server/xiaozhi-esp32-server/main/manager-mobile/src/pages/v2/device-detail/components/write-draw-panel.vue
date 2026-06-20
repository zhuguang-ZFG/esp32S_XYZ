<script lang="ts" setup>
import { t } from '@/i18n'

const writeTextInput = defineModel<string>('writeTextInput', { default: '你好' })
const drawPromptInput = defineModel<string>('drawPromptInput', { default: '星星' })

defineProps<{
  writeTextLoading: boolean
  drawGeneratedLoading: boolean
  starterAssets: { id: string; label: string }[]
  defaultFontId: string
}>()

const emit = defineEmits<{
  writeText: []
  drawPrompt: []
  drawStarter: [starterId: string]
}>()
</script>

<template>
  <!-- 写字 -->
  <view class="bento-card">
    <view class="bento-title">{{ t('v2.detail.writeDemo') }}</view>
    <text class="hint-text">{{ t('v2.detail.defaultFont') }}: {{ defaultFontId }}</text>
    <wd-input
      v-model="writeTextInput"
      clearable
      :maxlength="40"
      :placeholder="t('v2.detail.writePlaceholder')"
      custom-class="!bg-[#f5f5f7] !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
    />
    <wd-button
      type="primary" block round size="large"
      :loading="writeTextLoading"
      custom-class="!h-[88rpx] !text-[30rpx]"
      @click="emit('writeText')"
    >
      {{ writeTextLoading ? t('v2.detail.submitting') : t('v2.detail.startWrite') }}
    </wd-button>
  </view>

  <!-- 画图 -->
  <view class="bento-card">
    <view class="bento-title">{{ t('v2.detail.drawDemo') }}</view>
    <text class="hint-text">{{ t('v2.detail.drawDesc') }}</text>
    <wd-input
      v-model="drawPromptInput"
      clearable
      :maxlength="60"
      :placeholder="t('v2.detail.drawPlaceholder')"
      custom-class="!bg-[#f5f5f7] !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
    />
    <wd-button
      type="primary" block round size="large"
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
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.bento-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 8rpx;
}

.hint-text {
  display: block;
  font-size: 24rpx;
  color: #9d9ea3;
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
