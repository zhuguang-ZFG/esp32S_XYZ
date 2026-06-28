import { http } from '@/http/request/alova'

export interface ImageGenerationRequest {
  prompt: string
  size?: string
  n?: number
}

export interface ImageGenerationResponse {
  created: number
  data: { url: string }[]
  /** 品牌标签（如「LiMa 生图」），非真实后端名；后端真实模型对外不可见。 */
  backend?: string
}

export function generateImage(request: ImageGenerationRequest) {
  return http.Post<ImageGenerationResponse>(
    '/device/v1/app/images/generations',
    { prompt: request.prompt, size: request.size || '1024x1024', n: request.n || 1 },
    { meta: { ignoreAuth: false, toast: false, isExposeError: true } },
  )
}
