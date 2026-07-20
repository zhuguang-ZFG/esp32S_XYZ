<script lang="ts" setup>
import type { V2PendingVoiceTaskResponse } from '@/api/v2/types'
import { computed, ref } from 'vue'
import { t } from '@/i18n'

const props = defineProps<{
  pendingVoiceTasks: V2PendingVoiceTaskResponse[]
  pendingVoiceApprovalCount: number
  pendingVoiceApprovalBadgeText: string
  voiceprintApprovalLabel: (task: V2PendingVoiceTaskResponse) => string
  voiceprintReenrollRequired: (task: V2PendingVoiceTaskResponse) => boolean
  voiceprintHasUnknownSpeaker: (task: V2PendingVoiceTaskResponse) => boolean
  taskActionLoading?: Record<string, boolean>
}>()

const emit = defineEmits<{
  refreshVoiceTasks: []
  approve: [taskId: string]
  reject: [taskId: string]
}>()
const voiceApprovalLoading = defineModel<boolean>('voiceApprovalLoading', { default: false })

// M16:原始 JSON 收进展开区,默认显示人话摘要
const rawExpanded = ref<Record<string, boolean>>({})

const CAPABILITY_KEYS: Record<string, string> = {
  write_text: 'v2.detail.capWriteText',
  draw_generated: 'v2.detail.capDraw',
  home: 'v2.detail.capHome',
  pause: 'v2.detail.capPause',
  resume: 'v2.detail.capResume',
  run_path: 'v2.detail.capRunPath',
}

function capabilityLabel(capability: string) {
  const key = CAPABILITY_KEYS[capability]
  return key ? t(key) : capability
}

function paramsSummary(task: V2PendingVoiceTaskResponse): string {
  if (!task.paramsJson)
    return ''
  try {
    const p = JSON.parse(task.paramsJson) as Record<string, unknown>
    if (typeof p.text === 'string' && p.text)
      return `${t('v2.detail.paramText')}: “${p.text}”`
    if (typeof p.prompt === 'string' && p.prompt)
      return `${t('v2.detail.paramPrompt')}: “${p.prompt}”`
    return ''
  }
  catch {
    return ''
  }
}

// S2: 模板里每个 task 只读一次摘要（避免 v-if + 插值双次 JSON.parse）
const summaryByTaskId = computed(() => {
  const map: Record<string, string> = {}
  for (const task of props.pendingVoiceTasks)
    map[task.taskId] = paramsSummary(task)
  return map
})

function toggleRaw(taskId: string) {
  rawExpanded.value[taskId] = !rawExpanded.value[taskId]
}
</script>

<template>
  <view class="bento-card">
    <view class="voice-header">
      <text class="bento-title voice-title">
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
            {{ capabilityLabel(task.capability) }}
          </text>
          <wd-tag type="warning" size="mini" round>
            {{ task.status }}
          </wd-tag>
        </view>
        <!-- M16/S2:关键参数人话化；摘要按 taskId 预计算 -->
        <text v-if="summaryByTaskId[task.taskId]" class="task-summary">
          {{ summaryByTaskId[task.taskId] }}
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
        <view class="raw-toggle" @click="toggleRaw(task.taskId)">
          <text class="raw-toggle-text">
            {{ t('v2.detail.rawParams') }}
          </text>
          <wd-icon :name="rawExpanded[task.taskId] ? 'arrow-up' : 'arrow-down'" size="12" color="var(--dim)" />
        </view>
        <view v-if="rawExpanded[task.taskId]" class="raw-block">
          <text class="task-id">
            {{ task.requestId || task.taskId }}
          </text>
          <text v-if="task.paramsJson" class="task-params">
            {{ task.paramsJson.slice(0, 200) }}
          </text>
        </view>
        <view class="task-actions">
          <wd-button
            type="success" round size="small"
            :loading="taskActionLoading?.[task.taskId]"
            :disabled="taskActionLoading?.[task.taskId]"
            @click="emit('approve', task.taskId)"
          >
            {{ t('v2.deviceDetail.approve') }}
          </wd-button>
          <wd-button
            type="danger" round size="small"
            :loading="taskActionLoading?.[task.taskId]"
            :disabled="taskActionLoading?.[task.taskId]"
            @click="emit('reject', task.taskId)"
          >
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
.voice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.voice-title {
  margin-bottom: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.voice-subtitle {
  display: block;
  font-size: 24rpx;
  color: var(--muted);
  margin-bottom: 20rpx;
}

.task-card {
  background: var(--bg2);
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
  color: var(--text);
}

.task-summary {
  display: block;
  font-size: 24rpx;
  color: var(--text);
  margin-bottom: 8rpx;
}

.task-id {
  display: block;
  font-size: 22rpx;
  color: var(--muted);
  margin-bottom: 4rpx;
}

.task-params {
  display: block;
  font-size: 22rpx;
  color: var(--muted);
  word-break: break-all;
}

.task-constraints {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-bottom: 12rpx;
}

.constraint-text {
  font-size: 22rpx;
  color: var(--muted);
}

.raw-toggle {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 0;
  margin-bottom: 8rpx;

  .raw-toggle-text {
    font-size: 22rpx;
    color: var(--dim);
  }
}

.raw-block {
  background: var(--bg);
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  margin-bottom: 12rpx;
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
  color: var(--dim);
}
</style>
