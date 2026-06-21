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
  <view class="bento-card">
    <view class="task-header">
      <text class="bento-title">
        {{ t('v2.detail.latestTask') }}
      </text>
      <wd-tag
        :type="latestPhase === 'done' ? 'success' : latestPhase === 'failed' ? 'danger' : latestPhase === 'running' ? 'primary' : 'default'"
        size="small" round
        :custom-style="`border-color:${phaseColor};color:${phaseColor}`"
      >
        {{ latestPhase }}
      </wd-tag>
    </view>
    <view v-if="latestProgressPercent !== null" class="progress-section">
      <view class="progress-track">
        <view class="progress-fill" :style="progressBarStyle" />
      </view>
      <text class="progress-label">
        {{ latestProgressLabel }}
      </text>
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
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.progress-section {
  margin-top: 8rpx;
}

.progress-track {
  height: 12rpx;
  background: #edf1f7;
  border-radius: 6rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #336cff;
  border-radius: 6rpx;
  transition: width 0.3s ease;
}

.progress-label {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #65686f;
}
</style>
