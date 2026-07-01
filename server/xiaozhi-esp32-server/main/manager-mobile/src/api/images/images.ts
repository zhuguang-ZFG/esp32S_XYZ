import { http } from '@/http/request/alova'

export interface ImageGenerationRequest {
  prompt: string
  size?: string
  n?: number
  /** 参考图 URL；提供时后端执行图生图。 */
  image_url?: string
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
    {
      prompt: request.prompt,
      size: request.size || '1024x1024',
      n: request.n || 1,
      ...(request.image_url ? { image_url: request.image_url } : {}),
    },
    { meta: { ignoreAuth: false, toast: false, isExposeError: true } },
  )
}
