<script lang="ts" setup>
import { computed } from 'vue'
import { t } from '@/i18n'

const props = defineProps<{
  latestPhase: string
  latestProgressPercent: number | null
  latestProgressLabel: string
  phaseColor: string
  progressBarStyle: string
}>()

const emit = defineEmits<{
  // S1: 失败后引导用户去写画区重新提交（无服务端重放 id，只能导航到输入区）
  retry: []
}>()

// M4:phase 表驱动本地化（未知枚举回退原文）
const PHASE_KEYS: Record<string, string> = {
  'idle': 'v2.detail.phaseIdle',
  'running': 'v2.detail.phaseRunning',
  'accepted': 'v2.detail.phaseAccepted',
  'progress': 'v2.detail.phaseProgress',
  'done': 'v2.detail.phaseDone',
  'failed': 'v2.detail.phaseFailed',
  'cancelled': 'v2.detail.phaseCancelled',
  'rejected': 'v2.detail.phaseRejected',
}
const phaseLabel = computed(() => {
  const key = PHASE_KEYS[props.latestPhase]
  return key ? t(key) : props.latestPhase
})
const isEmpty = computed(() => props.latestPhase === '—' || props.latestPhase === 'idle')
const isRunning = computed(() => ['running', 'accepted', 'progress'].includes(props.latestPhase))
const isFailed = computed(() => props.latestPhase === 'failed')
</script>

<template>
  <view class="bento-card" :class="{ 'task-running': isRunning }">
    <view class="task-header">
      <view class="title-wrap">
        <view v-if="isRunning" class="pulse-dot online" />
        <text class="bento-title task-title">
          {{ t('v2.detail.latestTask') }}
        </text>
      </view>
      <wd-tag
        v-if="!isEmpty"
        :type="latestPhase === 'done' ? 'success' : latestPhase === 'failed' ? 'danger' : latestPhase === 'running' ? 'primary' : 'default'"
        size="small" round
        :custom-style="`border-color:${phaseColor};color:${phaseColor}`"
      >
        {{ phaseLabel }}
      </wd-tag>
    </view>
    <!-- M4:空态明说，而非留白 -->
    <text v-if="isEmpty" class="empty-text">
      {{ t('v2.detail.taskEmpty') }}
    </text>
    <view v-if="latestProgressPercent !== null" class="progress-section">
      <view class="progress-track">
        <view class="progress-fill" :style="progressBarStyle" />
      </view>
      <text class="progress-label">
        {{ latestProgressLabel }}
      </text>
    </view>
    <!-- M4/S1:失败就近提示 + 重试（滚到写画区） -->
    <view v-if="isFailed" class="failed-row">
      <text class="failed-hint">
        {{ t('v2.detail.taskFailedHint') }}
      </text>
      <wd-button type="error" plain round size="small" @click="emit('retry')">
        {{ t('v2.detail.taskRetry') }}
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.task-running {
  border-color: var(--accent);
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.task-title {
  margin-bottom: 0;
}

.empty-text {
  display: block;
  font-size: 24rpx;
  color: var(--dim);
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

.failed-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 12rpx;
}

.failed-hint {
  flex: 1;
  font-size: 24rpx;
  color: var(--danger);
}
</style>
