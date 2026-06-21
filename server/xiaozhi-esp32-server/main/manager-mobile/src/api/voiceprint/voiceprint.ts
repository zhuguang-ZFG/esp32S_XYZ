import type {
  ChatHistory,
  CreateSpeakerData,
  VoicePrint,
} from './types'
import { http } from '@/http/request/alova'

const appPrefix = '/device/v1/app'

// 获取声纹列表
export function getVoicePrintList(deviceId: string) {
  return http.Get<{ voiceprints: VoicePrint[], count: number }>(`${appPrefix}/devices/${deviceId}/voiceprints`, {
    meta: {
      ignoreAuth: false,
      toast: false,
    },
    cacheFor: {
      expire: 0,
    },
  }).then(res => res.voiceprints || [])
}

// 获取语音对话记录（用于选择声纹向量）
export function getChatHistory(deviceId: string) {
  return http.Get<{ chatHistory: ChatHistory[], count: number }>(`${appPrefix}/devices/${deviceId}/chat-history`, {
    meta: {
      ignoreAuth: false,
      toast: false,
    },
    cacheFor: {
      expire: 0,
    },
  }).then(res => res.chatHistory || [])
}

// 新增说话人
export function createVoicePrint(data: CreateSpeakerData) {
  return http.Post<null>(`${appPrefix}/voiceprints/enroll`, {
    deviceId: data.deviceId,
    memberId: data.memberId,
    audioId: data.audioId,
    sourceName: data.sourceName,
    introduce: data.introduce,
  }, {
    meta: {
      ignoreAuth: false,
      toast: true,
    },
  })
}

// 删除声纹
export function deleteVoicePrint(id: string) {
  return http.Delete<null>(`${appPrefix}/voiceprints/${id}`, {
    meta: {
      ignoreAuth: false,
      toast: true,
    },
  })
}

// 更新声纹信息
export function updateVoicePrint(data: VoicePrint) {
  return http.Put<null>(`${appPrefix}/voiceprints/${data.id}`, {
    sourceName: data.sourceName,
    introduce: data.introduce,
    audioId: data.audioId,
  }, {
    meta: {
      ignoreAuth: false,
      toast: true,
    },
  })
}

// 获取音频下载ID
export function getAudioDownloadId(audioId: string) {
  return http.Get<{ audioId: string, url: string, contentType: string }>(`${appPrefix}/audio/${audioId}`, {
    meta: {
      ignoreAuth: false,
      toast: false,
    },
  })
}
