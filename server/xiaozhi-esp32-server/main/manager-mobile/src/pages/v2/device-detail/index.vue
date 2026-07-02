<script lang="ts" setup>
import type { V2ShareResponse } from '@/api/v2'
import type { V2DeviceInfo, V2DeviceSupplyResponse, V2DeviceTransferResponse, V2SelfCheckHistoryResponse } from '@/api/v2/types'
import { onLoad } from '@dcloudio/uni-app'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import {
  v2AcceptDeviceTransfer,
  v2CancelDeviceTransfer,
  v2CreateShare,
  v2GetDeviceInfo,
  v2ListSelfCheckHistory,
  v2ListShares,
  v2RequestDeviceTransfer,
  v2RevokeShare,
  v2SubmitTask,
  v2UnbindDevice,
  v2UpdateDeviceSupplies,
} from '@/api/v2'
import { t } from '@/i18n'
import DeviceInfoCard from './components/device-info-card.vue'
import HealthCheck from './components/health-check.vue'
import SharePanel from './components/share-panel.vue'
import SuppliesPanel from './components/supplies-panel.vue'
import TaskStatus from './components/task-status.vue'
import TransferPanel from './components/transfer-panel.vue'
import VoiceApproval from './components/voice-approval.vue'
import VoiceCommand from './components/voice-command.vue'
import WriteDrawPanel from './components/write-draw-panel.vue'
import { useDeviceWebSocket } from './composables/useDeviceWebSocket'
import { useVoiceApproval } from './composables/useVoiceApproval'

defineOptions({ name: 'V2DeviceDetail' })
const message = useMessage()
const deviceId = ref('')
const deviceInfo = ref<V2DeviceInfo | null>(null)
const homeLoading = ref(false)
const infoLoading = ref(false)
const writeTextInput = ref('你好')
const writeTextLoading = ref(false)
const drawGeneratedLoading = ref(false)
const healthCheckLoading = ref(false)
const suppliesLoading = ref(false)
const transferLoading = ref(false)
const drawPromptInput = ref('星星')
const deviceSupplies = ref<V2DeviceSupplyResponse | null>(null)
const deviceTransfer = ref<V2DeviceTransferResponse | null>(null)
const transferTargetPhone = ref('')
const transferAcceptId = ref('')
const latestPhase = ref('—')
const latestProgressPercent = ref<number | null>(null)
const latestProgressLabel = ref('')
const latestDiagnosticStatus = ref('pending')
const latestDiagnosticSummary = ref('No self-check result yet')
const latestDiagnosticAt = ref('')
const selfCheckHistory = ref<V2SelfCheckHistoryResponse[]>([])
const shares = ref<V2ShareResponse[]>([])
const shareLoading = ref(false)
const sharePermission = ref('view')
const shareExpiry = ref('7d')
const unbindLoading = ref(false)
let infoLoadingTimer: ReturnType<typeof setTimeout> | null = null

const defaultWriteTextFontId = 'kai_basic_v1'
const starterAssets = [
  { id: 'starter_star', label: '星星' },
  { id: 'starter_house', label: '小房子' },
  { id: 'starter_tree', label: '树' },
  { id: 'starter_fish', label: '鱼' },
  { id: 'starter_flower', label: '花' },
]
const healthCheckPath = [
  { cmd: 'M', x: 5, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 5, z: 0 },
]

// --- Types ---
interface WorkspaceMm { x?: number | string, y?: number | string, z?: number | string }
interface MotionProgress { done_segments?: number | string, total_segments?: number | string, percent?: number | string }
interface DeviceInfoReplyPayload { model?: string, hw_rev?: string, fw_rev?: string, workspace_mm?: WorkspaceMm | string }
interface JobStatusPayload { phase?: string, capability?: string, progress?: MotionProgress }
type SelfCheckStatus = 'pass' | 'warn' | 'fail' | 'pending' | string
interface SelfCheckItem { name?: string, status?: SelfCheckStatus, detail?: string }
interface SelfCheckPayload { check_id?: string, scope?: string, status?: SelfCheckStatus, checks?: SelfCheckItem[] | Record<string, SelfCheckItem | SelfCheckStatus | string> }

// --- Error messages ---
function taskSubmitErrorMessage(error: any) {
  const text = String(error?.message || error || '')
  if (text.includes('E_RUNTIME_STALE'))
    return t('v2.detail.errorRuntimeStale')
  if (text.includes('E_CONTENT_BLOCKED'))
    return t('v2.detail.errorContentBlocked')
  if (text.includes('E_INVALID_DRAWING'))
    return t('v2.detail.errorInvalidDrawing')
  if (text.includes('E_NOT_ENTITLED'))
    return t('v2.detail.errorNotEntitled')
  if (text.includes('E_NO_PAPER'))
    return t('v2.detail.errorNoPaper')
  return text || t('common.fail')
}
function showSubmitToast(key: string) {
  uni.showToast({ title: t(key), icon: 'none' })
}
function clearInfoLoadingTimer() {
  if (infoLoadingTimer) {
    clearTimeout(infoLoadingTimer)
    infoLoadingTimer = null
  }
}

// --- WebSocket ---
const { connected, logLines, latestEvent, connect: wsConnect, appendLog } = useDeviceWebSocket(deviceId)

// --- Voice approval composable ---
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

function handleEdgeAEvent(event: any) {
  const payload = event?.payload as JobStatusPayload | DeviceInfoReplyPayload | SelfCheckPayload | undefined
  if (event?.event_type === 'device_info_reply') {
    applyDeviceInfoReply(event, payload as DeviceInfoReplyPayload)
    return
  }
  if (event?.event_type === 'self_check') {
    applySelfCheck(event, payload as SelfCheckPayload)
    return
  }
  const jobPayload = payload as JobStatusPayload | undefined
  appendLog(`seq=${event?.seq} ${jobPayload?.capability || ''} ${jobPayload?.phase || ''}`)
  applyTaskProgress(jobPayload)
  if (jobPayload?.phase)
    latestPhase.value = jobPayload.phase
}

function applyTaskProgress(payload: JobStatusPayload | undefined) {
  if (!payload?.phase)
    return
  if (['done', 'failed', 'cancelled', 'rejected'].includes(payload.phase))
    healthCheckLoading.value = false
  if (payload.phase === 'progress' && payload.progress) {
    const pct = clampPercent(Number(payload.progress.percent || 0))
    const done = Number(payload.progress.done_segments || 0)
    const total = Number(payload.progress.total_segments || 0)
    latestProgressPercent.value = pct
    latestProgressLabel.value = total > 0 ? `${pct}% (${done}/${total})` : `${pct}%`
    return
  }
  if (payload.phase === 'done') {
    latestProgressPercent.value = 100
    latestProgressLabel.value = '100%'
    return
  }
  if (['accepted', 'running'].includes(payload.phase)) {
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
    return
  }
  if (['failed', 'cancelled', 'rejected'].includes(payload.phase)) {
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
  }
}
function clampPercent(v: number) {
  return Number.isFinite(v) ? Math.max(0, Math.min(100, Math.round(v))) : 0
}
const progressBarStyle = computed(() => `width:${latestProgressPercent.value ?? 0}%;`)

function normalizeWorkspace(value: WorkspaceMm | string | undefined) {
  if (!value)
    return null
  if (typeof value === 'string') {
    try {
      return normalizeWorkspace(JSON.parse(value))
    }
    catch { return null }
  }
  return { x: Number(value.x || 0), y: Number(value.y || 0), z: Number(value.z || 0) }
}

function applyDeviceInfoReply(event: any, payload: DeviceInfoReplyPayload) {
  const ws = normalizeWorkspace(payload?.workspace_mm)
  deviceInfo.value = {
    deviceId: event?.device_id || deviceId.value,
    model: payload?.model || deviceInfo.value?.model || '',
    hwRev: payload?.hw_rev || deviceInfo.value?.hwRev || '',
    fwRev: payload?.fw_rev || deviceInfo.value?.fwRev || '',
    workspaceMm: ws || deviceInfo.value?.workspaceMm || { x: 0, y: 0, z: 0 },
    status: deviceInfo.value?.status || 'online',
    lastSeenAt: event?.ts ? new Date(event.ts).toISOString() : new Date().toISOString(),
  }
  infoLoading.value = false
  clearInfoLoadingTimer()
  appendLog(`device_info_reply seq=${event?.seq || '-'} model=${payload?.model || '-'}`)
}

function applySelfCheck(event: any, payload: SelfCheckPayload) {
  latestDiagnosticStatus.value = String(payload?.status || 'pending')
  latestDiagnosticSummary.value = formatSelfCheckSummary(payload)
  latestDiagnosticAt.value = event?.ts ? new Date(event.ts).toLocaleString() : new Date().toLocaleString()
  selfCheckHistory.value = [{ id: Date.now(), deviceId: deviceId.value, checkId: payload?.check_id || 'startup', scope: payload?.scope || 'startup', status: latestDiagnosticStatus.value, summary: latestDiagnosticSummary.value, checksJson: payload?.checks ? JSON.stringify(payload.checks) : undefined, reportedAt: new Date().toISOString() }, ...selfCheckHistory.value].filter((v, i, l) => l.findIndex(o => o.id === v.id) === i).slice(0, 5)
  appendLog(`self_check seq=${event?.seq || '-'} status=${latestDiagnosticStatus.value}`)
}

function formatSelfCheckSummary(payload: SelfCheckPayload | undefined) {
  const names = ['nvs', 'wifi', 'u1_uart', 'audio']
  const checks: Record<string, SelfCheckItem> = {}
  const c = payload?.checks
  if (Array.isArray(c)) {
    c.forEach((i) => {
      if (i?.name)
        checks[i.name] = i
    })
  }
  else if (c && typeof c === 'object') {
    Object.entries(c).forEach(([n, v]) => {
      checks[n] = v && typeof v === 'object' ? { name: n, ...(v as SelfCheckItem) } : { name: n, status: String(v) }
    })
  }
  return `${payload?.scope || 'startup'} ${names.map(n => `${n}:${checks[n]?.status || 'missing'}`).join(' ')}`
}

async function loadSelfCheckHistoryData() {
  try {
    selfCheckHistory.value = await v2ListSelfCheckHistory(deviceId.value)
    const latest = selfCheckHistory.value[0]
    if (latest) {
      latestDiagnosticStatus.value = latest.status || 'pending'
      latestDiagnosticSummary.value = latest.summary || latest.checksJson || 'Self-check history loaded'
      latestDiagnosticAt.value = latest.reportedAt ? new Date(latest.reportedAt).toLocaleString() : ''
    }
  }
  catch (e) {
    console.warn('load self-check history failed:', e)
    selfCheckHistory.value = []
  }
}

// --- Computed labels ---
const phaseColor = computed(() => {
  if (['running', 'accepted', 'progress'].includes(latestPhase.value))
    return 'var(--accent)'
  if (latestPhase.value === 'done')
    return 'var(--green)'
  if (latestPhase.value === 'failed')
    return 'var(--danger)'
  return 'var(--muted)'
})
const workspaceLabel = computed(() => {
  const ws = normalizeWorkspace(deviceInfo.value?.workspaceMm as any)
  return ws ? `${t('v2.detail.workspace')} X ${ws.x} / Y ${ws.y} / Z ${ws.z} mm` : `${t('v2.detail.workspace')} —`
})
const paperSlotStateLabel = computed(() => {
  const s = deviceSupplies.value?.paperSlotState || 'unknown'
  return s === 'loaded' ? t('v2.detail.paperLoaded') : s === 'empty' ? t('v2.detail.paperEmpty') : t('v2.detail.unknown')
})
const penStateLabel = computed(() => {
  if (!deviceSupplies.value?.penInstalledAt)
    return t('v2.detail.noPenRecord')
  return `${t('v2.detail.inkEstimate')} ${deviceSupplies.value.penInkPercentEst ?? 100}%`
})
const transferStateLabel = computed(() => {
  if (!deviceTransfer.value)
    return t('v2.detail.noTransferPending')
  return `#${deviceTransfer.value.transferId} ${deviceTransfer.value.status}`
})

// --- Actions ---
async function handleHome() {
  homeLoading.value = true
  try {
    const r = await v2SubmitTask(deviceId.value, 'home')
    showSubmitToast('v2.detail.homeSubmitted')
    appendLog(`home: ${r.taskId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    homeLoading.value = false
  }
}
// 语音指令派发成功（voice-command 组件内部已确认 + 派发）。
function handleVoiceDispatched(taskId: string, capability: string) {
  showSubmitToast('v2.detail.voiceSubmitted')
  appendLog(`voice ${capability}: ${taskId}`)
}
function handleVoiceError(msg: string) {
  message.alert(msg)
}
async function handleWriteText() {
  const text = writeTextInput.value.trim()
  if (!text) {
    message.alert(t('v2.detail.enterWriteText'))
    return
  }
  writeTextLoading.value = true
  try {
    const r = await v2SubmitTask(deviceId.value, 'write_text', { text, font_id: defaultWriteTextFontId })
    latestPhase.value = r.status
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
    showSubmitToast('v2.detail.writeSubmitted')
    appendLog(`write_text: ${r.taskId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    writeTextLoading.value = false
  }
}
async function submitDraw(params: Record<string, unknown>, label: string) {
  drawGeneratedLoading.value = true
  try {
    const r = await v2SubmitTask(deviceId.value, 'draw_generated', params)
    latestPhase.value = r.status
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
    showSubmitToast('v2.detail.drawSubmitted')
    appendLog(`${label}: ${r.taskId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    drawGeneratedLoading.value = false
  }
}
async function handleDrawPrompt() {
  const p = drawPromptInput.value.trim()
  if (!p) {
    message.alert(t('v2.detail.enterDrawPrompt'))
    return
  }
  await submitDraw({ prompt: p }, 'draw_generated')
}
async function handleDrawStarter(id: string) {
  await submitDraw({ starter_id: id, use_starter_asset: true }, `draw_starter ${id}`)
}
async function handleRefreshInfo() {
  infoLoading.value = true
  clearInfoLoadingTimer()
  try {
    const r = await v2SubmitTask(deviceId.value, 'get_device_info')
    latestPhase.value = r.status
    showSubmitToast('v2.detail.infoSubmitted')
    appendLog(`get_device_info: ${r.taskId}`)
    infoLoadingTimer = setTimeout(() => {
      infoLoading.value = false
    }, 12000)
  }
  catch (e: any) {
    infoLoading.value = false
    message.alert(taskSubmitErrorMessage(e))
  }
}
async function handleHealthCheck() {
  healthCheckLoading.value = true
  try {
    const r = await v2SubmitTask(deviceId.value, 'run_path', { path: healthCheckPath, feed: 900 })
    latestPhase.value = r.status
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
    latestDiagnosticSummary.value = 'Manual run_path submitted'
    showSubmitToast('v2.detail.healthCheckSubmitted')
    appendLog(`health_check: ${r.taskId}`)
  }
  catch (e: any) {
    healthCheckLoading.value = false
    message.alert(taskSubmitErrorMessage(e))
  }
}
async function updatePaper(state: 'empty' | 'loaded' | 'unknown') {
  suppliesLoading.value = true
  try {
    deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId.value, { paperSlotState: state })
    showSubmitToast(state === 'loaded' ? 'v2.detail.paperMarkedLoaded' : state === 'empty' ? 'v2.detail.paperMarkedEmpty' : 'v2.detail.paperMarkedUnknown')
    appendLog(`supplies paper=${state}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    suppliesLoading.value = false
  }
}
async function markNewPen() {
  suppliesLoading.value = true
  try {
    deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId.value, { penInstalledAt: new Date().toISOString(), penInkPercentEst: 100, resetPenMileage: true })
    showSubmitToast('v2.detail.penRecorded')
    appendLog('supplies pen installed')
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    suppliesLoading.value = false
  }
}
async function handleRequestTransfer() {
  const target = transferTargetPhone.value.trim()
  if (!target) {
    message.alert(t('v2.detail.enterTargetPhone'))
    return
  }
  transferLoading.value = true
  try {
    deviceTransfer.value = await v2RequestDeviceTransfer(deviceId.value, { targetPhone: target })
    transferAcceptId.value = String(deviceTransfer.value.transferId)
    showSubmitToast('v2.detail.transferCreated')
    appendLog(`transfer id=${deviceTransfer.value.transferId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    transferLoading.value = false
  }
}
async function handleCancelTransfer() {
  const tid = transferAcceptId.value.trim() || String(deviceTransfer.value?.transferId || '') || null
  if (!tid) {
    message.alert(t('v2.detail.enterTransferId'))
    return
  }
  transferLoading.value = true
  try {
    deviceTransfer.value = await v2CancelDeviceTransfer(tid)
    showSubmitToast('v2.detail.transferCancelled')
    appendLog(`transfer cancelled`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    transferLoading.value = false
  }
}
async function handleAcceptTransfer() {
  const tid = transferAcceptId.value.trim() || String(deviceTransfer.value?.transferId || '') || null
  if (!tid) {
    message.alert(t('v2.detail.enterTransferId'))
    return
  }
  transferLoading.value = true
  try {
    deviceTransfer.value = await v2AcceptDeviceTransfer(tid)
    showSubmitToast('v2.detail.transferAccepted')
    appendLog(`transfer accepted`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    transferLoading.value = false
  }
}

function navigateBack() {
  uni.navigateBack()
}
function goToChatHistory() {
  uni.navigateTo({ url: `/pages/chat-history/index?deviceId=${deviceId.value || ''}` })
}
function goToVoiceprint() {
  uni.navigateTo({ url: `/pages/voiceprint/index?deviceId=${deviceId.value || ''}` })
}
function goToAgents() {
  uni.navigateTo({ url: '/pages/index/index' })
}

// ── 设备分享（AUDIT gap 实现）──
async function loadShares() {
  if (!deviceId.value)
    return
  try {
    shares.value = await v2ListShares(deviceId.value)
  }
  catch (e: any) {
    console.warn('load shares failed:', e?.message || e)
  }
}

async function handleCreateShare() {
  if (!deviceId.value)
    return
  shareLoading.value = true
  try {
    const days = Number.parseInt(shareExpiry.value.replace('d', ''), 10) || 7
    const expiresAt = new Date(Date.now() + days * 86400000).toISOString()
    await v2CreateShare(deviceId.value, sharePermission.value, expiresAt)
    uni.showToast({ title: t('v2.detail.shareCreated'), icon: 'success' })
    await loadShares()
  }
  catch (e: any) {
    message.alert(e?.message || t('v2.detail.shareFailed'))
  }
  finally {
    shareLoading.value = false
  }
}

async function handleRevokeShare(shareToken: string) {
  if (!deviceId.value)
    return
  shareLoading.value = true
  try {
    await v2RevokeShare(deviceId.value, shareToken)
    uni.showToast({ title: t('v2.detail.shareRevoked'), icon: 'success' })
    await loadShares()
  }
  catch (e: any) {
    message.alert(e?.message || t('v2.detail.revokeFailed'))
  }
  finally {
    shareLoading.value = false
  }
}

// ── 设备解绑 ──
async function handleUnbind() {
  if (!deviceId.value)
    return
  uni.vibrateShort({ type: 'medium' })
  try {
    const confirmed = await message.confirm(t('v2.detail.unbindConfirm'))
    if (!confirmed)
      return
  }
  catch {
    return
  }
  unbindLoading.value = true
  try {
    await v2UnbindDevice(deviceId.value)
    uni.showToast({ title: t('v2.detail.unbindSuccess'), icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  }
  catch (e: any) {
    message.alert(e?.message || t('v2.detail.unbindFailed'))
  }
  finally {
    unbindLoading.value = false
  }
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
  wsConnect()
})
onUnmounted(() => {
  clearInfoLoadingTimer()
})
</script>

<template>
  <wd-config-provider theme-color="var(--accent)" />
  <wd-navbar :title="t('v2.deviceDetail.title')" safe-area-inset-top left-arrow placeholder fixed @click-left="navigateBack" />

  <view class="bento-page page-enter">
    <device-info-card :device-info="deviceInfo" :device-id="deviceId" :connected="connected" :workspace-label="workspaceLabel" :info-loading="infoLoading" />
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
    <write-draw-panel v-model:write-text-input="writeTextInput" v-model:draw-prompt-input="drawPromptInput" :write-text-loading="writeTextLoading" :draw-generated-loading="drawGeneratedLoading" :starter-assets="starterAssets" :default-font-id="defaultWriteTextFontId" @write-text="handleWriteText" @draw-prompt="handleDrawPrompt" @draw-starter="handleDrawStarter" />
    <voice-command :device-id="deviceId" @dispatched="handleVoiceDispatched" @error="handleVoiceError" />
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
        <view class="quick-link" @click="goToChatHistory">
          <wd-icon name="chat" size="20" color="var(--muted)" />
          <text>{{ t('v2.detail.chatHistory') }}</text>
          <wd-icon name="arrow-right" size="14" color="#c7c7cc" custom-class="ml-auto" />
        </view>
        <view class="quick-link" @click="goToVoiceprint">
          <wd-icon name="volume" size="20" color="var(--muted)" />
          <text>{{ t('v2.detail.voiceprintMgmt') }}</text>
          <wd-icon name="arrow-right" size="14" color="#c7c7cc" custom-class="ml-auto" />
        </view>
        <view class="quick-link" @click="goToAgents">
          <wd-icon name="robot" size="20" color="var(--muted)" />
          <text>{{ t('v2.detail.manageAgents') }}</text>
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
