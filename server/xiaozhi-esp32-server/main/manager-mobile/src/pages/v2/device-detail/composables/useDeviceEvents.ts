import type { V2DeviceInfo, V2SelfCheckHistoryResponse } from '@/api/v2/types'
import { computed, ref } from 'vue'
import { v2ListSelfCheckHistory, v2GetDeviceRuntimeStatus } from '@/api/v2'
import { t } from '@/i18n'

// --- WS 事件 payload 类型 ---
interface WorkspaceMm { x?: number | string, y?: number | string, z?: number | string }
interface MotionProgress { done_segments?: number | string, total_segments?: number | string, percent?: number | string }
interface DeviceInfoReplyPayload { model?: string, hw_rev?: string, fw_rev?: string, workspace_mm?: WorkspaceMm | string }
interface JobStatusPayload { phase?: string, capability?: string, progress?: MotionProgress }
type SelfCheckStatus = 'pass' | 'warn' | 'fail' | 'pending' | string
interface SelfCheckItem { name?: string, status?: SelfCheckStatus, detail?: string }
interface SelfCheckPayload { check_id?: string, scope?: string, status?: SelfCheckStatus, checks?: SelfCheckItem[] | Record<string, SelfCheckItem | SelfCheckStatus | string> }

function clampPercent(v: number) {
  return Number.isFinite(v) ? Math.max(0, Math.min(100, Math.round(v))) : 0
}

function normalizeWorkspace(value: WorkspaceMm | string | undefined) {
  if (!value)
    return null
  if (typeof value === 'string') {
    try {
      return normalizeWorkspace(JSON.parse(value))
    }
    catch { console.warn('normalizeWorkspace JSON parse failed'); return null }
  }
  return { x: Number(value.x || 0), y: Number(value.y || 0), z: Number(value.z || 0) }
}

/**
 * 设备详情页的 WS 事件 + 进度 + 自检状态（P3.1 从 index.vue 提取）。
 *
 * 拥有 deviceInfo / infoLoading / healthCheckLoading 等与事件强相关的状态，
 * 并向 useDeviceActions 暴露 setter，避免同一份状态在两个 composable 里各存一份。
 */
export function useDeviceEvents(deviceId: () => string, appendLog: (msg: string) => void) {
  const deviceInfo = ref<V2DeviceInfo | null>(null)
  const infoLoading = ref(false)
  const healthCheckLoading = ref(false)
  const latestPhase = ref('—')
  const latestProgressPercent = ref<number | null>(null)
  const latestProgressLabel = ref('')
  const latestDiagnosticStatus = ref('pending')
  const latestDiagnosticSummary = ref('No self-check result yet')
  const latestDiagnosticAt = ref('')
  const selfCheckHistory = ref<V2SelfCheckHistoryResponse[]>([])
  let infoLoadingTimer: ReturnType<typeof setTimeout> | null = null

  function clearInfoLoadingTimer() {
    if (infoLoadingTimer) {
      clearTimeout(infoLoadingTimer)
      infoLoadingTimer = null
    }
  }
  function startInfoLoadingTimer() {
    clearInfoLoadingTimer()
    infoLoadingTimer = setTimeout(() => {
      infoLoading.value = false
    }, 12000)
  }
  function setPhase(phase: string) {
    latestPhase.value = phase
  }
  function resetProgress() {
    latestProgressPercent.value = null
    latestProgressLabel.value = ''
  }

  function handleEdgeAEvent(event: any) {
    const payload = event?.payload as JobStatusPayload | DeviceInfoReplyPayload | SelfCheckPayload | undefined
    if (event?.event_type === 'status_snapshot') {
      const snap = payload as Record<string, unknown> | undefined
      if (deviceInfo.value) {
        deviceInfo.value = {
          ...deviceInfo.value,
          status: snap?.online ? 'online' : 'offline',
          fwRev: String(snap?.firmwareVersion || deviceInfo.value.fwRev || ''),
          lastSeenAt: String(snap?.lastSeenAt || deviceInfo.value.lastSeenAt || ''),
        }
      }
      infoLoading.value = false
      clearInfoLoadingTimer()
      // MP-3:直接采用 mapServerEvent 已算好的 phase(working ? 'running' : 'idle')。
      // 此前只在 working 时置 running、从不回置 idle,错过 completed/failed 事件后永久假忙。
      if (typeof snap?.phase === 'string')
        latestPhase.value = snap.phase
      else if (snap?.working !== undefined)
        latestPhase.value = snap.working ? 'running' : 'idle'
      return
    }
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
      resetProgress()
      return
    }
    if (['failed', 'cancelled', 'rejected'].includes(payload.phase))
      resetProgress()
  }

  function applyDeviceInfoReply(event: any, payload: DeviceInfoReplyPayload) {
    const ws = normalizeWorkspace(payload?.workspace_mm)
    deviceInfo.value = {
      deviceId: event?.device_id || deviceId(),
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
    selfCheckHistory.value = [{ id: Date.now(), deviceId: deviceId(), checkId: payload?.check_id || 'startup', scope: payload?.scope || 'startup', status: latestDiagnosticStatus.value, summary: latestDiagnosticSummary.value, checksJson: payload?.checks ? JSON.stringify(payload.checks) : undefined, reportedAt: new Date().toISOString() }, ...selfCheckHistory.value].filter((v, i, l) => l.findIndex(o => o.id === v.id) === i).slice(0, 5)
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
      selfCheckHistory.value = await v2ListSelfCheckHistory(deviceId())
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

  const phaseColor = computed(() => {
    if (['running', 'accepted', 'progress'].includes(latestPhase.value))
      return 'var(--accent)'
    if (latestPhase.value === 'done')
      return 'var(--green)'
    if (latestPhase.value === 'failed')
      return 'var(--danger)'
    return 'var(--muted)'
  })
  const progressBarStyle = computed(() => `width:${latestProgressPercent.value ?? 0}%;`)
  const isDeviceBusy = computed(() => ['running', 'accepted', 'progress'].includes(latestPhase.value))
  const workspaceLabel = computed(() => {
    const ws = normalizeWorkspace(deviceInfo.value?.workspaceMm as any)
    return ws ? `${t('v2.detail.workspace')} X ${ws.x} / Y ${ws.y} / Z ${ws.z} mm` : `${t('v2.detail.workspace')} —`
  })

  async function applyRuntimeStatus(deviceIdValue: string) {
    const status = await v2GetDeviceRuntimeStatus(deviceIdValue)
    if (deviceInfo.value) {
      deviceInfo.value = {
        ...deviceInfo.value,
        status: status.online ? 'online' : 'offline',
        fwRev: status.firmwareVersion || deviceInfo.value.fwRev,
        lastSeenAt: status.lastSeenAt || deviceInfo.value.lastSeenAt,
      }
    }
    infoLoading.value = false
    clearInfoLoadingTimer()
    // MP-3:手动刷新同样要能把 working=false 回置 idle,否则任务失败后无法恢复
    latestPhase.value = status.working ? 'running' : 'idle'
  }

  return {
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
  }
}
