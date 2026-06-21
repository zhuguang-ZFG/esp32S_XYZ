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
const transferTargetUnionid = defineModel<string>('transferTargetUnionid', { default: '' })
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
      v-model="transferTargetUnionid"
      clearable
      :maxlength="80"
      :placeholder="t('v2.detail.targetUnionid')"
      custom-class="!bg-[#f5f5f7] !rounded-[16rpx] !px-[20rpx] !mt-[16rpx] !mb-[12rpx]"
    />
    <wd-input
      v-model="transferAcceptId"
      clearable
      type="number"
      placeholder="transferId"
      custom-class="!bg-[#f5f5f7] !rounded-[16rpx] !px-[20rpx] !mb-[20rpx]"
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
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.bento-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 8rpx;
}

.state-label {
  font-size: 24rpx;
  color: #65686f;
}

.transfer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
</style>
