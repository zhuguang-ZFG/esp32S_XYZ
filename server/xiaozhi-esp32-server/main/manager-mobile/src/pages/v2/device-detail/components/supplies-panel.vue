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
    <!-- M12:纸/笔拆两行 telemetry,主次分明 -->
    <view class="telemetry-row">
      <text class="telemetry-label">
        {{ t('v2.detail.paperLabel') }}
      </text>
      <wd-tag
        :type="deviceSupplies?.paperSlotState === 'loaded' ? 'success' : deviceSupplies?.paperSlotState === 'empty' ? 'danger' : 'default'"
        size="small" round
      >
        {{ paperSlotStateLabel }}
      </wd-tag>
    </view>
    <view class="telemetry-row supply-pen-row">
      <text class="telemetry-label">
        {{ t('v2.detail.penLabel') }}
      </text>
      <text class="telemetry-value">
        {{ penStateLabel }}
      </text>
    </view>
    <!-- M12:仅主操作 primary,其余 plain 收敛 -->
    <view class="supply-actions">
      <wd-button type="primary" round size="small" :loading="suppliesLoading" @click="emit('updatePaper', 'loaded')">
        {{ t('v2.detail.paperLoaded') }}
      </wd-button>
      <wd-button type="warning" plain round size="small" :disabled="suppliesLoading" @click="emit('updatePaper', 'empty')">
        {{ t('v2.detail.paperEmpty') }}
      </wd-button>
      <wd-button type="info" plain round size="small" :disabled="suppliesLoading" @click="emit('newPen')">
        {{ t('v2.detail.newPen') }}
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.supply-pen-row {
  margin-bottom: 20rpx;
}

.supply-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
</style>
