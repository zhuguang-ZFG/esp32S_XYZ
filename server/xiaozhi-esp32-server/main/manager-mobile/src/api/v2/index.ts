import type { V2BindResponse, V2DeletionResponse, V2DeviceInfo, V2DeviceSupplyResponse, V2DeviceSupplyUpdateRequest, V2DeviceTransferRequest, V2DeviceTransferResponse, V2LoginResponse, V2PendingVoiceTaskResponse, V2SelfCheckHistoryResponse, V2SubmitTaskResponse } from './types'
import { http } from '@/http/request/alova'

export function v2Login(code: string) {
  return http.Post<V2LoginResponse>('/api/v1/auth/login', { code }, { meta: { ignoreAuth: true, toast: true, isExposeError: true } })
}
export function v2BindDevice(deviceSn: string, activationCode: string) {
  return http.Post<V2BindResponse>('/api/v1/devices/bind', { device_sn: deviceSn, activation_code: activationCode }, { meta: { ignoreAuth: false, toast: true } })
}
export function v2GetDevices() {
  return http.Get<{ total: number; rows: V2DeviceInfo[] }>('/api/v1/devices', { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } })
}
export function v2GetDeviceInfo(deviceId: string) {
  return http.Get<V2DeviceInfo>(`/api/v1/devices/${deviceId}/info`, { meta: { ignoreAuth: false, toast: false } })
}
export function v2SubmitTask(deviceId: string, capability: string, params?: Record<string, unknown>, requestId = createTaskRequestId(capability)) {
  return http.Post<V2SubmitTaskResponse>(
    `/api/v1/devices/${deviceId}/tasks`,
    { capability, requestId, params, source: 'client' },
    { meta: { ignoreAuth: false, toast: false } },
  )
}
export function v2ListPendingVoiceTasks(deviceId: string) {
  return http.Post<V2PendingVoiceTaskResponse[]>(
    `/api/v1/devices/${deviceId}/voice-tasks/pending`,
    {},
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
}
export function v2ListSelfCheckHistory(deviceId: string) {
  return http.Post<V2SelfCheckHistoryResponse[]>(
    `/api/v1/devices/${deviceId}/self-check/history`,
    {},
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
}
export function v2ApproveVoiceTask(taskId: string, reason?: string) {
  return http.Post<V2SubmitTaskResponse>(
    `/api/v1/tasks/${taskId}/approve`,
    reason ? { reason } : {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}
export function v2RejectVoiceTask(taskId: string, reason?: string) {
  return http.Post<V2SubmitTaskResponse>(
    `/api/v1/tasks/${taskId}/reject`,
    reason ? { reason } : {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}
export function v2UpdateDeviceSupplies(deviceId: string, request: V2DeviceSupplyUpdateRequest) {
  return http.Post<V2DeviceSupplyResponse>(
    `/api/v1/devices/${deviceId}/supplies`,
    request,
    { meta: { ignoreAuth: false, toast: false } },
  )
}
export function v2RequestDeviceTransfer(deviceId: string, request: V2DeviceTransferRequest) {
  return http.Post<V2DeviceTransferResponse>(
    `/api/v1/devices/${deviceId}/transfer`,
    request,
    { meta: { ignoreAuth: false, toast: false } },
  )
}
export function v2AcceptDeviceTransfer(transferId: number) {
  return http.Post<V2DeviceTransferResponse>(
    `/api/v1/device-transfers/${transferId}/accept`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}
export function v2CancelDeviceTransfer(transferId: number) {
  return http.Post<V2DeviceTransferResponse>(
    `/api/v1/device-transfers/${transferId}/cancel`,
    {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}
export function v2ListPendingIncomingDeviceTransfers() {
  return http.Post<V2DeviceTransferResponse[]>(
    '/api/v1/device-transfers/pending-incoming',
    {},
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
}
export function v2DeleteAccount() {
  return http.Post<V2DeletionResponse>(
    '/api/v1/account/delete',
    {},
    { meta: { ignoreAuth: false, toast: false } },
  )
}

function createTaskRequestId(capability: string) {
  const safeCapability = capability.replace(/[^a-zA-Z0-9_-]/g, '_') || 'task'
  const randomPart = Math.random().toString(36).slice(2, 10)
  return `client-${safeCapability}-${Date.now().toString(36)}-${randomPart}`
}
