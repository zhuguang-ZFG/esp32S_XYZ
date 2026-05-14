import type { V2BindResponse, V2DeviceInfo, V2LoginResponse, V2SubmitTaskResponse } from './types'
import { http } from '@/http/request/alova'

/** 微信 code 登录换取 JWT */
export function v2Login(code: string) {
  return http.Post<V2LoginResponse>(
    '/api/v1/auth/login',
    { code },
    { meta: { ignoreAuth: true, toast: true, isExposeError: true } },
  )
}

/** 绑定设备（sn + activation_code） */
export function v2BindDevice(deviceSn: string, activationCode: string) {
  return http.Post<V2BindResponse>(
    '/api/v1/devices/bind',
    { device_sn: deviceSn, activation_code: activationCode },
    { meta: { ignoreAuth: false, toast: true } },
  )
}

/** 获取已绑定设备列表 */
export function v2GetDevices() {
  return http.Get<{ total: number; rows: V2DeviceInfo[] }>(
    '/api/v1/devices',
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
}

/** 获取单个设备信息 */
export function v2GetDeviceInfo(deviceId: string) {
  return http.Get<V2DeviceInfo>(
    `/api/v1/devices/${deviceId}/info`,
    { meta: { ignoreAuth: false, toast: false } },
  )
}

/** 提交任务 */
export function v2SubmitTask(deviceId: string, capability: string, params?: Record<string, unknown>) {
  return http.Post<V2SubmitTaskResponse>(
    `/api/v1/devices/${deviceId}/tasks`,
    { capability, params, source: 'client' },
    { meta: { ignoreAuth: false, toast: true } },
  )
}
