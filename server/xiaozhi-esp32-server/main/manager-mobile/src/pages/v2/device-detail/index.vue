<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { t } from '@/i18n'
import {
  v2AcceptDeviceTransfer, v2CancelDeviceTransfer,
  v2GetDeviceInfo, v2ListSelfCheckHistory,
  v2RequestDeviceTransfer, v2SubmitTask, v2UpdateDeviceSupplies,
} from '@/api/v2'
import type { V2DeviceInfo, V2DeviceSupplyResponse, V2DeviceTransferResponse, V2SelfCheckHistoryResponse } from '@/api/v2/types'
import { useDeviceWebSocket } from './composables/useDeviceWebSocket'
import { useVoiceApproval } from './composables/useVoiceApproval'
import DeviceInfoCard from './components/device-info-card.vue'
import SuppliesPanel from './components/supplies-panel.vue'
import TransferPanel from './components/transfer-panel.vue'
import VoiceApproval from './components/voice-approval.vue'
import TaskStatus from './components/task-status.vue'
import HealthCheck from './components/health-check.vue'
import WriteDrawPanel from './components/write-draw-panel.vue'

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
const transferTargetUnionid = ref('')
const transferAcceptId = ref('')
const latestPhase = ref('—')
const latestProgressPercent = ref<number | null>(null)
const latestProgressLabel = ref('')
const latestDeviceInfoTaskId = ref('')
const latestDiagnosticStatus = ref('pending')
const latestDiagnosticSummary = ref('No self-check result yet')
const latestDiagnosticAt = ref('')
const selfCheckHistory = ref<V2SelfCheckHistoryResponse[]>([])
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
  { cmd: 'M', x: 5, y: 5, z: 0 }, { cmd: 'L', x: 25, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 25, z: 0 }, { cmd: 'L', x: 5, y: 25, z: 0 }, { cmd: 'L', x: 5, y: 5, z: 0 },
]

// --- Types ---
type WorkspaceMm = { x?: number | string; y?: number | string; z?: number | string }
type MotionProgress = { done_segments?: number | string; total_segments?: number | string; percent?: number | string }
interface DeviceInfoReplyPayload { model?: string; hw_rev?: string; fw_rev?: string; workspace_mm?: WorkspaceMm | string }
interface JobStatusPayload { phase?: string; capability?: string; progress?: MotionProgress }
type SelfCheckStatus = 'pass' | 'warn' | 'fail' | 'pending' | string
type SelfCheckItem = { name?: string; status?: SelfCheckStatus; detail?: string }
interface SelfCheckPayload { check_id?: string; scope?: string; status?: SelfCheckStatus; checks?: SelfCheckItem[] | Record<string, SelfCheckItem | SelfCheckStatus | string> }

// --- Error messages ---
function taskSubmitErrorMessage(error: any) {
  const text = String(error?.message || error || '')
  if (text.includes('E_RUNTIME_STALE')) return t('v2.detail.errorRuntimeStale')
  if (text.includes('E_CONTENT_BLOCKED')) return t('v2.detail.errorContentBlocked')
  if (text.includes('E_INVALID_DRAWING')) return t('v2.detail.errorInvalidDrawing')
  if (text.includes('E_NOT_ENTITLED')) return t('v2.detail.errorNotEntitled')
  if (text.includes('E_NO_PAPER')) return t('v2.detail.errorNoPaper')
  return text || t('common.fail')
}
function showSubmitToast(key: string) { uni.showToast({ title: t(key), icon: 'none' }) }
function clearInfoLoadingTimer() { if (infoLoadingTimer) { clearTimeout(infoLoadingTimer); infoLoadingTimer = null } }

// --- WebSocket ---
const { connected, logLines, latestEvent, connect: wsConnect, appendLog } = useDeviceWebSocket(deviceId)

// --- Voice approval composable ---
const {
  pendingVoiceTasks, voiceApprovalLoading, pendingVoiceApprovalCount, pendingVoiceApprovalBadgeText,
  voiceprintApprovalLabel, voiceprintReenrollRequired, voiceprintHasUnknownSpeaker,
  loadPendingVoiceTasks, handleApproveVoiceTask, handleRejectVoiceTask,
} = useVoiceApproval(() => deviceId.value, appendLog, message, taskSubmitErrorMessage)

watch(latestEvent, (event) => { if (event) handleEdgeAEvent(event) })

function handleEdgeAEvent(event: any) {
  const payload = event?.payload as JobStatusPayload | DeviceInfoReplyPayload | SelfCheckPayload | undefined
  if (event?.event_type === 'device_info_reply') { applyDeviceInfoReply(event, payload as DeviceInfoReplyPayload); return }
  if (event?.event_type === 'self_check') { applySelfCheck(event, payload as SelfCheckPayload); return }
  const jobPayload = payload as JobStatusPayload | undefined
  appendLog(`seq=${event?.seq} ${jobPayload?.capability || ''} ${jobPayload?.phase || ''}`)
  applyTaskProgress(jobPayload)
  if (jobPayload?.phase) latestPhase.value = jobPayload.phase
}

function applyTaskProgress(payload: JobStatusPayload | undefined) {
  if (!payload?.phase) return
  if (['done', 'failed', 'cancelled', 'rejected'].includes(payload.phase)) healthCheckLoading.value = false
  if (payload.phase === 'progress' && payload.progress) {
    const pct = clampPercent(Number(payload.progress.percent || 0))
    const done = Number(payload.progress.done_segments || 0)
    const total = Number(payload.progress.total_segments || 0)
    latestProgressPercent.value = pct
    latestProgressLabel.value = total > 0 ? `${pct}% (${done}/${total})` : `${pct}%`
    return
  }
  if (payload.phase === 'done') { latestProgressPercent.value = 100; latestProgressLabel.value = '100%'; return }
  if (['accepted', 'running'].includes(payload.phase)) { latestProgressPercent.value = null; latestProgressLabel.value = ''; return }
  if (['failed', 'cancelled', 'rejected'].includes(payload.phase)) { latestProgressPercent.value = null; latestProgressLabel.value = '' }
}
function clampPercent(v: number) { return Number.isFinite(v) ? Math.max(0, Math.min(100, Math.round(v))) : 0 }
const progressBarStyle = computed(() => `width:${latestProgressPercent.value ?? 0}%;`)

function normalizeWorkspace(value: WorkspaceMm | string | undefined) {
  if (!value) return null
  if (typeof value === 'string') { try { return normalizeWorkspace(JSON.parse(value)) } catch { return null } }
  return { x: Number(value.x || 0), y: Number(value.y || 0), z: Number(value.z || 0) }
}

function applyDeviceInfoReply(event: any, payload: DeviceInfoReplyPayload) {
  const ws = normalizeWorkspace(payload?.workspace_mm)
  deviceInfo.value = {
    deviceId: event?.device_id || deviceId.value, model: payload?.model || deviceInfo.value?.model || '',
    hwRev: payload?.hw_rev || deviceInfo.value?.hwRev || '', fwRev: payload?.fw_rev || deviceInfo.value?.fwRev || '',
    workspaceMm: ws || deviceInfo.value?.workspaceMm || { x: 0, y: 0, z: 0 },
    status: deviceInfo.value?.status || 'online',
    lastSeenAt: event?.ts ? new Date(event.ts).toISOString() : new Date().toISOString(),
  }
  latestDeviceInfoTaskId.value = event?.task_id || ''; infoLoading.value = false; clearInfoLoadingTimer()
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
  if (Array.isArray(c)) c.forEach(i => { if (i?.name) checks[i.name] = i })
  else if (c && typeof c === 'object') Object.entries(c).forEach(([n, v]) => { checks[n] = v && typeof v === 'object' ? { name: n, ...(v as SelfCheckItem) } : { name: n, status: String(v) } })
  return `${payload?.scope || 'startup'} ${names.map(n => `${n}:${checks[n]?.status || 'missing'}`).join(' ')}`
}

async function loadSelfCheckHistoryData() {
  try {
    selfCheckHistory.value = await v2ListSelfCheckHistory(deviceId.value)
    const latest = selfCheckHistory.value[0]
    if (latest) { latestDiagnosticStatus.value = latest.status || 'pending'; latestDiagnosticSummary.value = latest.summary || latest.checksJson || 'Self-check history loaded'; latestDiagnosticAt.value = latest.reportedAt ? new Date(latest.reportedAt).toLocaleString() : '' }
  } catch (e) { console.warn('load self-check history failed:', e); selfCheckHistory.value = [] }
}

// --- Computed labels ---
const phaseColor = computed(() => { if (['running', 'accepted', 'progress'].includes(latestPhase.value)) return '#336cff'; if (latestPhase.value === 'done') return '#07c160'; if (latestPhase.value === 'failed') return '#ee0a24'; return '#999' })
const workspaceLabel = computed(() => { const ws = normalizeWorkspace(deviceInfo.value?.workspaceMm as any); return ws ? `${t('v2.detail.workspace')} X ${ws.x} / Y ${ws.y} / Z ${ws.z} mm` : `${t('v2.detail.workspace')} —` })
const paperSlotStateLabel = computed(() => { const s = deviceSupplies.value?.paperSlotState || 'unknown'; return s === 'loaded' ? t('v2.detail.paperLoaded') : s === 'empty' ? t('v2.detail.paperEmpty') : t('v2.detail.unknown') })
const penStateLabel = computed(() => { if (!deviceSupplies.value?.penInstalledAt) return t('v2.detail.noPenRecord'); return `${t('v2.detail.inkEstimate')} ${deviceSupplies.value.penInkPercentEst ?? 100}%` })
const transferStateLabel = computed(() => { if (!deviceTransfer.value) return t('v2.detail.noTransferPending'); return `#${deviceTransfer.value.transferId} ${deviceTransfer.value.status}` })

// --- Actions ---
async function handleHome() { homeLoading.value = true; try { const r = await v2SubmitTask(deviceId.value, 'home'); showSubmitToast('v2.detail.homeSubmitted'); appendLog(`home: ${r.taskId}`) } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { homeLoading.value = false } }
async function handleWriteText() { const text = writeTextInput.value.trim(); if (!text) { message.alert(t('v2.detail.enterWriteText')); return }; writeTextLoading.value = true; try { const r = await v2SubmitTask(deviceId.value, 'write_text', { text, font_id: defaultWriteTextFontId }); latestPhase.value = r.status; latestProgressPercent.value = null; latestProgressLabel.value = ''; showSubmitToast('v2.detail.writeSubmitted'); appendLog(`write_text: ${r.taskId}`) } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { writeTextLoading.value = false } }
async function submitDraw(params: Record<string, unknown>, label: string) { drawGeneratedLoading.value = true; try { const r = await v2SubmitTask(deviceId.value, 'draw_generated', params); latestPhase.value = r.status; latestProgressPercent.value = null; latestProgressLabel.value = ''; showSubmitToast('v2.detail.drawSubmitted'); appendLog(`${label}: ${r.taskId}`) } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { drawGeneratedLoading.value = false } }
async function handleDrawPrompt() { const p = drawPromptInput.value.trim(); if (!p) { message.alert(t('v2.detail.enterDrawPrompt')); return }; await submitDraw({ prompt: p }, 'draw_generated') }
async function handleDrawStarter(id: string) { await submitDraw({ starter_id: id, use_starter_asset: true }, `draw_starter ${id}`) }
async function handleRefreshInfo() { infoLoading.value = true; clearInfoLoadingTimer(); try { const r = await v2SubmitTask(deviceId.value, 'get_device_info'); latestPhase.value = r.status; latestDeviceInfoTaskId.value = r.taskId; showSubmitToast('v2.detail.infoSubmitted'); appendLog(`get_device_info: ${r.taskId}`); infoLoadingTimer = setTimeout(() => { infoLoading.value = false }, 12000) } catch (e: any) { infoLoading.value = false; message.alert(taskSubmitErrorMessage(e)) } }
async function handleHealthCheck() { healthCheckLoading.value = true; try { const r = await v2SubmitTask(deviceId.value, 'run_path', { path: healthCheckPath, feed: 900 }); latestPhase.value = r.status; latestProgressPercent.value = null; latestProgressLabel.value = ''; latestDiagnosticSummary.value = 'Manual run_path submitted'; showSubmitToast('v2.detail.healthCheckSubmitted'); appendLog(`health_check: ${r.taskId}`) } catch (e: any) { healthCheckLoading.value = false; message.alert(taskSubmitErrorMessage(e)) } }
async function updatePaper(state: 'empty' | 'loaded' | 'unknown') { suppliesLoading.value = true; try { deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId.value, { paperSlotState: state }); showSubmitToast(state === 'loaded' ? 'v2.detail.paperMarkedLoaded' : state === 'empty' ? 'v2.detail.paperMarkedEmpty' : 'v2.detail.paperMarkedUnknown'); appendLog(`supplies paper=${state}`) } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { suppliesLoading.value = false } }
async function markNewPen() { suppliesLoading.value = true; try { deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId.value, { penInstalledAt: new Date().toISOString(), penInkPercentEst: 100, resetPenMileage: true }); showSubmitToast('v2.detail.penRecorded'); appendLog('supplies pen installed') } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { suppliesLoading.value = false } }
async function handleRequestTransfer() { const target = transferTargetUnionid.value.trim(); if (!target) { message.alert(t('v2.detail.enterTargetUnionid')); return }; transferLoading.value = true; try { deviceTransfer.value = await v2RequestDeviceTransfer(deviceId.value, { targetUnionid: target }); transferAcceptId.value = String(deviceTransfer.value.transferId); showSubmitToast('v2.detail.transferCreated'); appendLog(`transfer id=${deviceTransfer.value.transferId}`) } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { transferLoading.value = false } }
async function handleCancelTransfer() { const tid = transferAcceptId.value.trim() || String(deviceTransfer.value?.transferId || '') || null; if (!tid) { message.alert(t('v2.detail.enterTransferId')); return }; transferLoading.value = true; try { deviceTransfer.value = await v2CancelDeviceTransfer(tid); showSubmitToast('v2.detail.transferCancelled'); appendLog(`transfer cancelled`) } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { transferLoading.value = false } }
async function handleAcceptTransfer() { const tid = transferAcceptId.value.trim() || String(deviceTransfer.value?.transferId || '') || null; if (!tid) { message.alert(t('v2.detail.enterTransferId')); return }; transferLoading.value = true; try { deviceTransfer.value = await v2AcceptDeviceTransfer(tid); showSubmitToast('v2.detail.transferAccepted'); appendLog(`transfer accepted`) } catch (e: any) { message.alert(taskSubmitErrorMessage(e)) } finally { transferLoading.value = false } }

function goToChatHistory() { uni.navigateTo({ url: '/pages/chat-history/index?agentId=default' }) }
function goToVoiceprint() { uni.navigateTo({ url: '/pages/voiceprint/index?agentId=default' }) }
function goToAgents() { uni.switchTab({ url: '/pages/index/index' }) }

onLoad((opt: any) => { deviceId.value = opt?.deviceId || '' })
onMounted(async () => { if (!deviceId.value) return; try { deviceInfo.value = await v2GetDeviceInfo(deviceId.value) } catch (e) { console.warn('device info load failed:', e) }; await loadPendingVoiceTasks(); await loadSelfCheckHistoryData(); wsConnect() })
onUnmounted(() => { clearInfoLoadingTimer() })
</script>

<template>
  <wd-config-provider theme-color="#336cff" />
  <wd-navbar :title="t('v2.deviceDetail.title')" left-arrow fixed placeholder safe-area-inset-top @click-left="uni.navigateBack()" />

  <device-info-card :device-info="deviceInfo" :device-id="deviceId" :connected="connected" :workspace-label="workspaceLabel" :info-loading="infoLoading" />
  <supplies-panel :device-supplies="deviceSupplies" :paper-slot-state-label="paperSlotStateLabel" :pen-state-label="penStateLabel" v-model:supplies-loading="suppliesLoading" @update-paper="updatePaper" @new-pen="markNewPen" />
  <transfer-panel :device-transfer="deviceTransfer" :transfer-state-label="transferStateLabel" v-model:transfer-loading="transferLoading" v-model:transfer-target-unionid="transferTargetUnionid" v-model:transfer-accept-id="transferAcceptId" @request-transfer="handleRequestTransfer" @cancel-transfer="handleCancelTransfer" @accept-transfer="handleAcceptTransfer" />
  <voice-approval :pending-voice-tasks="pendingVoiceTasks" :pending-voice-approval-count="pendingVoiceApprovalCount" :pending-voice-approval-badge-text="pendingVoiceApprovalBadgeText" :voiceprint-approval-label="voiceprintApprovalLabel" :voiceprint-reenroll-required="voiceprintReenrollRequired" :voiceprint-has-unknown-speaker="voiceprintHasUnknownSpeaker" v-model:voice-approval-loading="voiceApprovalLoading" @refresh-voice-tasks="loadPendingVoiceTasks" @approve="handleApproveVoiceTask" @reject="handleRejectVoiceTask" />
  <task-status :latest-phase="latestPhase" :latest-progress-percent="latestProgressPercent" :latest-progress-label="latestProgressLabel" :phase-color="phaseColor" :progress-bar-style="progressBarStyle" />
  <health-check :latest-diagnostic-status="latestDiagnosticStatus" :latest-diagnostic-summary="latestDiagnosticSummary" :latest-diagnostic-at="latestDiagnosticAt" :self-check-history="selfCheckHistory" v-model:health-check-loading="healthCheckLoading" @run-health-check="handleHealthCheck" />

  <view class="mx-[20rpx] mt-[20rpx] flex gap-[20rpx]">
    <wd-button type="primary" block round size="large" :loading="homeLoading" custom-class="!h-[96rpx] !text-[32rpx]" @click="handleHome">
      {{ homeLoading ? t('v2.deviceDetail.homing') : t('v2.deviceDetail.homeButton') }}
    </wd-button>
    <wd-button type="info" block round size="large" :loading="infoLoading" custom-class="!h-[96rpx] !text-[32rpx]" @click="handleRefreshInfo">
      {{ infoLoading ? t('v2.detail.querying') : t('v2.detail.refreshInfo') }}
    </wd-button>
  </view>

  <write-draw-panel :write-text-loading="writeTextLoading" :draw-generated-loading="drawGeneratedLoading" :starter-assets="starterAssets" :default-font-id="defaultWriteTextFontId" v-model:write-text-input="writeTextInput" v-model:draw-prompt-input="drawPromptInput" @write-text="handleWriteText" @draw-prompt="handleDrawPrompt" @draw-starter="handleDrawStarter" />

  <wd-cell-group border custom-class="!mt-[20rpx]" :title="t('v2.detail.quickLinks')">
    <wd-cell :title="t('v2.detail.chatHistory')" is-link @click="goToChatHistory" />
    <wd-cell :title="t('v2.detail.voiceprintMgmt')" is-link @click="goToVoiceprint" />
    <wd-cell :title="t('v2.detail.manageAgents')" is-link @click="goToAgents" />
  </wd-cell-group>

  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="connected ? t('v2.detail.wssSubscribed') : t('v2.detail.wssDisconnected')" center>
      <template v-if="!connected" #value>
        <wd-button type="text" size="small" @click="wsConnect">{{ t('v2.deviceDetail.connectAndSubscribe') }}</wd-button>
      </template>
    </wd-cell>
  </wd-cell-group>
  <scroll-view scroll-y class="bg-[#f5f5f5] rounded-[8rpx] mx-[20rpx] p-[16rpx]" style="max-height:300rpx">
    <wd-text v-for="(l, i) in logLines" :key="i" :text="l" size="20rpx" color="#666" custom-class="!leading-[36rpx]" />
    <wd-text v-if="!logLines.length" :text="t('v2.detail.waitingEvents')" size="24rpx" color="#999" />
  </scroll-view>
</template>
