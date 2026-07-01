<script lang="ts" setup>
import type { V2DeviceTransferResponse } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  deviceTransfer: V2DeviceTransferResponse | null
  transferStateLabel: string
}>()

const emit = defineEmits<{
  requestTransfer: []
  cancelTransfer: []
  acceptTransfer: []
}>()
const transferLoading = defineModel<boolean>('transferLoading', { default: false })
const transferTargetPhone = defineModel<string>('transferTargetPhone', { default: '' })
const transferAcceptId = defineModel<string>('transferAcceptId', { default: '' })
</script>

<template>
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.transfer') }}
    </view>
    <text class="state-label">
      {{ transferStateLabel }}
    </text>
    <wd-input
      v-model="transferTargetPhone"
      clearable
      type="number"
      :maxlength="11"
      :placeholder="t('v2.detail.targetPhone')"
      custom-class="!bg-[#14181f] !text-[#f0f4f8] !rounded-[16rpx] !px-[20rpx] !mt-[16rpx] !mb-[12rpx]"
    />
    <wd-input
      v-model="transferAcceptId"
      clearable
      type="number"
      placeholder="transferId"
      custom-class="!bg-[#14181f] !text-[#f0f4f8] !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
    />
    <view class="transfer-actions">
      <wd-button type="primary" round size="small" :loading="transferLoading" @click="emit('requestTransfer')">
        {{ t('v2.detail.requestTransfer') }}
      </wd-button>
      <wd-button type="warning" round size="small" :disabled="transferLoading" @click="emit('cancelTransfer')">
        {{ t('v2.detail.cancelTransfer') }}
      </wd-button>
      <wd-button type="success" round size="small" :disabled="transferLoading" @click="emit('acceptTransfer')">
        {{ t('v2.detail.acceptTransfer') }}
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
  margin-bottom: 8rpx;
}

.state-label {
  font-size: 24rpx;
  color: var(--muted);
}

.transfer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
</style>
