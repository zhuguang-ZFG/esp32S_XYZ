<script lang="ts" setup>
import type { V2DeviceSupplyResponse } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  deviceSupplies: V2DeviceSupplyResponse | null
  paperSlotStateLabel: string
  penStateLabel: string
}>()

const emit = defineEmits<{
  updatePaper: [state: 'empty' | 'loaded' | 'unknown']
  newPen: []
}>()
const suppliesLoading = defineModel<boolean>('suppliesLoading', { default: false })
</script>

<template>
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.supplies') }}
    </view>
    <view class="supply-row">
      <view class="supply-item">
        <wd-tag
          :type="deviceSupplies?.paperSlotState === 'loaded' ? 'success' : deviceSupplies?.paperSlotState === 'empty' ? 'danger' : 'default'"
          size="small" round
        >
          {{ paperSlotStateLabel }}
        </wd-tag>
        <text class="supply-label">
          {{ penStateLabel }}
        </text>
      </view>
    </view>
    <view class="supply-actions">
      <wd-button type="success" round size="small" :loading="suppliesLoading" @click="emit('updatePaper', 'loaded')">
        {{ t('v2.detail.paperLoaded') }}
      </wd-button>
      <wd-button type="warning" round size="small" :disabled="suppliesLoading" @click="emit('updatePaper', 'empty')">
        {{ t('v2.detail.paperEmpty') }}
      </wd-button>
      <wd-button type="info" round size="small" :disabled="suppliesLoading" @click="emit('newPen')">
        {{ t('v2.detail.newPen') }}
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
  margin-bottom: 16rpx;
}

.supply-row {
  margin-bottom: 20rpx;
}

.supply-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.supply-label {
  font-size: 24rpx;
  color: var(--muted);
}

.supply-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
</style>
