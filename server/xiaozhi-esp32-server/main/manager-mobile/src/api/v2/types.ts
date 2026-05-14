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
export interface V2SubmitTaskResponse { taskId: string; status: string }
export interface V2LoginResponse { token: string; userId: number }
