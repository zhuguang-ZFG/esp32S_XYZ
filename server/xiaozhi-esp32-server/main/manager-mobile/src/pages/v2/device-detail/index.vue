<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { t } from '@/i18n'
import { buildEdgeAClientWsUrl, updateM6PendingTabBarBadge } from '@/utils'
import { v2AcceptDeviceTransfer, v2ApproveVoiceTask, v2CancelDeviceTransfer, v2GetDeviceInfo, v2ListPendingVoiceTasks, v2ListSelfCheckHistory, v2RejectVoiceTask, v2RequestDeviceTransfer, v2SubmitTask, v2UpdateDeviceSupplies } from '@/api/v2'
import type { V2DeviceInfo, V2DeviceSupplyResponse, V2DeviceTransferResponse, V2PendingVoiceTaskResponse, V2SelfCheckHistoryResponse } from '@/api/v2/types'

defineOptions({ name: 'V2DeviceDetail' })
const message = useMessage()

const deviceId = ref('')
const deviceInfo = ref<V2DeviceInfo | null>(null)
const homeLoading = ref(false)
const infoLoading = ref(false)
const writeTextLoading = ref(false)
const writeTextInput = ref('你好')
const drawGeneratedLoading = ref(false)
const healthCheckLoading = ref(false)
const suppliesLoading = ref(false)
const transferLoading = ref(false)
const voiceApprovalLoading = ref(false)
const drawPromptInput = ref('星星')
const deviceSupplies = ref<V2DeviceSupplyResponse | null>(null)
const deviceTransfer = ref<V2DeviceTransferResponse | null>(null)
const pendingVoiceTasks = ref<V2PendingVoiceTaskResponse[]>([])
const pendingVoiceApprovalCount = computed(() => pendingVoiceTasks.value.length)
const pendingVoiceApprovalBadgeText = computed(() => String(pendingVoiceApprovalCount.value))
const transferTargetUnionid = ref('')
const transferAcceptId = ref('')
const wsConnected = ref(false)
const logLines = ref<string[]>([])
const latestPhase = ref('—')
const latestProgressPercent = ref<number | null>(null)
const latestProgressLabel = ref('')
const latestDeviceInfoTaskId = ref('')
const latestDiagnosticStatus = ref('pending')
const latestDiagnosticSummary = ref('No self-check result yet')
const latestDiagnosticAt = ref('')
const selfCheckHistory = ref<V2SelfCheckHistoryResponse[]>([])
let socketTask: UniApp.SocketTask | null = null
let infoLoadingTimer: ReturnType<typeof setTimeout> | null = null
const runtimeStatusRefreshMessage = '设备状态正在刷新，请稍后重试'
const contentBlockedMessage = '内容不适合绘制，请换一段文字或图案'
const invalidDrawingMessage = '图案暂时无法绘制，请换一个更简单的图案'
const entitlementRequiredMessage = '这个资源还未开通，请选择已开通的字体或图案'
const noPaperMessage = '纸张未就绪，请先在耗材状态中标记纸张已放好'
const defaultWriteTextFontId = 'kai_basic_v1'
const starterAssets = [
  { id: 'starter_star', label: '星星' },
  { id: 'starter_house', label: '小房子' },
  { id: 'starter_tree', label: '树' },
  { id: 'starter_fish', label: '鱼' },
  { id: 'starter_flower', label: '花' },
]

type WorkspaceMm = { x?: number | string; y?: number | string; z?: number | string }
type MotionProgress = { done_segments?: number | string; total_segments?: number | string; percent?: number | string }

interface DeviceInfoReplyPayload {
  model?: string
  hw_rev?: string
  fw_rev?: string
  workspace_mm?: WorkspaceMm | string
}

interface JobStatusPayload {
  phase?: string
  capability?: string
  progress?: MotionProgress
}

type SelfCheckStatus = 'pass' | 'warn' | 'fail' | 'pending' | string
type SelfCheckItem = { name?: string; status?: SelfCheckStatus; detail?: string }

interface SelfCheckPayload {
  check_id?: string
  scope?: string
  status?: SelfCheckStatus
  checks?: SelfCheckItem[] | Record<string, SelfCheckItem | SelfCheckStatus | string>
}

interface VoiceprintConstraint {
  matched?: boolean
  reason?: string
  member_id?: number | string
  memberId?: number | string
  display_name?: string
  displayName?: string
  member_type?: string
  memberType?: string
  speaker_ref?: string
  speakerRef?: string
  reenroll_hint?: boolean
  reenrollHint?: boolean
}

const healthCheckPath = [
  { cmd: 'M', x: 5, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 5, z: 0 },
]

onLoad((opt: any) => { deviceId.value = opt?.deviceId || '' })

onMounted(async () => {
  if (!deviceId.value) return
  try { deviceInfo.value = await v2GetDeviceInfo(deviceId.value) } catch { /* offline ok */ }
  await loadPendingVoiceTasks()
  await loadSelfCheckHistory()
  connectWs()
})

onUnmounted(() => {
  socketTask?.close({})
  socketTask = null
  clearInfoLoadingTimer()
})

function connectWs() {
  const url = buildEdgeAClientWsUrl()
  const token = uni.getStorageSync('token') || ''
  appendLog(`→ ${url}`)
  socketTask = uni.connectSocket({ url, header: { Authorization: `Bearer ${token}` } }) as unknown as UniApp.SocketTask
  socketTask.onOpen(() => { wsConnected.value = true; appendLog('connected'); socketTask?.send({ data: JSON.stringify({ op: 'auth', token }) }) })
  socketTask.onMessage(({ data }) => {
    try {
      const m = typeof data === 'string' ? JSON.parse(data) : data
      if (m.type === 'authed') { appendLog('authed'); socketTask?.send({ data: JSON.stringify({ op: 'subscribe_device', device_id: deviceId.value }) }) }
      else if (m.type === 'subscribed') appendLog(`subscribed ${m.topic}`)
      else if (m.type === 'event') handleEdgeAEvent(m.event)
      else if (m.type === 'pong') appendLog('pong')
      else if (m.type === 'error') appendLog(`error: ${m.code}`)
    } catch { appendLog(`raw: ${String(data).slice(0, 120)}`) }
  })
  socketTask.onClose(() => { wsConnected.value = false; appendLog('disconnected') })
  socketTask.onError(() => { wsConnected.value = false; appendLog('transport error') })
}

function appendLog(msg: string) {
  logLines.value.push(`[${new Date().toLocaleTimeString()}] ${msg}`)
  if (logLines.value.length > 30) logLines.value = logLines.value.slice(-30)
}

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
    const percent = clampPercent(Number(payload.progress.percent || 0))
    const done = Number(payload.progress.done_segments || 0)
    const total = Number(payload.progress.total_segments || 0)
    latestProgressPercent.value = percent
    latestProgressLabel.value = total > 0 ? `${percent}% (${done}/${total})` : `${percent}%`
    return
  }
  if (payload.phase === 'done') {
    latestProgressPercent.value = 100
    latestProgressLabel.value = '100%'
    return
  }
  if (payload.phase === 'accepted' || payload.phase === 'running') {
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
    return
  }
  if (['failed', 'cancelled', 'rejected'].includes(payload.phase)) {
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
  }
}

function clampPercent(value: number) {
  if (!Number.isFinite(value))
    return 0
  return Math.max(0, Math.min(100, Math.round(value)))
}

const progressBarStyle = computed(() => {
  const percent = latestProgressPercent.value ?? 0
  return `width:${percent}%;`
})

function applyDeviceInfoReply(event: any, payload: DeviceInfoReplyPayload) {
  const workspaceMm = normalizeWorkspace(payload?.workspace_mm)
  deviceInfo.value = {
    deviceId: event?.device_id || deviceId.value,
    model: payload?.model || deviceInfo.value?.model || '',
    hwRev: payload?.hw_rev || deviceInfo.value?.hwRev || '',
    fwRev: payload?.fw_rev || deviceInfo.value?.fwRev || '',
    workspaceMm: workspaceMm || deviceInfo.value?.workspaceMm || { x: 0, y: 0, z: 0 },
    status: deviceInfo.value?.status || 'online',
    lastSeenAt: event?.ts ? new Date(event.ts).toISOString() : new Date().toISOString(),
  }
  latestDeviceInfoTaskId.value = event?.task_id || ''
  infoLoading.value = false
  clearInfoLoadingTimer()
  appendLog(`device_info_reply seq=${event?.seq || '-'} model=${payload?.model || '-'}`)
}

function normalizeWorkspace(value: WorkspaceMm | string | undefined) {
  if (!value)
    return null
  if (typeof value === 'string') {
    try {
      return normalizeWorkspace(JSON.parse(value))
    }
    catch {
      return null
    }
  }
  return {
    x: Number(value.x || 0),
    y: Number(value.y || 0),
    z: Number(value.z || 0),
  }
}

function applySelfCheck(event: any, payload: SelfCheckPayload) {
  latestDiagnosticStatus.value = String(payload?.status || 'pending')
  latestDiagnosticSummary.value = formatSelfCheckSummary(payload)
  latestDiagnosticAt.value = event?.ts ? new Date(event.ts).toLocaleString() : new Date().toLocaleString()
  prependSelfCheckHistory({
    id: Date.now(),
    deviceId: deviceId.value,
    checkId: payload?.check_id || 'startup',
    scope: payload?.scope || 'startup',
    status: latestDiagnosticStatus.value,
    summary: latestDiagnosticSummary.value,
    checksJson: payload?.checks ? JSON.stringify(payload.checks) : undefined,
    reportedAt: new Date().toISOString(),
  })
  appendLog(`self_check seq=${event?.seq || '-'} status=${latestDiagnosticStatus.value}`)
}

async function loadSelfCheckHistory() {
  try {
    selfCheckHistory.value = await v2ListSelfCheckHistory(deviceId.value)
    const latest = selfCheckHistory.value[0]
    if (latest) {
      latestDiagnosticStatus.value = latest.status || 'pending'
      latestDiagnosticSummary.value = latest.summary || latest.checksJson || 'Self-check history loaded'
      latestDiagnosticAt.value = latest.reportedAt ? new Date(latest.reportedAt).toLocaleString() : ''
    }
  }
  catch {
    selfCheckHistory.value = []
  }
}

function prependSelfCheckHistory(item: V2SelfCheckHistoryResponse) {
  selfCheckHistory.value = [item, ...selfCheckHistory.value]
    .filter((value, index, list) => list.findIndex(other => other.id === value.id) === index)
    .slice(0, 5)
}

function formatSelfCheckSummary(payload: SelfCheckPayload | undefined) {
  const names = ['nvs', 'wifi', 'u1_uart', 'audio']
  const checks = normalizeSelfCheckItems(payload?.checks)
  const summary = names.map((name) => {
    const item = checks[name]
    return `${name}:${item?.status || 'missing'}`
  })
  return `${payload?.scope || 'startup'} ${summary.join(' ')}`
}

function normalizeSelfCheckItems(checks: SelfCheckPayload['checks']) {
  const result: Record<string, SelfCheckItem> = {}
  if (Array.isArray(checks)) {
    checks.forEach((item) => {
      if (item?.name)
        result[item.name] = item
    })
    return result
  }
  if (checks && typeof checks === 'object') {
    Object.entries(checks).forEach(([name, value]) => {
      result[name] = value && typeof value === 'object' ? { name, ...(value as SelfCheckItem) } : { name, status: String(value) }
    })
  }
  return result
}

function voiceprintConstraintForTask(task: V2PendingVoiceTaskResponse): VoiceprintConstraint | null {
  const constraints = parseJsonObject(task.constraintsJson)
  const voiceprint = constraints?.voiceprint
  return voiceprint && typeof voiceprint === 'object' && !Array.isArray(voiceprint)
    ? voiceprint as VoiceprintConstraint
    : null
}

function voiceprintApprovalLabel(task: V2PendingVoiceTaskResponse) {
  const voiceprint = voiceprintConstraintForTask(task)
  if (!voiceprint)
    return 'No voiceprint metadata'
  const reason = String(voiceprint.reason || 'unknown')
  const displayName = voiceprint.display_name || voiceprint.displayName || ''
  const memberType = voiceprint.member_type || voiceprint.memberType || ''
  if (voiceprintReenrollRequired(task))
    return `Voiceprint matched ${displayName || 'child'}, re-enroll required before future commands`
  if (voiceprint.matched)
    return `Voiceprint matched ${displayName || 'registered member'}${memberType ? ` (${memberType})` : ''}`
  if (reason === 'child_unknown_allowed')
    return 'Unknown child speaker allowed by policy; primary confirmation required'
  if (reason === 'unknown_allowed')
    return 'Unknown speaker allowed by policy; primary confirmation required'
  return `Voiceprint: ${reason}`
}

function voiceprintReenrollRequired(task: V2PendingVoiceTaskResponse) {
  const voiceprint = voiceprintConstraintForTask(task)
  return Boolean(voiceprint?.reenroll_hint || voiceprint?.reenrollHint || voiceprint?.reason === 'child_reenroll_required')
}

function voiceprintHasUnknownSpeaker(task: V2PendingVoiceTaskResponse) {
  const reason = voiceprintConstraintForTask(task)?.reason
  return reason === 'child_unknown_allowed' || reason === 'unknown_allowed'
}

function parseJsonObject(value: string | undefined) {
  if (!value)
    return null
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, any> : null
  }
  catch {
    return null
  }
}

async function handleHome() {
  homeLoading.value = true
  try { const r = await v2SubmitTask(deviceId.value, 'home'); showSubmitToast('归零已提交'); appendLog(`home: ${r.taskId}`) }
  catch (e: any) { message.alert(taskSubmitErrorMessage(e)) }
  finally { homeLoading.value = false }
}

async function handleWriteText() {
  const text = writeTextInput.value.trim()
  if (!text) {
    message.alert('请输入要书写的文字')
    return
  }

  writeTextLoading.value = true
  try {
    const r = await v2SubmitTask(deviceId.value, 'write_text', { text, font_id: defaultWriteTextFontId })
    latestPhase.value = r.status
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
    showSubmitToast('写字已提交')
    appendLog(`write_text: ${r.taskId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    writeTextLoading.value = false
  }
}

async function submitDrawGenerated(params: Record<string, unknown>, logLabel: string) {
  drawGeneratedLoading.value = true
  try {
    const r = await v2SubmitTask(deviceId.value, 'draw_generated', params)
    latestPhase.value = r.status
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
    showSubmitToast('画图已提交')
    appendLog(`${logLabel}: ${r.taskId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    drawGeneratedLoading.value = false
  }
}

async function handleDrawPrompt() {
  const prompt = drawPromptInput.value.trim()
  if (!prompt) {
    message.alert('请输入想画的图案')
    return
  }
  await submitDrawGenerated({ prompt }, 'draw_generated')
}

async function handleDrawStarter(starterId: string) {
  await submitDrawGenerated({ starter_id: starterId, use_starter_asset: true }, `draw_starter ${starterId}`)
}

async function loadPendingVoiceTasks() {
  if (!deviceId.value)
    return
  voiceApprovalLoading.value = true
  try {
    pendingVoiceTasks.value = await v2ListPendingVoiceTasks(deviceId.value)
    updateM6PendingTabBarBadge('voiceApproval', pendingVoiceTasks.value.length)
    appendLog(`pending voice approvals: ${pendingVoiceTasks.value.length}`)
  }
  catch (e: any) {
    pendingVoiceTasks.value = []
    updateM6PendingTabBarBadge('voiceApproval', 0)
    appendLog(`pending voice approvals error: ${String(e?.message || e).slice(0, 80)}`)
  }
  finally {
    voiceApprovalLoading.value = false
  }
}

async function handleApproveVoiceTask(taskId: string) {
  voiceApprovalLoading.value = true
  try {
    const r = await v2ApproveVoiceTask(taskId, 'approved from mobile primary UI')
    showSubmitToast('Voice task approved')
    appendLog(`voice approve: ${r.taskId} ${r.status}`)
    await loadPendingVoiceTasks()
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    voiceApprovalLoading.value = false
  }
}

async function handleRejectVoiceTask(taskId: string) {
  voiceApprovalLoading.value = true
  try {
    const r = await v2RejectVoiceTask(taskId, 'rejected from mobile primary UI')
    showSubmitToast('Voice task rejected')
    appendLog(`voice reject: ${r.taskId} ${r.status}`)
    await loadPendingVoiceTasks()
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    voiceApprovalLoading.value = false
  }
}

async function handleRefreshInfo() {
  infoLoading.value = true
  clearInfoLoadingTimer()
  try {
    const r = await v2SubmitTask(deviceId.value, 'get_device_info')
    latestPhase.value = r.status
    latestDeviceInfoTaskId.value = r.taskId
    showSubmitToast('设备信息查询已提交')
    appendLog(`get_device_info: ${r.taskId}`)
    infoLoadingTimer = setTimeout(() => { infoLoading.value = false }, 12000)
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
    latestDiagnosticSummary.value = 'Manual run_path diagnostic submitted'
    showSubmitToast('健康检查已提交')
    appendLog(`health_check run_path: ${r.taskId}`)
  }
  catch (e: any) {
    healthCheckLoading.value = false
    message.alert(taskSubmitErrorMessage(e))
  }
}

async function updatePaperSlotState(paperSlotState: 'empty' | 'loaded' | 'unknown') {
  suppliesLoading.value = true
  try {
    deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId.value, { paperSlotState })
    showSubmitToast(paperSlotState === 'loaded' ? '纸张已标记为放好' : paperSlotState === 'empty' ? '已标记没纸' : '纸张状态已设为未知')
    appendLog(`supplies paper_slot_state=${paperSlotState}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    suppliesLoading.value = false
  }
}

async function markNewPenInstalled() {
  suppliesLoading.value = true
  try {
    deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId.value, {
      penInstalledAt: new Date().toISOString(),
      penInkPercentEst: 100,
      resetPenMileage: true,
    })
    showSubmitToast('已记录新笔')
    appendLog('supplies pen_installed_at=now pen_ink_percent_est=100')
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    suppliesLoading.value = false
  }
}

async function handleRequestTransfer() {
  const targetUnionid = transferTargetUnionid.value.trim()
  if (!targetUnionid) {
    message.alert('请输入目标 unionid')
    return
  }
  transferLoading.value = true
  try {
    deviceTransfer.value = await v2RequestDeviceTransfer(deviceId.value, { targetUnionid })
    transferAcceptId.value = String(deviceTransfer.value.transferId)
    showSubmitToast('转赠请求已创建')
    appendLog(`transfer pending id=${deviceTransfer.value.transferId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    transferLoading.value = false
  }
}

async function handleCancelTransfer() {
  const transferId = currentTransferId()
  if (!transferId) {
    message.alert('请输入 transferId')
    return
  }
  transferLoading.value = true
  try {
    deviceTransfer.value = await v2CancelDeviceTransfer(transferId)
    showSubmitToast('转赠已取消')
    appendLog(`transfer cancelled id=${transferId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    transferLoading.value = false
  }
}

async function handleAcceptTransfer() {
  const transferId = currentTransferId()
  if (!transferId) {
    message.alert('请输入 transferId')
    return
  }
  transferLoading.value = true
  try {
    deviceTransfer.value = await v2AcceptDeviceTransfer(transferId)
    showSubmitToast('转赠已接受')
    appendLog(`transfer accepted id=${transferId}`)
  }
  catch (e: any) {
    message.alert(taskSubmitErrorMessage(e))
  }
  finally {
    transferLoading.value = false
  }
}

function currentTransferId() {
  const raw = transferAcceptId.value.trim() || String(deviceTransfer.value?.transferId || '')
  const value = Number(raw)
  return Number.isFinite(value) && value > 0 ? value : null
}

function taskSubmitErrorMessage(error: any) {
  const text = String(error?.message || error || '')
  if (text.includes('E_RUNTIME_STALE'))
    return runtimeStatusRefreshMessage
  if (text.includes('E_CONTENT_BLOCKED'))
    return contentBlockedMessage
  if (text.includes('E_INVALID_DRAWING'))
    return invalidDrawingMessage
  if (text.includes('E_NOT_ENTITLED'))
    return entitlementRequiredMessage
  if (text.includes('E_NO_PAPER'))
    return noPaperMessage
  return text || '提交失败'
}

function showSubmitToast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

function navigateBack() {
  uni.navigateBack()
}

function clearInfoLoadingTimer() {
  if (infoLoadingTimer) {
    clearTimeout(infoLoadingTimer)
    infoLoadingTimer = null
  }
}

const phaseColor = computed(() => {
  if (latestPhase.value === 'running' || latestPhase.value === 'accepted' || latestPhase.value === 'progress') return '#336cff'
  if (latestPhase.value === 'done') return '#07c160'
  if (latestPhase.value === 'failed') return '#ee0a24'
  return '#999'
})

const workspaceLabel = computed(() => {
  const workspace = normalizeWorkspace(deviceInfo.value?.workspaceMm as any)
  if (!workspace)
    return '工作空间 —'
  return `工作空间 X ${workspace.x} / Y ${workspace.y} / Z ${workspace.z} mm`
})

const paperSlotStateLabel = computed(() => {
  const state = deviceSupplies.value?.paperSlotState || 'unknown'
  if (state === 'loaded')
    return '纸张已放好'
  if (state === 'empty')
    return '没纸'
  return '未知'
})

const penStateLabel = computed(() => {
  if (!deviceSupplies.value?.penInstalledAt)
    return '未记录换笔'
  const percent = deviceSupplies.value.penInkPercentEst ?? 100
  return `墨水估算 ${percent}%`
})

const transferStateLabel = computed(() => {
  if (!deviceTransfer.value)
    return '无待处理转赠'
  return `#${deviceTransfer.value.transferId} ${deviceTransfer.value.status}`
})
</script>

<template>
  <wd-config-provider theme-color="#336cff" />
  <wd-navbar :title="t('v2.deviceDetail.title')" left-arrow fixed placeholder safe-area-inset-top @click-left="navigateBack" />

  <!-- device info -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="deviceInfo?.model || deviceId" :label="`HW ${deviceInfo?.hwRev||'—'} | FW ${deviceInfo?.fwRev||'—'}`">
      <template #value>
        <wd-tag :type="wsConnected ? 'success' : 'default'" size="mini">
          {{ wsConnected ? t('v2.deviceDetail.connected') : t('v2.deviceDetail.disconnected') }}
        </wd-tag>
      </template>
    </wd-cell>
    <wd-cell title="设备 ID" :value="deviceId" />
    <wd-cell title="工作空间" :label="workspaceLabel">
      <template v-if="latestDeviceInfoTaskId" #value>
        <wd-text :text="latestDeviceInfoTaskId.slice(0, 8)" size="24rpx" color="#666" />
      </template>
    </wd-cell>
  </wd-cell-group>

  <!-- supplies -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell title="耗材状态" :label="penStateLabel">
      <template #value>
        <wd-tag :type="deviceSupplies?.paperSlotState === 'loaded' ? 'success' : deviceSupplies?.paperSlotState === 'empty' ? 'danger' : 'default'" size="mini">
          {{ paperSlotStateLabel }}
        </wd-tag>
      </template>
    </wd-cell>
    <view class="mx-[30rpx] mb-[24rpx] flex flex-wrap gap-[12rpx]">
      <wd-button type="success" round size="small" :loading="suppliesLoading" @click="updatePaperSlotState('loaded')">
        纸张已放好
      </wd-button>
      <wd-button type="warning" round size="small" :disabled="suppliesLoading" @click="updatePaperSlotState('empty')">
        没纸了
      </wd-button>
      <wd-button type="info" round size="small" :disabled="suppliesLoading" @click="markNewPenInstalled">
        我换了新笔
      </wd-button>
    </view>
  </wd-cell-group>

  <!-- device transfer -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell title="设备转赠" :label="transferStateLabel" />
    <view class="mx-[30rpx] mb-[24rpx]">
      <wd-input
        v-model="transferTargetUnionid"
        clearable
        :maxlength="80"
        placeholder="目标 unionid"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
      />
      <wd-input
        v-model="transferAcceptId"
        clearable
        type="number"
        placeholder="transferId"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
      />
      <view class="flex flex-wrap gap-[12rpx]">
        <wd-button type="primary" round size="small" :loading="transferLoading" @click="handleRequestTransfer">
          发起转赠
        </wd-button>
        <wd-button type="warning" round size="small" :disabled="transferLoading" @click="handleCancelTransfer">
          取消转赠
        </wd-button>
        <wd-button type="success" round size="small" :disabled="transferLoading" @click="handleAcceptTransfer">
          接受转赠
        </wd-button>
      </view>
    </view>
  </wd-cell-group>

  <!-- Pending voice approvals -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="t('v2.deviceDetail.pendingVoiceApprovals')" :label="`${pendingVoiceApprovalCount} 条语音任务等待主控审批`">
      <template #value>
        <view class="flex items-center gap-[12rpx]">
          <wd-tag v-if="pendingVoiceApprovalCount" type="warning" size="mini">
            {{ pendingVoiceApprovalBadgeText }}
          </wd-tag>
          <wd-button type="text" size="small" :loading="voiceApprovalLoading" @click="loadPendingVoiceTasks">
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
          <wd-tag type="warning" size="mini">
            {{ task.status }}
          </wd-tag>
        </view>
        <wd-text :text="task.requestId || task.taskId" size="22rpx" color="#666" custom-class="!mt-[8rpx]" />
        <wd-text v-if="task.paramsJson" :text="task.paramsJson.slice(0, 120)" size="22rpx" color="#666" custom-class="!mt-[8rpx]" />
        <view v-if="task.constraintsJson" class="mt-[12rpx] flex flex-col gap-[8rpx]">
          <wd-text :text="voiceprintApprovalLabel(task)" size="22rpx" color="#4b5563" />
          <wd-tag v-if="voiceprintReenrollRequired(task)" type="warning" size="mini">
            Child voiceprint re-enroll needed
          </wd-tag>
          <wd-tag v-if="voiceprintHasUnknownSpeaker(task)" type="danger" size="mini">
            Unknown speaker requires primary review
          </wd-tag>
        </view>
        <view class="mt-[16rpx] flex flex-wrap gap-[12rpx]">
          <wd-button type="success" round size="small" :loading="voiceApprovalLoading" @click="handleApproveVoiceTask(task.taskId)">
            {{ t('v2.deviceDetail.approve') }}
          </wd-button>
          <wd-button type="danger" round size="small" :disabled="voiceApprovalLoading" @click="handleRejectVoiceTask(task.taskId)">
            {{ t('v2.deviceDetail.reject') }}
          </wd-button>
        </view>
      </view>
    </template>
    <view v-else class="mx-[30rpx] mb-[24rpx] rounded-[8rpx] bg-[#f5f7fb] p-[20rpx]">
      <wd-text :text="t('v2.deviceDetail.noPendingVoice')" size="24rpx" color="#666" />
    </view>
  </wd-cell-group>

  <!-- task status -->
  <wd-cell-group border>
    <wd-cell title="最近任务">
      <template #value>
        <wd-tag :type="latestPhase === 'done' ? 'success' : latestPhase === 'failed' ? 'danger' : latestPhase === 'running' ? 'primary' : 'default'" size="mini" :custom-style="`border-color:${phaseColor};color:${phaseColor}`">
          {{ latestPhase }}
        </wd-tag>
      </template>
    </wd-cell>
    <view v-if="latestProgressPercent !== null" class="mx-[30rpx] mb-[24rpx]">
      <view class="h-[12rpx] bg-[#edf1f7] rounded-[6rpx] overflow-hidden">
        <view class="h-full bg-[#336cff] rounded-[6rpx]" :style="progressBarStyle" />
      </view>
      <wd-text :text="latestProgressLabel" size="22rpx" color="#666" custom-class="!mt-[8rpx]" />
    </view>
  </wd-cell-group>

  <!-- health check -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell title="健康检查" :label="latestDiagnosticSummary">
      <template #value>
        <wd-tag :type="latestDiagnosticStatus === 'pass' ? 'success' : latestDiagnosticStatus === 'fail' ? 'danger' : 'default'" size="mini">
          {{ latestDiagnosticStatus }}
        </wd-tag>
      </template>
    </wd-cell>
    <wd-cell title="最近诊断" :value="latestDiagnosticAt || '等待结果'" />
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
          <wd-tag :type="item.status === 'pass' ? 'success' : item.status === 'fail' ? 'danger' : 'default'" size="mini">
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
      <wd-button type="primary" block round size="large" :loading="healthCheckLoading" custom-class="!h-[88rpx] !text-[30rpx]" @click="handleHealthCheck">
        {{ healthCheckLoading ? '检查中...' : '开始健康检查' }}
      </wd-button>
    </view>
  </wd-cell-group>

  <!-- home button -->
  <view class="mx-[20rpx] mt-[20rpx] flex gap-[20rpx]">
    <wd-button type="primary" block round size="large" :loading="homeLoading" custom-class="!h-[96rpx] !text-[32rpx]" @click="handleHome">
      {{ homeLoading ? t('v2.deviceDetail.homing') : t('v2.deviceDetail.homeButton') }}
    </wd-button>
    <wd-button type="info" block round size="large" :loading="infoLoading" custom-class="!h-[96rpx] !text-[32rpx]" @click="handleRefreshInfo">
      {{ infoLoading ? '查询中...' : '刷新信息' }}
    </wd-button>
  </view>

  <!-- write_text demo -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell title="写字 Demo" :label="`默认字体 ${defaultWriteTextFontId}`" />
    <view class="mx-[30rpx] mb-[24rpx]">
      <wd-input
        v-model="writeTextInput"
        clearable
        :maxlength="40"
        placeholder="输入要写的文字"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
      />
      <wd-button type="primary" block round size="large" :loading="writeTextLoading" custom-class="!h-[88rpx] !text-[30rpx]" @click="handleWriteText">
        {{ writeTextLoading ? '提交中...' : '开始写字' }}
      </wd-button>
    </view>
  </wd-cell-group>

  <!-- draw_generated demo -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell title="画图 Demo" label="文字描述或预置图案" />
    <view class="mx-[30rpx] mb-[24rpx]">
      <wd-input
        v-model="drawPromptInput"
        clearable
        :maxlength="60"
        placeholder="输入想画的图案"
        custom-class="!bg-[#f5f7fb] !rounded-[8rpx] !px-[20rpx] !mb-[16rpx]"
      />
      <wd-button type="primary" block round size="large" :loading="drawGeneratedLoading" custom-class="!h-[88rpx] !text-[30rpx]" @click="handleDrawPrompt">
        {{ drawGeneratedLoading ? '提交中...' : '生成绘制' }}
      </wd-button>
      <view class="mt-[16rpx] flex flex-wrap gap-[12rpx]">
        <wd-button
          v-for="asset in starterAssets"
          :key="asset.id"
          type="info"
          plain
          round
          size="small"
          :disabled="drawGeneratedLoading"
          @click="handleDrawStarter(asset.id)"
        >
          {{ asset.label }}
        </wd-button>
      </view>
    </view>
  </wd-cell-group>

  <!-- Edge-A WSS log (following existing device/index.vue debug pattern) -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="wsConnected ? 'Edge-A WSS 已订阅' : 'Edge-A WSS 未连接'" center>
      <template v-if="!wsConnected" #value>
        <wd-button type="text" size="small" @click="connectWs">{{ t('v2.deviceDetail.connectAndSubscribe') }}</wd-button>
      </template>
    </wd-cell>
  </wd-cell-group>
  <scroll-view scroll-y class="bg-[#f5f5f5] rounded-[8rpx] mx-[20rpx] p-[16rpx]" style="max-height:300rpx">
    <wd-text v-for="(l,i) in logLines" :key="i" :text="l" size="20rpx" color="#666" custom-class="!leading-[36rpx]" />
    <wd-text v-if="!logLines.length" text="等待事件..." size="24rpx" color="#999" />
  </scroll-view>
</template>
