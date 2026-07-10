import type { GalleryDownloadResponse, GalleryImage, GalleryListResponse } from './types'
import { http } from '@/http/request/alova'
import { getBearerToken, getEnvBaseUrl } from '@/utils'

const appPrefix = '/device/v1/app'

export const GALLERY_MAX_UPLOAD_BYTES = 10 * 1024 * 1024
export const GALLERY_PAGE_SIZE = 24

export function compressGalleryUploadPath(tempFilePath: string): Promise<string> {
  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    uni.compressImage({
      src: tempFilePath,
      quality: 80,
      success: (res) => resolve(res.tempFilePath || tempFilePath),
      fail: () => resolve(tempFilePath),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve(tempFilePath)
    // #endif
  })
}

export function assertGalleryUploadSize(filePath: string, maxBytes: number): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.getFileInfo({
      filePath,
      success: (info) => {
        if ((info.size ?? 0) > maxBytes) {
          reject(new Error(`file exceeds ${Math.round(maxBytes / 1024 / 1024)}MB`))
          return
        }
        resolve()
      },
      fail: () => resolve(),
    })
  })
}

export async function listGalleryImages(limit = GALLERY_PAGE_SIZE, offset = 0) {
  const res = await http.Get<{ images: GalleryImage[], count: number, total: number }>(
    `${appPrefix}/gallery`,
    {
      params: { limit, offset },
      meta: { ignoreAuth: false, toast: false },
      cacheFor: { expire: 0 },
    },
  )
  return {
    images: res.images || [],
    count: res.count || 0,
    total: res.total ?? res.count ?? 0,
  } satisfies GalleryListResponse
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

export function uploadGalleryImage(
  tempFilePath: string,
  onProgress?: (percent: number) => void,
): Promise<GalleryImage> {
  const token = getBearerToken()
  const url = `${getEnvBaseUrl().replace(/\/$/, '')}${appPrefix}/gallery`
  return new Promise((resolve, reject) => {
    const uploadTask = uni.uploadFile({
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
    uploadTask.onProgressUpdate?.((event) => {
      onProgress?.(event.progress)
    })
  })
}
