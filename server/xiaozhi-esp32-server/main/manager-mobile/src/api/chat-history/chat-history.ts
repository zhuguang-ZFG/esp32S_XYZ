import type {
  ChatMessagesResponse,
  ChatSessionsResponse,
  GetSessionsParams,
} from './types'
import { http } from '@/http/request/alova'

const appPrefix = '/device/v1/app'

/**
 * 获取聊天会话列表
 * @param deviceId 设备ID
 * @param params 分页参数
 */
export function getChatSessions(deviceId: string, params: GetSessionsParams) {
  return http.Get<ChatSessionsResponse>(`${appPrefix}/devices/${deviceId}/chat-sessions`, {
    params,
    meta: {
      ignoreAuth: false,
      toast: false,
    },
    cacheFor: {
      expire: 0,
    },
  })
}

/**
 * 获取聊天记录详情
 * @param deviceId 设备ID
 * @param sessionId 会话ID
 */
export function getChatHistory(deviceId: string, sessionId: string) {
  return http.Get<ChatMessagesResponse>(`${appPrefix}/devices/${deviceId}/chat-sessions/${sessionId}/messages`, {
    meta: {
      ignoreAuth: false,
      toast: false,
    },
    cacheFor: {
      expire: -1,
    },
  }).then(res => res?.messages || [])
}

/**
 * 获取音频元数据
 * @param audioId 音频ID
 */
export function getAudioId(audioId: string) {
  return http.Get<{ audioId: string, url: string, contentType: string }>(`${appPrefix}/audio/${audioId}`, {
    meta: {
      ignoreAuth: false,
      toast: false,
    },
  })
}

/**
 * 获取音频播放地址
 * @param audioId 音频ID
 */
export function getAudioPlayUrl(audioId: string) {
  return `${appPrefix}/audio/${audioId}`
}
