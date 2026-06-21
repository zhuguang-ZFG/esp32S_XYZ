<script lang="ts" setup>
import type { V2DeviceInfo } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  deviceInfo: V2DeviceInfo | null
  deviceId: string
  connected: boolean
  workspaceLabel: string
  infoLoading: boolean
}>()
</script>

<template>
  <view class="bento-card device-header">
    <view class="device-top-row">
      <view class="device-name">
        {{ deviceInfo?.model || deviceId }}
      </view>
      <view class="status-dot" :class="connected ? 'online' : 'offline'" />
    </view>
    <view class="device-meta">
      <text>{{ t('v2.detail.hwRev') }} {{ deviceInfo?.hwRev || '—' }} · {{ t('v2.detail.fwRev') }} {{ deviceInfo?.fwRev || '—' }}</text>
    </view>
    <view class="device-id-row">
      <text class="label">
        {{ t('v2.detail.deviceId') }}
      </text>
      <text class="value">
        {{ deviceId }}
      </text>
    </view>
    <view class="device-id-row">
      <text class="label">
        {{ t('v2.detail.workspace') }}
      </text>
      <text class="value">
        {{ workspaceLabel }}
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

.device-header {
  background: linear-gradient(135deg, #336cff 0%, #5b8def 100%);
  color: #ffffff;
}

.device-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.device-name {
  font-size: 36rpx;
  font-weight: 700;
}

.status-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;

  &.online {
    background: #4ade80;
    box-shadow: 0 0 8rpx #4ade80;
  }

  &.offline {
    background: rgba(255, 255, 255, 0.4);
  }
}

.device-meta {
  font-size: 24rpx;
  opacity: 0.85;
  margin-bottom: 20rpx;
}

.device-id-row {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 0;
  font-size: 24rpx;

  .label {
    opacity: 0.7;
  }

  .value {
    opacity: 0.95;
    text-align: right;
    max-width: 60%;
    word-break: break-all;
  }
}
</style>
