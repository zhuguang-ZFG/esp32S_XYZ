<script lang="ts" setup>
import type { V2PendingVoiceTaskResponse } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  pendingVoiceTasks: V2PendingVoiceTaskResponse[]
  pendingVoiceApprovalCount: number
  pendingVoiceApprovalBadgeText: string
  voiceprintApprovalLabel: (task: V2PendingVoiceTaskResponse) => string
  voiceprintReenrollRequired: (task: V2PendingVoiceTaskResponse) => boolean
  voiceprintHasUnknownSpeaker: (task: V2PendingVoiceTaskResponse) => boolean
}>()

const voiceApprovalLoading = defineModel<boolean>('voiceApprovalLoading', { default: false })
const emit = defineEmits<{
  refreshVoiceTasks: []
  approve: [taskId: string]
  reject: [taskId: string]
}>()
</script>

<template>
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.deviceDetail.pendingVoiceApprovals')" :label="`${pendingVoiceApprovalCount} ${t('v2.detail.voiceTasksWaiting')}`">
      <template #value>
        <view class="flex items-center gap-[12rpx]">
          <wd-tag v-if="pendingVoiceApprovalCount" type="warning" size="mini">
            {{ pendingVoiceApprovalBadgeText }}
          </wd-tag>
          <wd-button type="text" size="small" :loading="voiceApprovalLoading" @click="emit('refreshVoiceTasks')">
            {{ t('v2.deviceDetail.refresh') }}
          </wd-button>
        </view>
      </template>
    </wd-cell>
    <template v-if="pendingVoiceTasks.length">
      <view
        v-for="task in pendingVoiceTasks"
        :key="task.taskId"
        class="mx-[30rpx] mb-[24rpx] rounded-[8rpx] bg-[#f5f7fb] p-[20rpx]"
      >
        <view class="flex items-center justify-between gap-[16rpx]">
          <wd-text :text="task.capability" size="28rpx" color="#222" />
          <wd-tag type="warning" size="mini">{{ task.status }}</wd-tag>
        </view>
        <wd-text :text="task.requestId || task.taskId" size="22rpx" color="#666" custom-class="!mt-[8rpx]" />
        <wd-text v-if="task.paramsJson" :text="task.paramsJson.slice(0, 120)" size="22rpx" color="#666" custom-class="!mt-[8rpx]" />
        <view v-if="task.constraintsJson" class="mt-[12rpx] flex flex-col gap-[8rpx]">
          <wd-text :text="voiceprintApprovalLabel(task)" size="22rpx" color="#4b5563" />
          <wd-tag v-if="voiceprintReenrollRequired(task)" type="warning" size="mini">
            {{ t('v2.detail.reenrollNeeded') }}
          </wd-tag>
          <wd-tag v-if="voiceprintHasUnknownSpeaker(task)" type="danger" size="mini">
            {{ t('v2.detail.unknownSpeaker') }}
          </wd-tag>
        </view>
        <view class="mt-[16rpx] flex flex-wrap gap-[12rpx]">
          <wd-button type="success" round size="small" :loading="voiceApprovalLoading" @click="emit('approve', task.taskId)">
            {{ t('v2.deviceDetail.approve') }}
          </wd-button>
          <wd-button type="danger" round size="small" :disabled="voiceApprovalLoading" @click="emit('reject', task.taskId)">
            {{ t('v2.deviceDetail.reject') }}
          </wd-button>
        </view>
      </view>
    </template>
    <view v-else class="mx-[30rpx] mb-[24rpx] rounded-[8rpx] bg-[#f5f7fb] p-[20rpx]">
      <wd-text :text="t('v2.deviceDetail.noPendingVoice')" size="24rpx" color="#666" />
    </view>
  </wd-cell-group>
</template>
