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
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.detail.writeDemo')" :label="`${t('v2.detail.defaultFont')} ${defaultFontId}`" />
    <view class="mx-[30rpx] mb-[24rpx]">
      <wd-input
        v-model="writeTextInput"
        clearable
        :maxlength="40"
        :placeholder="t('v2.detail.writePlaceholder')"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
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
  </wd-cell-group>

  <!-- 画图 -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.detail.drawDemo')" :label="t('v2.detail.drawDesc')" />
    <view class="mx-[30rpx] mb-[24rpx]">
      <wd-input
        v-model="drawPromptInput"
        clearable
        :maxlength="60"
        :placeholder="t('v2.detail.drawPlaceholder')"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
      />
      <wd-button
        type="primary" block round size="large"
        :loading="drawGeneratedLoading"
        custom-class="!h-[88rpx] !text-[30rpx]"
        @click="emit('drawPrompt')"
      >
        {{ drawGeneratedLoading ? t('v2.detail.submitting') : t('v2.detail.generateDraw') }}
      </wd-button>
      <view class="mt-[16rpx] flex flex-wrap gap-[12rpx]">
        <wd-button
          v-for="asset in starterAssets"
          :key="asset.id"
          type="info" plain round size="small"
          :disabled="drawGeneratedLoading"
          @click="emit('drawStarter', asset.id)"
        >
          {{ asset.label }}
        </wd-button>
      </view>
    </view>
  </wd-cell-group>
</template>
