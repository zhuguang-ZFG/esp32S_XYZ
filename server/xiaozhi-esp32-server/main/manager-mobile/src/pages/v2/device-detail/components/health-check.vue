<script lang="ts" setup>
import type { V2SelfCheckHistoryResponse } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  latestDiagnosticStatus: string
  latestDiagnosticSummary: string
  latestDiagnosticAt: string
  selfCheckHistory: V2SelfCheckHistoryResponse[]
  deviceOnline?: boolean
}>()

const emit = defineEmits<{
  runHealthCheck: []
}>()
const healthCheckLoading = defineModel<boolean>('healthCheckLoading', { default: false })
</script>

<template>
  <view class="bento-card">
    <view class="health-header">
      <text class="bento-title">
        {{ t('v2.detail.healthCheck') }}
      </text>
      <wd-tag
        :type="latestDiagnosticStatus === 'pass' ? 'success' : latestDiagnosticStatus === 'fail' ? 'danger' : 'default'"
        size="small" round
      >
        {{ latestDiagnosticStatus }}
      </wd-tag>
    </view>
    <text class="summary-text">
      {{ latestDiagnosticSummary }}
    </text>
    <text class="time-text">
      {{ t('v2.detail.latestDiagnosis') }}: {{ latestDiagnosticAt || t('v2.detail.waitingResult') }}
    </text>

    <!-- M13:自检进行中就地反馈 -->
    <view v-if="healthCheckLoading" class="checking-row">
      <wd-loading size="32rpx" />
      <text class="checking-text">
        {{ t('v2.detail.selfCheckRunning') }}
      </text>
    </view>

    <view v-if="selfCheckHistory.length" class="history-list">
      <view
        v-for="item in selfCheckHistory"
        :key="item.id"
        class="history-item"
      >
        <view class="history-top">
          <text class="history-scope">
            {{ item.scope || item.checkId || 'self_check' }}
          </text>
          <wd-tag
            :type="item.status === 'pass' ? 'success' : item.status === 'fail' ? 'danger' : 'default'"
            size="mini" round
          >
            {{ item.status }}
          </wd-tag>
        </view>
        <text class="history-time">
          {{ item.reportedAt ? new Date(item.reportedAt).toLocaleString() : '-' }}
        </text>
        <text class="history-summary">
          {{ item.summary || item.checksJson || 'No summary' }}
        </text>
      </view>
    </view>

    <wd-button
      type="primary" round block size="large"
      :loading="healthCheckLoading"
      :disabled="deviceOnline === false"
      custom-class="!h-[88rpx] !text-[30rpx] !mt-[20rpx]"
      @click="emit('runHealthCheck')"
    >
      {{ healthCheckLoading ? t('v2.detail.checking') : t('v2.detail.startHealthCheck') }}
    </wd-button>
    <!-- M13:disabled 时给出原因,不让用户猜 -->
    <text v-if="deviceOnline === false" class="offline-hint">
      {{ t('v2.detail.offlineCannotCheck') }}
    </text>
  </view>
</template>

<style lang="scss" scoped>
.health-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;

  .bento-title {
    margin-bottom: 0;
  }
}

.checking-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 0;

  .checking-text {
    font-size: 24rpx;
    color: var(--muted);
  }
}

.offline-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--dim);
  text-align: center;
}

.summary-text {
  display: block;
  font-size: 24rpx;
  color: var(--muted);
  margin-bottom: 8rpx;
}

.time-text {
  display: block;
  font-size: 22rpx;
  color: var(--dim);
  margin-bottom: 20rpx;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.history-item {
  background: var(--bg2);
  border-radius: 16rpx;
  padding: 16rpx;
}

.history-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6rpx;
}

.history-scope {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text);
}

.history-time {
  display: block;
  font-size: 22rpx;
  color: var(--muted);
  margin-bottom: 4rpx;
}

.history-summary {
  display: block;
  font-size: 22rpx;
  color: var(--muted);
}
</style>
