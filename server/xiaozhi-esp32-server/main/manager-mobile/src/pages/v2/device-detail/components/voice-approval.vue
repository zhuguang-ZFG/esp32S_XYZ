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

const emit = defineEmits<{
  refreshVoiceTasks: []
  approve: [taskId: string]
  reject: [taskId: string]
}>()
const voiceApprovalLoading = defineModel<boolean>('voiceApprovalLoading', { default: false })
</script>

<template>
  <view class="bento-card">
    <view class="voice-header">
      <text class="bento-title">
        {{ t('v2.deviceDetail.pendingVoiceApprovals') }}
      </text>
      <view class="header-right">
        <wd-tag v-if="pendingVoiceApprovalCount" type="warning" size="small" round>
          {{ pendingVoiceApprovalBadgeText }}
        </wd-tag>
        <wd-button type="text" size="small" :loading="voiceApprovalLoading" @click="emit('refreshVoiceTasks')">
          {{ t('v2.deviceDetail.refresh') }}
        </wd-button>
      </view>
    </view>
    <text class="voice-subtitle">
      {{ pendingVoiceApprovalCount }} {{ t('v2.detail.voiceTasksWaiting') }}
    </text>

    <template v-if="pendingVoiceTasks.length">
      <view
        v-for="task in pendingVoiceTasks"
        :key="task.taskId"
        class="task-card"
      >
        <view class="task-top">
          <text class="task-capability">
            {{ task.capability }}
          </text>
          <wd-tag type="warning" size="mini" round>
            {{ task.status }}
          </wd-tag>
        </view>
        <text class="task-id">
          {{ task.requestId || task.taskId }}
        </text>
        <text v-if="task.paramsJson" class="task-params">
          {{ task.paramsJson.slice(0, 120) }}
        </text>
        <view v-if="task.constraintsJson" class="task-constraints">
          <text class="constraint-text">
            {{ voiceprintApprovalLabel(task) }}
          </text>
          <wd-tag v-if="voiceprintReenrollRequired(task)" type="warning" size="mini" round>
            {{ t('v2.detail.reenrollNeeded') }}
          </wd-tag>
          <wd-tag v-if="voiceprintHasUnknownSpeaker(task)" type="danger" size="mini" round>
            {{ t('v2.detail.unknownSpeaker') }}
          </wd-tag>
        </view>
        <view class="task-actions">
          <wd-button type="success" round size="small" :loading="voiceApprovalLoading" @click="emit('approve', task.taskId)">
            {{ t('v2.deviceDetail.approve') }}
          </wd-button>
          <wd-button type="danger" round size="small" :disabled="voiceApprovalLoading" @click="emit('reject', task.taskId)">
            {{ t('v2.deviceDetail.reject') }}
          </wd-button>
        </view>
      </view>
    </template>
    <view v-else class="empty-state">
      <text>{{ t('v2.deviceDetail.noPendingVoice') }}</text>
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
}

.voice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.voice-subtitle {
  display: block;
  font-size: 24rpx;
  color: #65686f;
  margin-bottom: 20rpx;
}

.task-card {
  background: #f5f5f7;
  border-radius: 16rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
}

.task-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.task-capability {
  font-size: 28rpx;
  font-weight: 500;
  color: #222;
}

.task-id {
  display: block;
  font-size: 22rpx;
  color: #65686f;
  margin-bottom: 4rpx;
}

.task-params {
  display: block;
  font-size: 22rpx;
  color: #65686f;
  margin-bottom: 8rpx;
}

.task-constraints {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-bottom: 12rpx;
}

.constraint-text {
  font-size: 22rpx;
  color: #4b5563;
}

.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.empty-state {
  text-align: center;
  padding: 32rpx 0;
  font-size: 24rpx;
  color: #9d9ea3;
}
</style>
