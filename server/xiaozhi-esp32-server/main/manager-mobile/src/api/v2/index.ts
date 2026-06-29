import type { V2BindResponse, V2DeletionResponse, V2DeviceInfo, V2DeviceSupplyResponse, V2DeviceSupplyUpdateRequest, V2DeviceTransferRequest, V2DeviceTransferResponse, V2LoginResponse, V2MeResponse, V2PendingVoiceTaskResponse, V2SelfCheckHistoryResponse, V2SubmitTaskResponse, V2TaskInfo, V2TaskListResponse } from './types'
import { http } from '@/http/request/alova'

const appPrefix = '/device/v1/app'

export function v2Login(code: string) {
  // meta.authRole:'login' 标记此请求为登录请求，alova onAuthRequired 会跳过鉴权与刷新等待，
  // 避免在 refreshTokenOnSuccess.handler 内调用本函数时陷入 tokenRefreshing 死锁。
  return http.Post<V2LoginResponse>(`${appPrefix}/auth/login`, { code }, { meta: { ignoreAuth: true, authRole: 'login', toast: true, isExposeError: true } })
}

/**
 * 静默刷新 token：微信 code → 换新 token → 更新本地存储
 *
 * 调用时机：alova refreshTokenOnSuccess.handler 在 token 临近过期/已过期时调用。
 * 关键点：
 * 1. 此函数内部发起的 v2Login 请求 meta 带 authRole:'login'，
 *    alova onAuthRequired 会跳过对其的鉴权检查，避免递归刷新（见 clienthook isLoginRole 分支）。
 * 2. 微信 code 一次性使用，过期后无法复用，故每次刷新都重新 uni.login 拿新 code。
 * 3. 抛错时由调用方（alova handler）决定是否回退到登录页；此处只负责刷新失败抛出。
 */
export async function v2RefreshToken(): Promise<{ token: string, expireAt: number }> {
  const res = await uni.login({ provider: 'weixin' })
  if (!res.code)
    throw new Error('wechat code unavailable')
  const data = await v2Login(res.code)
  if (!data || !data.token) {
    throw new Error('WeChat login returned invalid response')
  }
  const expireAt = Math.floor(Date.now() / 1000) + (data.expiresIn || 86400)
  uni.setStorageSync('token', JSON.stringify({ token: data.token, expireAt }))
  return { token: data.token, expireAt }
}

export async function v2GetMe() {
  const res = await http.Get<V2MeResponse>(`${appPrefix}/auth/me`, { meta: { ignoreAuth: false, toast: false } })
  return {
    accountId: res.accountId || '',
    phone: res.phone || '',
    nickname: res.nickname || '',
    avatarUrl: res.avatarUrl || '',
    role: res.role || 'user',
    createdAt: res.createdAt || '',
  }
}

export function v2BindDevice(deviceSn: string, activationCode: string) {
  return http.Post<V2BindResponse>(
    `${appPrefix}/devices/bind`,
    { deviceSn, activationCode },
    { meta: { ignoreAuth: false, toast: true } },
  )
}

export async function v2GetDevices() {
  const res = await http.Get<{ devices: unknown[], count: number }>(`${appPrefix}/devices`, {
    meta: { ignoreAuth: false, toast: false },
    cacheFor: { expire: 0 },
  })
  return { total: res.count, rows: res.devices.map(toDeviceInfo) }
}

export async function v2GetDeviceInfo(deviceId: string) {
  const res = await http.Get<unknown>(`${appPrefix}/devices/${deviceId}`, { meta: { ignoreAuth: false, toast: false } })
  return toDeviceInfo(res)
}

export function v2SubmitTask(deviceId: string, capability: string, params?: Record<string, unknown>, requestId = createTaskRequestId(capability)) {
  return http.Post<V2SubmitTaskResponse>(
    `${appPrefix}/devices/${deviceId}/tasks`,
    { capability, requestId, params, source: 'client' },
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export async function v2GetTask(taskId: string) {
  const res = await http.Get<Record<string, any>>(
    `${appPrefix}/tasks/${taskId}`,
    { meta: { ignoreAuth: false, toast: false } },
  )
  return toTaskInfo(res)
}

export async function v2ListTasks(deviceId: string, status = '', limit = 20) {
  const res = await http.Get<V2TaskListResponse>(
    `${appPrefix}/tasks`,
    { params: { deviceId, status, limit }, meta: { ignoreAuth: false, toast: false } },
  )
  return { tasks: (res.tasks || []).map(toTaskInfo), count: res.count }
}

export async function v2ListPendingVoiceTasks(deviceId: string) {
  const res = await http.Post<{ tasks: V2PendingVoiceTaskResponse[], count: number }>(
    `${appPrefix}/devices/${deviceId}/voice-tasks/pending`,
    {},
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
  return res.tasks || []
}

export async function v2ListSelfCheckHistory(deviceId: string) {
  const res = await http.Get<{ selfChecks: unknown[], count: number }>(
    `${appPrefix}/devices/${deviceId}/self-checks`,
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
  return (res.selfChecks || []).map(toSelfCheckHistory)
}

export function v2ApproveVoiceTask(taskId: string, reason?: string) {
  return http.Post<V2SubmitTaskResponse>(
    `${appPrefix}/tasks/${taskId}/approve`,
    reason ? { reason } : {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export function v2RejectVoiceTask(taskId: string, reason?: string) {
  return http.Post<V2SubmitTaskResponse>(
    `${appPrefix}/tasks/${taskId}/reject`,
    reason ? { reason } : {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export async function v2UpdateDeviceSupplies(deviceId: string, request: V2DeviceSupplyUpdateRequest) {
  const supplies = await http.Put<unknown[]>(
    `${appPrefix}/devices/${deviceId}/supplies`,
    toSupplyUpdateRequest(request),
    { meta: { ignoreAuth: false, toast: false } },
  )
  return toSupplyState(deviceId, supplies || [], request)
}

export function v2RequestDeviceTransfer(deviceId: string, request: V2DeviceTransferRequest) {
  return http.Post<V2DeviceTransferResponse>(
    `${appPrefix}/devices/${deviceId}/transfer`,
    { toPhone: request.targetUnionid, reason: 'manager-mobile transfer request' },
    { meta: { ignoreAuth: false, toast: false } },
  ).then(toTransferResponse)
}

export function v2AcceptDeviceTransfer(transferId: number | string) {
  return http.Post<V2DeviceTransferResponse>(
    `${appPrefix}/transfers/${transferId}/accept`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  ).then(toTransferResponse)
}

export function v2CancelDeviceTransfer(transferId: number | string) {
  return http.Post<V2DeviceTransferResponse>(
    `${appPrefix}/transfers/${transferId}/cancel`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  ).then(toTransferResponse)
}

export async function v2ListPendingIncomingDeviceTransfers() {
  const res = await http.Get<{ transfers: unknown[], count: number }>(
    `${appPrefix}/transfers/pending`,
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
  return (res.transfers || []).map(toTransferResponse)
}

export async function v2DeleteAccount() {
  const res = await http.Post<{ accountId: string, deletedAt: string }>(
    `${appPrefix}/auth/account/delete`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  )
  return { status: 'deleted', affectedRows: res.accountId ? 1 : 0, auditRetentionDays: 0 } as V2DeletionResponse
}

function toDeviceInfo(raw: unknown): V2DeviceInfo {
  const row = raw as Record<string, any>
  return {
    deviceId: String(row.deviceId || row.id || ''),
    model: String(row.model || ''),
    hwRev: String(row.hwRev || row.hardwareVer || row.hardware_ver || ''),
    fwRev: String(row.fwRev || row.firmwareVer || row.firmware_ver || ''),
    workspaceMm: parseWorkspace(row.workspaceMm || row.workspace_mm || row.metadata),
    status: String(row.status || 'offline'),
    lastSeenAt: String(row.lastSeenAt || row.lastHeartbeat || ''),
  }
}

function toSelfCheckHistory(raw: unknown): V2SelfCheckHistoryResponse {
  const row = raw as Record<string, any>
  const details = row.details || row.checksJson || ''
  return {
    id: Number(row.id) || Date.now(),
    deviceId: String(row.deviceId || ''),
    checkId: String(row.checkId || row.id || ''),
    scope: String(row.scope || row.checkType || 'self_check'),
    status: String(row.status || row.result || 'pending'),
    summary: typeof details === 'string' ? details : JSON.stringify(details),
    checksJson: typeof details === 'string' ? details : JSON.stringify(details),
    reportedAt: String(row.reportedAt || row.createdAt || ''),
  }
}

function toSupplyUpdateRequest(request: V2DeviceSupplyUpdateRequest) {
  const supplies = []
  if (request.paperSlotState) {
    supplies.push({
      supplyType: 'paper',
      level: request.paperSlotState === 'loaded' ? 1 : request.paperSlotState === 'empty' ? 0 : 0.5,
      status: request.paperSlotState === 'loaded' ? 'normal' : request.paperSlotState,
    })
  }
  if (request.penInstalledAt || request.penInkPercentEst !== undefined || request.resetPenMileage) {
    supplies.push({
      supplyType: 'pen',
      level: Math.max(0, Math.min(1, (request.penInkPercentEst ?? 100) / 100)),
      status: (request.penInkPercentEst ?? 100) <= 10 ? 'low' : 'normal',
    })
  }
  return { supplies }
}

function toSupplyState(deviceId: string, supplies: unknown[], request: V2DeviceSupplyUpdateRequest): V2DeviceSupplyResponse {
  const paper = supplies.map(row => row as Record<string, any>).find(row => row.supplyType === 'paper')
  const pen = supplies.map(row => row as Record<string, any>).find(row => row.supplyType === 'pen')
  const paperSlotState = request.paperSlotState || (paper?.status === 'empty' ? 'empty' : paper ? 'loaded' : 'unknown')
  return {
    deviceId,
    paperSlotState,
    penInstalledAt: request.penInstalledAt,
    penInkPercentEst: request.penInkPercentEst ?? (pen ? Math.round(Number(pen.level || 0) * 100) : undefined),
    penMileageMm: 0,
  }
}

function toTransferResponse(raw: unknown): V2DeviceTransferResponse {
  const row = raw as Record<string, any>
  return {
    transferId: row.transferId,
    deviceId: String(row.deviceId || ''),
    sourceAccountId: row.sourceAccountId || row.fromAccountId,
    targetAccountId: row.targetAccountId || row.toAccountId,
    status: row.status,
  }
}

function parseWorkspace(value: unknown) {
  if (!value)
    return { x: 0, y: 0, z: 0 }
  if (typeof value === 'string') {
    try {
      return parseWorkspace(JSON.parse(value))
    }
    catch {
      return { x: 0, y: 0, z: 0 }
    }
  }
  const row = value as Record<string, any>
  const workspace = row.workspaceMm || row.workspace_mm || row.workspace || row
  return {
    x: Number(workspace.x || 0),
    y: Number(workspace.y || 0),
    z: Number(workspace.z || 0),
  }
}

function createTaskRequestId(capability: string) {
  const safeCapability = capability.replace(/[^\w-]/g, '_') || 'task'
  const randomPart = Math.random().toString(36).slice(2, 10)
  return `client-${safeCapability}-${Date.now().toString(36)}-${randomPart}`
}

function toTaskInfo(raw: Record<string, any>): V2TaskInfo {
  const params = raw.params || raw.task?.params || {}
  const result = raw.result || raw.task?.result || {}
  return {
    taskId: String(raw.taskId || raw.id || raw.task_id || ''),
    status: String(raw.status || raw.taskState || 'unknown'),
    deviceId: String(raw.deviceId || raw.device_id || ''),
    capability: String(raw.capability || raw.app_capability || params.source_capability || ''),
    params,
    sent: Boolean(raw.sent),
    queueDepth: Number(raw.queueDepth || raw.queue_depth || 0),
    createdAt: String(raw.createdAt || raw.created_at || ''),
    updatedAt: String(raw.updatedAt || raw.updated_at || ''),
    result,
    imageUrl: String(result.imageUrl || result.image_url || params.imageUrl || ''),
    error: String(raw.error || result.error || ''),
  }
}
