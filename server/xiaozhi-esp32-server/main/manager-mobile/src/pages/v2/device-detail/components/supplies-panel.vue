<script lang="ts" setup>
import type { V2DeviceSupplyResponse } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  deviceSupplies: V2DeviceSupplyResponse | null
  paperSlotStateLabel: string
  penStateLabel: string
}>()

const suppliesLoading = defineModel<boolean>('suppliesLoading', { default: false })
const emit = defineEmits<{
  updatePaper: [state: 'empty' | 'loaded' | 'unknown']
  newPen: []
}>()
</script>

<template>
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.detail.supplies')" :label="penStateLabel">
      <template #value>
        <wd-tag
          :type="deviceSupplies?.paperSlotState === 'loaded' ? 'success' : deviceSupplies?.paperSlotState === 'empty' ? 'danger' : 'default'"
          size="mini"
        >
          {{ paperSlotStateLabel }}
        </wd-tag>
      </template>
    </wd-cell>
    <view class="mx-[30rpx] mb-[24rpx] flex flex-wrap gap-[12rpx]">
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
  </wd-cell-group>
</template>
