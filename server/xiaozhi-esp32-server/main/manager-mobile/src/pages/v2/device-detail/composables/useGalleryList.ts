import type { GalleryImage } from '@/api/gallery'
import { onUnmounted } from 'vue'
import { GALLERY_PAGE_SIZE, listGalleryImages } from '@/api/gallery'
import { t } from '@/i18n'
import {
  GALLERY_PRELOAD_DEFAULT_COUNT,
  clearGalleryPreloadCache,
  preloadGalleryThumbs,
} from '@/utils/galleryPreload'

export function useGalleryList() {
  const images = ref<GalleryImage[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const totalCount = ref(0)
  const preloadedUntil = ref(0)

  const hasMore = computed(() => images.value.length < totalCount.value)

  function resetPreloadState() {
    preloadedUntil.value = 0
    clearGalleryPreloadCache()
  }

  function preloadNextThumbs() {
    if (!images.value.length || preloadedUntil.value >= images.value.length) {
      return
    }
    preloadGalleryThumbs(images.value, {
      offset: preloadedUntil.value,
      limit: GALLERY_PRELOAD_DEFAULT_COUNT,
    })
    preloadedUntil.value = Math.min(
      preloadedUntil.value + GALLERY_PRELOAD_DEFAULT_COUNT,
      images.value.length,
    )
  }

  async function loadGallery(reset = true) {
    if (reset) {
      if (loading.value) {
        return
      }
      loading.value = true
    }
    else {
      if (loadingMore.value || loading.value || !hasMore.value) {
        return
      }
      loadingMore.value = true
    }

    try {
      const offset = reset ? 0 : images.value.length
      const res = await listGalleryImages(GALLERY_PAGE_SIZE, offset)
      if (typeof res.total === 'number') {
        totalCount.value = res.total
      }
      else {
        totalCount.value = offset + res.images.length + (res.images.length >= GALLERY_PAGE_SIZE ? 1 : 0)
      }
      if (reset) {
        images.value = res.images
        resetPreloadState()
      }
      else {
        const existing = new Set(images.value.map(row => row.id))
        images.value = [
          ...images.value,
          ...res.images.filter(row => !existing.has(row.id)),
        ]
      }
      preloadNextThumbs()
    }
    catch (error: any) {
      uni.showToast({ title: error?.message || t('v2.detail.galleryLoadFailed'), icon: 'none' })
    }
    finally {
      if (reset) {
        loading.value = false
      }
      else {
        loadingMore.value = false
      }
    }
  }

  function onGalleryScroll(event: { detail?: { scrollLeft?: number, scrollWidth?: number } }) {
    const scrollLeft = event.detail?.scrollLeft ?? 0
    const scrollWidth = event.detail?.scrollWidth ?? 0
    const itemPx = uni.upx2px(176)
    const viewportPx = uni.upx2px(750)
    const visibleEnd = Math.ceil((scrollLeft + viewportPx) / Math.max(itemPx, 1))
    if (visibleEnd + GALLERY_PRELOAD_DEFAULT_COUNT > preloadedUntil.value) {
      preloadNextThumbs()
    }
    if (scrollWidth > 0 && scrollLeft + viewportPx >= scrollWidth - uni.upx2px(120)) {
      loadGallery(false)
    }
  }

  function prependImage(image: GalleryImage) {
    const existed = images.value.some(row => row.id === image.id)
    images.value = [image, ...images.value.filter(row => row.id !== image.id)]
    if (!existed) {
      totalCount.value += 1
    }
    preloadGalleryThumbs([image])
  }

  function removeImageLocal(imageId: string) {
    const before = images.value.length
    images.value = images.value.filter(row => row.id !== imageId)
    if (images.value.length < before) {
      totalCount.value = Math.max(0, totalCount.value - 1)
    }
  }


  const onTokenRefreshed = () => loadGallery(true)
  uni.$on?.('gallery:token-refreshed', onTokenRefreshed)

  onUnmounted(() => {
    uni.$off?.('gallery:token-refreshed', onTokenRefreshed)
  })

  return {
    images,
    loading,
    loadingMore,
    totalCount,
    hasMore,
    loadGallery,
    onGalleryScroll,
    prependImage,
    removeImageLocal,
  }
}
