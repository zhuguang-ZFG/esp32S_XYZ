// 声纹信息响应类型
export interface VoicePrint {
  id: string
  voiceprintId: string
  memberId: string
  memberName: string
  deviceId: string
  audioId: string
  sourceName: string
  introduce: string
  sampleCount: number
  confidence: number
  status: string
  createDate: string
  createdAt: string
}

// 语音对话记录类型
export interface ChatHistory {
  content: string
  audioId: string
}

// 创建说话人数据类型
export interface CreateSpeakerData {
  deviceId: string
  memberId: string
  audioId: string
  sourceName: string
  introduce: string
}

// 通用响应类型
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
}
