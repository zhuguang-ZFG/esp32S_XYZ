import type { GalleryImage } from '@/api/gallery'
import { getBearerToken, getEnvBaseUrl } from '@/utils'

export const GALLERY_PRELOAD_DEFAULT_COUNT = 6

export function galleryThumbSrc(image: Pick<GalleryImage, 'id' | 'thumbPath'>): string {
  const base = getEnvBaseUrl().replace(/\/$/, '')
  const path = image.thumbPath || `/device/v1/app/gallery/${image.id}/thumb`
  const token = getBearerToken()
  const url = `${base}${path}`
  if (!token) {
    return url
  }
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}access_token=${encodeURIComponent(token)}`
}

export function preloadGalleryThumbs(
  images: Pick<GalleryImage, 'id' | 'thumbPath'>[],
  limit = GALLERY_PRELOAD_DEFAULT_COUNT,
): void {
  const batch = images.slice(0, Math.max(0, limit))
  if (!batch.length) {
    return
  }
  const data = batch.map(image => ({ type: 'image' as const, src: galleryThumbSrc(image) }))
  // #ifdef MP-WEIXIN
  const wxApi = typeof wx !== 'undefined' ? (wx as any) : null
  if (wxApi?.preloadAssets) {
    wxApi.preloadAssets({ data })
    return
  }
  // #endif
  batch.forEach((image) => {
    uni.getImageInfo({ src: galleryThumbSrc(image) })
  })
}
