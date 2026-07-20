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
  <view class="transfer-panel">
    <!-- M5 双态:默认态只留手机号 + 发起 -->
    <template v-if="!deviceTransfer">
      <text class="state-label">
        {{ transferStateLabel }}
      </text>
      <wd-input
        v-model="transferTargetPhone"
        clearable
        type="number"
        :maxlength="11"
        :placeholder="t('v2.detail.targetPhone')"
        custom-class="dark-input !rounded-[16rpx] !px-[20rpx] !mt-[16rpx] !mb-[20rpx]"
      />
      <wd-button type="primary" round block :loading="transferLoading" custom-class="!h-[88rpx]" @click="emit('requestTransfer')">
        {{ t('v2.detail.requestTransfer') }}
      </wd-button>
    </template>

    <!-- M5 双态:已有转赠时切状态视图,transferId 自动带入隐藏 -->
    <template v-else>
      <view class="transfer-state">
        <wd-tag type="warning" size="small" round>
          #{{ deviceTransfer.transferId }} {{ deviceTransfer.status }}
        </wd-tag>
        <text v-if="transferTargetPhone" class="transfer-target">
          {{ t('v2.detail.transferTo') }} {{ transferTargetPhone }}
        </text>
      </view>
      <view class="transfer-actions">
        <wd-button type="warning" round size="small" :loading="transferLoading" @click="emit('cancelTransfer')">
          {{ t('v2.detail.cancelTransfer') }}
        </wd-button>
        <wd-button type="success" round size="small" :disabled="transferLoading" @click="emit('acceptTransfer')">
          {{ t('v2.detail.acceptTransfer') }}
        </wd-button>
      </view>
      <!-- transferAcceptId 仍由 composable 维护,不再要求用户手填 -->
      <view v-if="false">
        {{ transferAcceptId }}
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.state-label {
  font-size: 24rpx;
  color: var(--muted);
}

.transfer-state {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.transfer-target {
  font-size: 26rpx;
  color: var(--text);
}

.transfer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
</style>
