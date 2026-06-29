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
  background: rgba(255, 255, 255, 0.06);
  border-radius: 6rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 6rpx;
  transition: width 0.3s ease;
}

.progress-label {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--muted);
}
</style>
