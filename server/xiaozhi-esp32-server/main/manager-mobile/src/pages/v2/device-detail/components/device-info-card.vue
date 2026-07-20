<script lang="ts" setup>
import type { V2DeviceInfo } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  deviceInfo: V2DeviceInfo | null
  deviceId: string
  deviceOnline: boolean
  workspaceLabel: string
  infoLoading: boolean
}>()
</script>

<template>
  <!-- M2:首屏加载骨架（激活原死 prop infoLoading） -->
  <view v-if="infoLoading && !deviceInfo" class="bento-card device-header">
    <view class="skeleton skeleton-text" style="width: 40%;" />
    <view class="skeleton skeleton-text" style="width: 70%;" />
    <view class="skeleton skeleton-text" />
  </view>
  <view v-else class="bento-card device-header">
    <view class="device-top-row">
      <view class="device-name">
        {{ deviceInfo?.model || deviceId }}
      </view>
      <view class="pulse-dot" :class="deviceOnline ? 'online' : 'offline'" />
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
/* M8:去大渐变 → surface 底 + 左侧 accent 竖条（全 app 渐变仅登录页保留） */
.device-header {
  border-left: 6rpx solid var(--accent);
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
  color: var(--text);
}

.device-meta {
  font-size: 24rpx;
  color: var(--muted);
  margin-bottom: 20rpx;
}

.device-id-row {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 0;
  font-size: 24rpx;

  .label {
    color: var(--muted);
  }

  .value {
    color: var(--text);
    text-align: right;
    max-width: 60%;
    word-break: break-all;
  }
}
</style>
