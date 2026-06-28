import { http } from '@/http/request/alova'

export interface ImageGenerationRequest {
  prompt: string
  size?: string
  n?: number
}

export interface ImageGenerationResponse {
  created: number
  data: { url: string }[]
  backend?: string
}

export function generateImage(request: ImageGenerationRequest) {
  return http.Post<ImageGenerationResponse>(
    '/device/v1/app/images/generations',
    { prompt: request.prompt, size: request.size || '1024x1024', n: request.n || 1 },
    { meta: { ignoreAuth: false, toast: false, isExposeError: true } },
  )
}
