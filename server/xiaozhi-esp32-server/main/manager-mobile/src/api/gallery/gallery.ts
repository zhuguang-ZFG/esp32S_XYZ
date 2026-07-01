import { http } from '@/http/request/alova'
import { getEnvBaseUrl } from '@/utils'

export interface GalleryImage {
  id: string
  accountId: string
  fileId: string
  filename: string
  mimeType: string
  sizeBytes: number
  thumbUrl: string
  tags: string[]
  status: string
  createdAt: string
}

export interface GalleryUploadResult {
  id: string
  fileId: string
  thumbUrl: string
  filename: string
}

const appPrefix = '/device/v1/app'

export function listGalleryItems(limit = 100, offset = 0) {
  return http.Get<{ images: GalleryImage[], count: number }>(
    `${appPrefix}/gallery`,
    { params: { limit, offset }, meta: { ignoreAuth: false, toast: false } },
  )
}

export function getGalleryUploadUrl() {
  return `${getEnvBaseUrl()}${appPrefix}/gallery`
}

export function deleteGalleryItem(imageId: string) {
  return http.Delete<{ deleted: boolean }>(
    `${appPrefix}/gallery/${imageId}`,
    { meta: { ignoreAuth: false, toast: false } },
  )
}
