<script lang="ts" setup>
import { t } from '@/i18n'

defineProps<{
  latestPhase: string
  latestProgressPercent: number | null
  latestProgressLabel: string
  phaseColor: string
  progressBarStyle: string
}>()
</script>

<template>
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.detail.latestTask')">
      <template #value>
        <wd-tag
          :type="latestPhase === 'done' ? 'success' : latestPhase === 'failed' ? 'danger' : latestPhase === 'running' ? 'primary' : 'default'"
          size="mini"
          :custom-style="`border-color:${phaseColor};color:${phaseColor}`"
        >
          {{ latestPhase }}
        </wd-tag>
      </template>
    </wd-cell>
    <view v-if="latestProgressPercent !== null" class="mx-[30rpx] mb-[24rpx]">
      <view class="h-[12rpx] bg-[#edf1f7] rounded-[6rpx] overflow-hidden">
        <view class="h-full bg-[#336cff] rounded-[6rpx] transition-all duration-300" :style="progressBarStyle" />
      </view>
      <wd-text :text="latestProgressLabel" size="22rpx" color="#666" custom-class="!mt-[8rpx]" />
    </view>
  </wd-cell-group>
</template>
