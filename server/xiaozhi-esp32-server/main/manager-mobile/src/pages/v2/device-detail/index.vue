<route lang="jsonc" type="page">
{
  "needLogin": true,
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "设备详情"
  }
}
</route>

<script lang="ts" setup>
import { onLoad } from '@dcloudio/uni-app'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { v2GetDeviceInfo } from '@/api/v2'
import { t } from '@/i18n'
import DeviceInfoCard from './components/device-info-card.vue'
import HealthCheck from './components/health-check.vue'
import SharePanel from './components/share-panel.vue'
import SuppliesPanel from './components/supplies-panel.vue'
import TaskStatus from './components/task-status.vue'
import TransferPanel from './components/transfer-panel.vue'
import VoiceApproval from './components/voice-approval.vue'
import WriteDrawPanel from './components/write-draw-panel.vue'
import { useDeviceActions } from './composables/useDeviceActions'
import { useDeviceEvents } from './composables/useDeviceEvents'
import { useDeviceWebSocket } from './composables/useDeviceWebSocket'
import { useVoiceApproval } from './composables/useVoiceApproval'

defineOptions({ name: 'V2DeviceDetail' })
const message = useMessage()
const deviceId = ref('')

// --- WebSocket ---
const { connected, deviceOnline, logLines, latestEvent, connect: wsConnect, appendLog } = useDeviceWebSocket(deviceId)

// --- WS 事件 + 进度 + 自检（P3.1 提取到 useDeviceEvents）---
const {
  deviceInfo,
  infoLoading,
  healthCheckLoading,
  latestPhase,
  latestProgressPercent,
  latestProgressLabel,
  latestDiagnosticStatus,
  latestDiagnosticSummary,
  latestDiagnosticAt,
  selfCheckHistory,
  phaseColor,
  progressBarStyle,
  workspaceLabel,
  isDeviceBusy,
  handleEdgeAEvent,
  loadSelfCheckHistoryData,
  clearInfoLoadingTimer,
  startInfoLoadingTimer,
  setPhase,
  resetProgress,
  applyRuntimeStatus,
} = useDeviceEvents(() => deviceId.value, appendLog)

// --- 任务派发 / 耗材 / 转移 / 分享 / 解绑（P3.1 提取到 useDeviceActions）---
const {
  homeLoading,
  writeTextInput,
  writeTextLoading,
  drawGeneratedLoading,
  suppliesLoading,
  transferLoading,
  drawPromptInput,
  deviceSupplies,
  deviceTransfer,
  transferTargetPhone,
  transferAcceptId,
  shares,
  shareLoading,
  sharePermission,
  shareExpiry,
  unbindLoading,
  starterAssets,
  defaultWriteTextFontId,
  paperSlotStateLabel,
  penStateLabel,
  transferStateLabel,
  taskSubmitErrorMessage,
  handleHome,
  handleWriteText,
  handleDrawPrompt,
  handleDrawStarter,
  handleRefreshInfo,
  handleHealthCheck,
  updatePaper,
  markNewPen,
  handleRequestTransfer,
  handleCancelTransfer,
  handleAcceptTransfer,
  loadShares,
  handleCreateShare,
  handleRevokeShare,
  handleUnbind,
} = useDeviceActions({
  deviceId: () => deviceId.value,
  message,
  appendLog,
  setPhase,
  resetProgress,
  infoLoading,
  healthCheckLoading,
  isDeviceBusy,
  startInfoLoadingTimer,
  clearInfoLoadingTimer,
  applyRuntimeStatus,
})

// --- 语音审批（依赖 actions 的 taskSubmitErrorMessage）---
const {
  pendingVoiceTasks,
  voiceApprovalLoading,
  pendingVoiceApprovalCount,
  pendingVoiceApprovalBadgeText,
  voiceprintApprovalLabel,
  voiceprintReenrollRequired,
  voiceprintHasUnknownSpeaker,
  loadPendingVoiceTasks,
  handleApproveVoiceTask,
  handleRejectVoiceTask,
} = useVoiceApproval(() => deviceId.value, appendLog, message, taskSubmitErrorMessage)

watch(latestEvent, (event) => {
  if (event)
    handleEdgeAEvent(event)
})

function navigateBack() {
  uni.navigateBack()
}
function goToVoiceprint() {
  uni.navigateTo({ url: `/pages/voiceprint/index?deviceId=${deviceId.value || ''}` })
}

onLoad((opt: any) => {
  deviceId.value = opt?.deviceId || ''
})
onMounted(async () => {
  if (!deviceId.value)
    return
  try {
    deviceInfo.value = await v2GetDeviceInfo(deviceId.value)
  }
  catch (e) {
    console.warn('device info load failed:', e)
  }
  await loadPendingVoiceTasks()
  await loadSelfCheckHistoryData()
  await loadShares()
})
onUnmounted(() => {
  clearInfoLoadingTimer()
})
</script>

<template>
  <wd-config-provider theme-color="var(--accent)" />
  <wd-navbar :title="t('v2.deviceDetail.title')" safe-area-inset-top placeholder left-arrow fixed @click-left="navigateBack" />

  <view class="page-enter bento-page">
    <device-info-card :device-info="deviceInfo" :device-id="deviceId" :device-online="deviceOnline" :workspace-label="workspaceLabel" :info-loading="infoLoading" />
    <supplies-panel v-model:supplies-loading="suppliesLoading" :device-supplies="deviceSupplies" :paper-slot-state-label="paperSlotStateLabel" :pen-state-label="penStateLabel" @update-paper="updatePaper" @new-pen="markNewPen" />

    <!-- 主操作按钮 -->
    <view class="bento-card action-row">
      <wd-button type="primary" round block size="large" :loading="homeLoading" custom-class="action-btn" @click="handleHome">
        {{ homeLoading ? t('v2.deviceDetail.homing') : t('v2.deviceDetail.homeButton') }}
      </wd-button>
      <wd-button type="info" round block size="large" :loading="infoLoading" custom-class="action-btn" @click="handleRefreshInfo">
        {{ infoLoading ? t('v2.detail.querying') : t('v2.detail.refreshInfo') }}
      </wd-button>
    </view>

    <task-status :latest-phase="latestPhase" :latest-progress-percent="latestProgressPercent" :latest-progress-label="latestProgressLabel" :phase-color="phaseColor" :progress-bar-style="progressBarStyle" />
    <write-draw-panel v-model:write-text-input="writeTextInput" v-model:draw-prompt-input="drawPromptInput" :write-text-loading="writeTextLoading" :draw-generated-loading="drawGeneratedLoading" :starter-assets="starterAssets" :default-font-id="defaultWriteTextFontId" :device-busy="isDeviceBusy" @write-text="handleWriteText" @draw-prompt="handleDrawPrompt" @draw-starter="handleDrawStarter" />
    <health-check v-model:health-check-loading="healthCheckLoading" :latest-diagnostic-status="latestDiagnosticStatus" :latest-diagnostic-summary="latestDiagnosticSummary" :latest-diagnostic-at="latestDiagnosticAt" :self-check-history="selfCheckHistory" @run-health-check="handleHealthCheck" />
    <transfer-panel v-model:transfer-loading="transferLoading" v-model:transfer-target-phone="transferTargetPhone" v-model:transfer-accept-id="transferAcceptId" :device-transfer="deviceTransfer" :transfer-state-label="transferStateLabel" @request-transfer="handleRequestTransfer" @cancel-transfer="handleCancelTransfer" @accept-transfer="handleAcceptTransfer" />
    <share-panel v-model:share-loading="shareLoading" v-model:share-permission="sharePermission" v-model:share-expiry="shareExpiry" :shares="shares" @create-share="handleCreateShare" @revoke-share="handleRevokeShare" />
    <voice-approval v-model:voice-approval-loading="voiceApprovalLoading" :pending-voice-tasks="pendingVoiceTasks" :pending-voice-approval-count="pendingVoiceApprovalCount" :pending-voice-approval-badge-text="pendingVoiceApprovalBadgeText" :voiceprint-approval-label="voiceprintApprovalLabel" :voiceprint-reenroll-required="voiceprintReenrollRequired" :voiceprint-has-unknown-speaker="voiceprintHasUnknownSpeaker" @refresh-voice-tasks="loadPendingVoiceTasks" @approve="handleApproveVoiceTask" @reject="handleRejectVoiceTask" />

    <!-- 快捷功能 -->
    <view class="bento-card">
      <view class="bento-title">
        {{ t('v2.detail.quickLinks') }}
      </view>
      <view class="quick-links">
        <view class="quick-link" @click="goToVoiceprint">
          <wd-icon name="volume" size="20" color="var(--muted)" />
          <text>{{ t('v2.detail.voiceprintMgmt') }}</text>
          <wd-icon name="arrow-right" size="14" color="#c7c7cc" custom-class="ml-auto" />
        </view>
      </view>
    </view>

    <!-- WSS 日志 -->
    <view class="bento-card">
      <view class="mb-[16rpx] flex items-center justify-between">
        <wd-tag :type="connected ? 'success' : 'default'" size="mini" round>
          {{ connected ? t('v2.detail.wssSubscribed') : t('v2.detail.wssDisconnected') }}
        </wd-tag>
        <wd-button v-if="!connected" type="text" size="small" @click="wsConnect">
          {{ t('v2.deviceDetail.connectAndSubscribe') }}
        </wd-button>
      </view>
      <scroll-view scroll-y class="wss-log">
        <wd-text v-for="(l, i) in logLines" :key="i" :text="l" size="20rpx" color="var(--dim)" custom-class="!leading-[36rpx]" />
        <wd-text v-if="!logLines.length" :text="t('v2.detail.waitingEvents')" size="24rpx" color="var(--faint)" />
      </scroll-view>
    </view>

    <!-- 设备管理（解绑） -->
    <view class="bento-card danger-zone">
      <view class="bento-title danger-title">
        {{ t('v2.detail.deviceManagement') }}
      </view>
      <wd-button type="error" plain round block size="medium" :loading="unbindLoading" @click="handleUnbind">
        {{ t('v2.detail.unbindDevice') }}
      </wd-button>
    </view>

    <view style="height: env(safe-area-inset-bottom);" />
  </view>
</template>

<style lang="scss" scoped>
.bento-page {
  min-height: 100vh;
  background: var(--bg);
  padding: 24rpx 20rpx 40rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.bento-card {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: var(--r);
  padding: 32rpx 28rpx;
}

.bento-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 16rpx;
}

.action-row {
  display: flex;
  gap: 20rpx;
  padding: 24rpx 28rpx;

  .action-btn {
    flex: 1;
    height: 96rpx !important;
    font-size: 32rpx !important;
    font-weight: 600 !important;
  }
}

.quick-links {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.quick-link {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 16rpx;
  border-radius: 16rpx;
  font-size: 28rpx;
  color: var(--text);
  transition: background 0.2s ease;

  &:active {
    background: var(--bg2);
  }

  & + & {
    border-top: 1rpx solid var(--border);
  }
}

.wss-log {
  max-height: 280rpx;
  background: var(--bg2);
  border-radius: 16rpx;
  padding: 20rpx;
}

.danger-zone {
  border: 1rpx solid rgba(239, 68, 68, 0.2);
}

.danger-title {
  color: #ef4444;
  margin-bottom: 16rpx;
}

/* 页面入场动画 */
.page-enter {
  animation: pageFadeIn 0.3s ease-out;
}

@keyframes pageFadeIn {
  from {
    opacity: 0;
    transform: translateY(8rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
