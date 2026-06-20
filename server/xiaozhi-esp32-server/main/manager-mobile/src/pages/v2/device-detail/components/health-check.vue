<script lang="ts" setup>
import type { V2SelfCheckHistoryResponse } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  latestDiagnosticStatus: string
  latestDiagnosticSummary: string
  latestDiagnosticAt: string
  selfCheckHistory: V2SelfCheckHistoryResponse[]
}>()

const healthCheckLoading = defineModel<boolean>('healthCheckLoading', { default: false })
const emit = defineEmits<{
  runHealthCheck: []
}>()
</script>

<template>
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.detail.healthCheck')" :label="latestDiagnosticSummary">
      <template #value>
        <wd-tag
          :type="latestDiagnosticStatus === 'pass' ? 'success' : latestDiagnosticStatus === 'fail' ? 'danger' : 'default'"
          size="mini"
        >
          {{ latestDiagnosticStatus }}
        </wd-tag>
      </template>
    </wd-cell>
    <wd-cell :title="t('v2.detail.latestDiagnosis')" :value="latestDiagnosticAt || t('v2.detail.waitingResult')" />
    <view v-if="selfCheckHistory.length" class="mx-[30rpx] mb-[24rpx]">
      <view
        v-for="item in selfCheckHistory"
        :key="item.id"
        class="mb-[12rpx] rounded-[8rpx] bg-[#f5f7fb] p-[16rpx]"
      >
        <view class="flex items-center justify-between gap-[16rpx]">
          <text class="text-[24rpx] font-medium text-[#232338]">
            {{ item.scope || item.checkId || 'self_check' }}
          </text>
          <wd-tag
            :type="item.status === 'pass' ? 'success' : item.status === 'fail' ? 'danger' : 'default'"
            size="mini"
          >
            {{ item.status }}
          </wd-tag>
        </view>
        <text class="mt-[6rpx] block text-[22rpx] text-[#65686f] leading-[1.4]">
          {{ item.reportedAt ? new Date(item.reportedAt).toLocaleString() : '-' }}
        </text>
        <text class="mt-[6rpx] block text-[22rpx] text-[#65686f] leading-[1.4]">
          {{ item.summary || item.checksJson || 'No summary' }}
        </text>
      </view>
    </view>
    <view class="mx-[30rpx] mb-[24rpx]">
      <wd-button
        type="primary" block round size="large"
        :loading="healthCheckLoading"
        custom-class="!h-[88rpx] !text-[30rpx]"
        @click="emit('runHealthCheck')"
      >
        {{ healthCheckLoading ? t('v2.detail.checking') : t('v2.detail.startHealthCheck') }}
      </wd-button>
    </view>
  </wd-cell-group>
</template>
