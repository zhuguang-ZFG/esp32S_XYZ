export interface V2DeviceInfo {
  deviceId: string
  model: string
  hwRev: string
  fwRev: string
  workspaceMm: { x: number; y: number; z: number }
  status: string
  lastSeenAt: string
}
export interface V2BindResponse { deviceId: string; message: string }
export interface V2SubmitTaskResponse { taskId: string; status: string; approvalRequiredBy?: string }
export interface V2PendingVoiceTaskResponse {
  taskId: string
  deviceId: string
  requestId: string
  capability: string
  paramsJson?: string
  constraintsJson?: string
  status: string
  createdAt?: string
}
export interface V2SelfCheckHistoryResponse {
  id: number
  deviceId: string
  checkId: string
  scope: string
  status: string
  summary?: string
  checksJson?: string
  reportedAt?: string
}
export interface V2LoginResponse { token: string; userId: number }
export interface V2DeviceSupplyUpdateRequest {
  paperSlotState?: 'empty' | 'loaded' | 'unknown'
  penInstalledAt?: string
  penInkPercentEst?: number
  resetPenMileage?: boolean
}
export interface V2DeviceSupplyResponse {
  deviceId: string
  paperSlotState: 'empty' | 'loaded' | 'unknown'
  penInstalledAt?: string
  penInkPercentEst?: number
  penMileageMm?: number
}
export interface V2DeviceTransferRequest {
  targetUnionid: string
}
export interface V2DeviceTransferResponse {
  transferId: number
  deviceId: string
  sourceAccountId: number
  targetAccountId: number
  status: 'pending' | 'accepted' | 'cancelled'
}
export interface V2DeletionResponse {
  status: string
  affectedRows: number
  auditRetentionDays: number
}
