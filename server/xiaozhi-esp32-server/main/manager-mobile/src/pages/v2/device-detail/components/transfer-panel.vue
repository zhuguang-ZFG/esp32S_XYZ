<script lang="ts" setup>
import type { V2DeviceTransferResponse } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  deviceTransfer: V2DeviceTransferResponse | null
  transferStateLabel: string
}>()

const transferLoading = defineModel<boolean>('transferLoading', { default: false })
const transferTargetUnionid = defineModel<string>('transferTargetUnionid', { default: '' })
const transferAcceptId = defineModel<string>('transferAcceptId', { default: '' })

const emit = defineEmits<{
  requestTransfer: []
  cancelTransfer: []
  acceptTransfer: []
}>()
</script>

<template>
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.detail.transfer')" :label="transferStateLabel" />
    <view class="mx-[30rpx] mb-[24rpx]">
      <wd-input
        v-model="transferTargetUnionid"
        clearable
        :maxlength="80"
        :placeholder="t('v2.detail.targetUnionid')"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
      />
      <wd-input
        v-model="transferAcceptId"
        clearable
        type="number"
        placeholder="transferId"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
      />
      <view class="flex flex-wrap gap-[12rpx]">
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
  </wd-cell-group>
</template>
