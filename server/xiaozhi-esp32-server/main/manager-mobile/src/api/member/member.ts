import type { MemberListResponse } from './types'
import { http } from '@/http/request/alova'

const appPrefix = '/device/v1/app'

// 获取设备成员列表
export function getMemberList(deviceId: string) {
  return http.Get<MemberListResponse>(`${appPrefix}/devices/${deviceId}/members`, {
    meta: {
      ignoreAuth: false,
      toast: false,
    },
    cacheFor: {
      expire: 0,
    },
  }).then(res => res.members || [])
}
