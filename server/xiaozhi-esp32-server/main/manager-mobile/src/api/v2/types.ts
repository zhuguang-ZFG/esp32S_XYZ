export interface V2DeviceInfo {
  deviceId: string
  model: string
  hwRev: string
  fwRev: string
  workspaceMm: { x: number, y: number, z: number }
  status: string
  lastSeenAt: string
}
export interface V2BindResponse { deviceId: string, message: string }
export interface V2SubmitTaskResponse { taskId: string, status: string, approvalRequiredBy?: string }

/** 写字参数（write_text capability） */
export interface WriteTextParams {
  text: string
  feed?: number // 1-2000 mm/min，默认 900
}

/** 仿手写参数（handwriting capability） */
export interface HandwritingParams {
  text: string
  font_type?: string // FONT_OPTIONS key，默认 "0"
  paper_bg_type?: string
  mistake_rate?: number // 0-100，默认 3
  messy_ratio?: number // 0-100，默认 0
  char_random?: number // 0-100，默认 0
  feed?: number // 1-2000 mm/min
}

/** AI 绘图参数（draw_generated capability） */
export interface DrawGeneratedParams {
  prompt: string
  feed?: number
  model?: string
  size?: string
}
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
export interface V2LoginResponse {
  token: string
  userId: string
  accountId?: string
  openid?: string
  expiresIn?: number
}
export interface V2MeResponse {
  accountId: string
  openid?: string
  phone?: string
  nickname?: string
  avatarUrl?: string
  role?: string
  createdAt?: string
}
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
  targetPhone: string
}
export interface V2DeviceTransferResponse {
  transferId: string
  deviceId: string
  sourceAccountId: string
  targetAccountId: string
  status: 'pending' | 'accepted' | 'cancelled'
}
export interface V2TaskInfo {
  taskId: string
  status: string
  deviceId: string
  capability?: string
  params?: Record<string, any>
  sent?: boolean
  queueDepth?: number
  createdAt?: string
  updatedAt?: string
  result?: Record<string, any>
  imageUrl?: string
  error?: string
}

export interface V2TaskListResponse {
  tasks: V2TaskInfo[]
  count: number
}

export interface V2DeletionResponse {
  status: string
  affectedRows: number
  auditRetentionDays: number
}
