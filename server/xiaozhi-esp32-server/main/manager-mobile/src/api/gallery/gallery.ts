import type { GalleryDownloadResponse, GalleryImage, GalleryListResponse } from './types'
import { http } from '@/http/request/alova'
import { getBearerToken, getEnvBaseUrl } from '@/utils'

const appPrefix = '/device/v1/app'

export async function listGalleryImages(limit = 100, offset = 0) {
  const res = await http.Get<{ images: GalleryImage[], count: number }>(
    `${appPrefix}/gallery`,
    {
      params: { limit, offset },
      meta: { ignoreAuth: false, toast: false },
      cacheFor: { expire: 0 },
    },
  )
  return { images: res.images || [], count: res.count || 0 } satisfies GalleryListResponse
}

export function getGalleryDownloadUrl(imageId: string) {
  return http.Get<GalleryDownloadResponse>(
    `${appPrefix}/gallery/${imageId}/download`,
    { meta: { ignoreAuth: false, toast: false }, cacheFor: { expire: 0 } },
  )
}

export function deleteGalleryImage(imageId: string) {
  return http.Delete<{ deleted: boolean }>(
    `${appPrefix}/gallery/${imageId}`,
    { meta: { ignoreAuth: false, toast: false } },
  )
}

export function uploadGalleryImage(tempFilePath: string): Promise<GalleryImage> {
  const token = getBearerToken()
  const url = `${getEnvBaseUrl().replace(/\/$/, '')}${appPrefix}/gallery`
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url,
      filePath: tempFilePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const body = JSON.parse(res.data) as { code: number, data: GalleryImage, message?: string }
          if (res.statusCode !== 200 || body.code !== 0 || !body.data?.id) {
            reject(new Error(body.message || `upload failed (${res.statusCode})`))
            return
          }
          resolve(body.data)
        }
        catch (error) {
          reject(error)
        }
      },
      fail: reject,
    })
  })
}
