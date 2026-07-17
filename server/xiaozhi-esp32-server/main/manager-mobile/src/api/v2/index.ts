import type { V2BindResponse, V2DeletionResponse, V2DeviceInfo, V2DeviceSupplyResponse, V2DeviceSupplyUpdateRequest, V2DeviceTransferRequest, V2DeviceTransferResponse, V2LoginResponse, V2MeResponse, V2PendingVoiceTaskResponse, V2SelfCheckHistoryResponse, V2SubmitTaskResponse, V2TaskInfo, V2TaskListResponse } from './types'
import { LOGIN_TIMEOUT_MS } from '@/config/timeouts'
import { http } from '@/http/request/alova'
import { getEnvBaseUrl } from '@/utils'

const appPrefix = '/device/v1/app'

/**
 * å¾®ä¿¡å°ç¨åºä¸é®ç»å½ã?
 *
 * æ³¨æï¼æ­¤æ¥å£ bypass alovaï¼ç´æ¥ä½¿ç?uni.requestã?
 * åå ï¼alova å¨é¨åååºæ ¼å¼?ç½ç»ç¯å¢ä¸ä¼æç»å½æåååºè§£æä¸º undefinedï¼?
 * å¯¼è´ token æ æ³åå¥ãuni.request æ´å¯æ§ï¼ä¸ç»å½æ¥å£ä¸éè¦?alova çé´æ?å·æ°è½åã?
 *
 * è¶æ¶ä¸éè¯ï¼
 * - timeout è®¾ä¸º 30sï¼è¦çå¾®ä¿?jscode2session å¶åæ¢ååº?+ ç½ç»æå¨ã?
 * - å¯?timeout / network ç±»éè¯¯èªå¨éè¯?1 æ¬¡ï¼é¿ååæ¬¡ç½ç»æå¨å¯¼è´ç»å½å¤±è´¥ã?
 */
const LOGIN_MAX_RETRIES = 2

async function _doLoginRequest(code: string): Promise<UniApp.RequestSuccessCallbackResult> {
  const baseUrl = getEnvBaseUrl()
  return uni.request({
    url: `${baseUrl}${appPrefix}/auth/login`,
    method: 'POST',
    header: { 'Content-Type': 'application/json' },
    data: { code },
    timeout: LOGIN_TIMEOUT_MS,
  })
}

function _isRetryableError(res: UniApp.RequestSuccessCallbackResult): boolean {
  // uni.request æååè°é?statusCode ä¸?0 æ?errMsg å?fail/timeout æ¶è§ä¸ºç½ç»å±å¤±è´¥
  if (!res.statusCode || res.statusCode <= 0)
    return true
  const errMsg = (res.errMsg || '').toLowerCase()
  return errMsg.includes('fail') || errMsg.includes('timeout') || errMsg.includes('abort')
}

export async function v2Login(code: string): Promise<V2LoginResponse> {
  let lastError = ''
  for (let attempt = 1; attempt <= LOGIN_MAX_RETRIES; attempt++) {
    const res = await _doLoginRequest(code)
    if (_isRetryableError(res)) {
      lastError = `network/timeout (attempt ${attempt}, statusCode=${res.statusCode}, errMsg=${res.errMsg || 'none'})`
      if (attempt < LOGIN_MAX_RETRIES)
        continue
      throw new Error(`WeChat login failed: ${lastError}`)
    }
    const data = res.data as V2LoginResponse | undefined
    if (res.statusCode !== 200 || !data || !data.token) {
      throw new Error(`WeChat login failed (statusCode=${res.statusCode})`)
    }
    return data
  }
  throw new Error(`WeChat login failed: ${lastError}`)
}

/**
 * éé»å·æ° tokenï¼å¾®ä¿?code â?æ¢æ° token â?æ´æ°æ¬å°å­å¨
 *
 * è°ç¨æ¶æºï¼alova refreshTokenOnSuccess.handler å?token ä¸´è¿è¿æ/å·²è¿ææ¶è°ç¨ã?
 * å³é®ç¹ï¼
 * 1. å¾®ä¿¡ code ä¸æ¬¡æ§ä½¿ç¨ï¼è¿æåæ æ³å¤ç¨ï¼ææ¯æ¬¡å·æ°é½éæ° uni.login æ¿æ° codeã?
 * 2. æéæ¶ç±è°ç¨æ¹ï¼alova handlerï¼å³å®æ¯å¦åéå°ç»å½é¡µï¼æ­¤å¤åªè´è´£å·æ°å¤±è´¥æåºã?
 */
export async function v2RefreshToken(): Promise<{ token: string, expireAt: number }> {
  const res = await uni.login({ provider: 'weixin' })
  if (!res.code)
    throw new Error('wechat code unavailable')
  const data = await v2Login(res.code)
  await bootstrapSessionAfterLogin(data)
  const expireAt = Math.floor(Date.now() / 1000) + (data.expiresIn || 86400)
  return { token: data.token, expireAt }
}

export async function v2GetMe() {
  const res = await http.Get<V2MeResponse>(`${appPrefix}/auth/me`, { meta: { ignoreAuth: false, toast: false } })
  const profile = {
    accountId: res.accountId || '',
    openid: res.openid || '',
    phone: res.phone || '',
    nickname: res.nickname || '',
    avatarUrl: res.avatarUrl || '',
    role: res.role || 'user',
    createdAt: res.createdAt || '',
  }
  if (profile.openid)
    uni.setStorageSync('openid', profile.openid)
  return profile
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

export async function v2GetDeviceRuntimeStatus(deviceId: string) {
  return http.Get<{
    deviceId: string
    online: boolean
    working: boolean
    activeTaskId?: string | null
    firmwareVersion?: string | null
    protocolVersion?: string | null
    lastSeenAt?: string | null
  }>(`${appPrefix}/devices/${deviceId}/status`, { meta: { ignoreAuth: false, toast: false } })
}

export function v2RenderAsset(assetId: string, deviceId: string) {
  return http.Post<{ taskId: string, status: string }>(
    `${appPrefix}/assets/${assetId}/render`,
    { deviceId },
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export function v2GetHandwritingOptions() {
  return http.Get<{
    fonts: Record<string, string>
    papers: Record<string, string>
    defaults: Record<string, string>
    maxTextLength: number
  }>(`${appPrefix}/handwriting/options`, { meta: { ignoreAuth: false, toast: false } })
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
  const target = request.targetPhone.trim()
  const payload: Record<string, string> = { reason: 'manager-mobile transfer request' }
  if (/^1\d{10}$/.test(target))
    payload.toPhone = target
  else if (target.startsWith('o') || target.startsWith('wx:'))
    payload.toOpenid = target
  else
    payload.toPhone = target
  return http.Post<V2DeviceTransferResponse>(
    `${appPrefix}/devices/${deviceId}/transfer`,
    payload,
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

// ââ è®¾å¤åäº«ï¼AUDIT gap å®ç°ï¼ââ

export interface V2ShareResponse {
  shareId: string
  deviceId: string
  shareToken: string
  permission: string
  status: string
  expiresAt: string
}

export function v2CreateShare(deviceId: string, permission: string, expiresAt: string) {
  return http.Post<V2ShareResponse>(
    `${appPrefix}/devices/${deviceId}/share`,
    { permission, expiresAt },
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export function v2RevokeShare(deviceId: string, shareToken?: string, shareId?: string) {
  return http.Post<V2ShareResponse>(
    `${appPrefix}/devices/${deviceId}/share/revoke`,
    { shareToken: shareToken || '', shareId: shareId || '' },
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export async function v2ListShares(deviceId: string) {
  const res = await http.Get<{ shares: V2ShareResponse[], count: number }>(
    `${appPrefix}/devices/${deviceId}/shares`,
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
  return res.shares || []
}

// ââ è®¾å¤è§£ç» ââ

export function v2UnbindDevice(deviceId: string) {
  return http.Post<{ ok: boolean, message: string }>(
    `${appPrefix}/devices/${deviceId}/unbind`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}

// ââ éç¥è®¢é ââ

export interface V2NotificationSubscription {
  subscriptionId: string
  status: string
}

export function v2SubscribeNotifications(openid: string, templateIds: string[], deviceIds: string[]) {
  return http.Post<V2NotificationSubscription>(
    `${appPrefix}/notifications/subscribe`,
    { openid, templateIds, deviceIds },
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export async function v2ListNotificationSubscriptions() {
  const res = await http.Get<{ subscriptions: V2NotificationSubscription[], count: number }>(
    `${appPrefix}/notifications/subscriptions`,
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
  return res.subscriptions || []
}

export function v2UnsubscribeNotification(subscriptionId: string) {
  return http.Delete<V2NotificationSubscription>(
    `${appPrefix}/notifications/subscriptions/${subscriptionId}`,
    { meta: { ignoreAuth: false, toast: false } },
  )
}


export function v2RotateDlcToken(deviceId: string) {
  return http.Post<{ deviceId: string, dlcApiToken: string, dlcApiTokenIssued: boolean }>(
    `${appPrefix}/devices/${deviceId}/dlc-token/rotate`,
    {},
    { meta: { ignoreAuth: false, toast: true } },
  )
}

export async function v2IssueVoiceTicket() {
  return http.Post<{ ticket: string, expires_in: number }>(
    `${appPrefix}/voice/ticket`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export async function v2IssueDeviceStatusWsTicket(deviceId: string) {
  return http.Post<{ ticket: string, expires_in: number }>(
    `${appPrefix}/devices/${deviceId}/ws/ticket`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export async function bootstrapSessionAfterLogin(data: V2LoginResponse) {
  const expireAt = Math.floor(Date.now() / 1000) + (data.expiresIn || 86400)
  uni.setStorageSync('token', JSON.stringify({ token: data.token, expireAt }))
  if (data.openid)
    uni.setStorageSync('openid', data.openid)
  const { useUserStore } = await import('@/store')
  const userStore = useUserStore()
  const accountId = data.accountId || data.userId || ''
  if (accountId)
    userStore.setUserInfo({ ...userStore.userInfo, accountId })
  try {
    await userStore.getUserInfo()
  }
  catch (error) {
    console.warn('bootstrapSessionAfterLogin: getUserInfo failed', error)
  }
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
