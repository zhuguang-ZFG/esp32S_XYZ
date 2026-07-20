<script lang="ts" setup>
import type { GalleryImage } from '@/api/gallery'
import { GALLERY_MAX_UPLOAD_BYTES, assertGalleryUploadSize, compressGalleryUploadPath, deleteGalleryImage, getGalleryDownloadUrl, uploadGalleryImage } from '@/api/gallery'
import { t } from '@/i18n'
import { useGalleryList } from '../composables/useGalleryList'
import { galleryThumbSrc } from '@/utils/galleryPreload'
import { formatBytes } from '@/utils/formatBytes'

const props = defineProps<{
  deviceBusy?: boolean
  drawFromImageLoading?: boolean
}>()

const emit = defineEmits<{
  drawFromImage: [image: GalleryImage]
}>()

const {
  images,
  loading,
  loadingMore,
  totalCount,
  hasMore,
  loadGallery,
  onGalleryScroll,
  prependImage,
  removeImageLocal,
} = useGalleryList()

const uploading = ref(false)
const uploadProgress = ref(0)
const selectedId = ref('')
const thumbErrors = ref<Record<string, boolean>>({})

const selectedImage = computed(() => images.value.find(row => row.id === selectedId.value) ?? null)
const countLabel = computed(() => {
  if (!totalCount.value) {
    return ''
  }
  if (images.value.length >= totalCount.value) {
    return String(totalCount.value)
  }
  return `${images.value.length}/${totalCount.value}`
})

function chooseAndUpload() {
  if (props.deviceBusy || uploading.value) {
    return
  }
  uni.chooseMedia({
    count: 1,
    mediaType: ['image'],
    success: async (res) => {
      const file = res.tempFiles?.[0]
      if (!file?.tempFilePath) {
        return
      }
      if ((file.size ?? 0) > GALLERY_MAX_UPLOAD_BYTES) {
        uni.showToast({ title: t('v2.detail.galleryTooLarge'), icon: 'none' })
        return
      }
      uploading.value = true
      uploadProgress.value = 0
      try {
        const uploadPath = await compressGalleryUploadPath(file.tempFilePath)
        await assertGalleryUploadSize(uploadPath, GALLERY_MAX_UPLOAD_BYTES)
        const image = await uploadGalleryImage(uploadPath, (percent) => {
          uploadProgress.value = Math.max(0, Math.min(100, Math.round(percent)))
        })
        prependImage(image)
        selectedId.value = image.id
        uni.showToast({ title: t('v2.detail.galleryUploadSuccess'), icon: 'success' })
      }
      catch (error: any) {
        uni.showToast({ title: error?.message || t('v2.detail.galleryUploadFailed'), icon: 'none' })
      }
      finally {
        uploading.value = false
        uploadProgress.value = 0
      }
    },
  })
}

function selectImage(image: GalleryImage) {
  if (selectedId.value === image.id) {
    previewImage(image)
    return
  }
  selectedId.value = image.id
}

async function previewImage(image: GalleryImage) {
  try {
    const download = await getGalleryDownloadUrl(image.id)
    const urls = images.value.map((item) => {
      return item.id === image.id ? download.url : galleryThumbSrc(item)
    })
    uni.previewImage({
      current: download.url,
      urls,
    })
  }
  catch (error: any) {
    uni.showToast({ title: error?.message || t('common.fail'), icon: 'none' })
  }
}

function previewSelected() {
  if (!selectedImage.value) {
    uni.showToast({ title: t('v2.detail.gallerySelectFirst'), icon: 'none' })
    return
  }
  previewImage(selectedImage.value)
}

function confirmRemoveImage(image: GalleryImage) {
  uni.showModal({
    title: t('common.confirm'),
    content: t('v2.detail.galleryDeleteConfirm'),
    success: (res) => {
      if (res.confirm) {
        removeImage(image)
      }
    },
  })
}

async function removeImage(image: GalleryImage) {
  try {
    await deleteGalleryImage(image.id)
    removeImageLocal(image.id)
    delete thumbErrors.value[image.id]
    if (selectedId.value === image.id) {
      selectedId.value = ''
    }
    uni.showToast({ title: t('v2.detail.galleryDeleted'), icon: 'success' })
  }
  catch (error: any) {
    uni.showToast({ title: error?.message || t('common.fail'), icon: 'none' })
  }
}

function submitSelected() {
  const image = selectedImage.value
  if (!image) {
    uni.showToast({ title: t('v2.detail.gallerySelectFirst'), icon: 'none' })
    return
  }
  emit('drawFromImage', image)
}

function onThumbError(imageId: string) {
  thumbErrors.value[imageId] = true
}

onMounted(() => {
  loadGallery(true)
})

defineExpose({ reload: () => loadGallery(true) })
</script>

<template>
  <view class="bento-card">
    <view class="title-row">
      <view class="bento-title">
        {{ t('v2.detail.galleryTitle') }}
      </view>
      <text v-if="countLabel" class="count-badge">
        {{ countLabel }}
      </text>
    </view>
    <text class="hint-text">
      {{ t('v2.detail.galleryDesc') }}
    </text>
    <text v-if="deviceBusy" class="busy-hint">
      {{ t('v2.detail.deviceBusyHint') }}
    </text>
    <view v-if="uploading" class="upload-progress">
      <view class="upload-progress-bar" :style="{ width: `${uploadProgress}%` }" />
      <text class="upload-progress-text">{{ uploadProgress }}%</text>
    </view>
    <view class="toolbar">
      <wd-button type="info" round plain size="small" :loading="uploading" :disabled="deviceBusy" @click="chooseAndUpload">
        {{ uploading ? t('v2.detail.submitting') : t('v2.detail.galleryUpload') }}
      </wd-button>
      <wd-button type="info" round plain size="small" :loading="loading" @click="loadGallery(true)">
        {{ t('v2.detail.galleryRefresh') }}
      </wd-button>
      <wd-button
        v-if="selectedId"
        type="info"
        round
        plain
        size="small"
        @click="previewSelected"
      >
        {{ t('v2.detail.galleryPreview') }}
      </wd-button>
    </view>
    <scroll-view scroll-x class="gallery-scroll" enable-flex @scroll="onGalleryScroll">
      <view v-if="loading && !images.length" class="skeleton-row">
        <view v-for="n in 4" :key="n" class="gallery-skeleton" />
      </view>
      <!-- M15:空态三件套 icon+文案+直接动作 -->
      <view v-else-if="!images.length" class="empty-block">
        <wd-icon name="photo" size="48" color="var(--dim)" />
        <text class="empty-hint">{{ t('v2.detail.galleryEmpty') }}</text>
        <wd-button type="primary" round size="small" :disabled="deviceBusy || uploading" @click="chooseAndUpload">
          {{ t('v2.detail.galleryUploadFirst') }}
        </wd-button>
      </view>
      <template v-else>
        <view
          v-for="item in images"
          :key="item.id"
          class="gallery-item"
          :class="{ selected: selectedId === item.id }"
          @click="selectImage(item)"
          @longpress="confirmRemoveImage(item)"
        >
          <view class="thumb-wrap">
            <image
              v-if="!thumbErrors[item.id]"
              class="gallery-thumb"
              lazy-load
              mode="aspectFill"
              :src="galleryThumbSrc(item)"
              @error="onThumbError(item.id)"
            />
            <view v-else class="gallery-thumb thumb-fallback">
              <text class="fallback-text">{{ t('v2.detail.galleryThumbFailed') }}</text>
            </view>
            <!-- M14:选中态显式删除入口(longpress 仍保留),走同一确认弹窗 -->
            <view
              v-if="selectedId === item.id"
              class="delete-badge"
              @click.stop="confirmRemoveImage(item)"
            >
              <wd-icon name="close" size="14" color="var(--text)" />
            </view>
          </view>
          <text class="gallery-name">{{ item.filename }}</text>
          <text v-if="item.sizeBytes" class="gallery-meta">{{ formatBytes(item.sizeBytes) }}</text>
        </view>
        <view v-if="loadingMore" class="gallery-item load-more-item">
          <view class="gallery-thumb load-more-box">
            <text class="load-more-text">{{ t('v2.detail.galleryLoadingMore') }}</text>
          </view>
        </view>
        <view v-else-if="hasMore" class="gallery-item load-more-item" @click="loadGallery(false)">
          <view class="gallery-thumb load-more-box tapable">
            <text class="load-more-text">{{ t('v2.detail.galleryLoadMore') }}</text>
          </view>
        </view>
      </template>
    </scroll-view>
    <wd-button
      type="primary"
      round
      block
      size="large"
      class="draw-btn"
      :loading="drawFromImageLoading"
      :disabled="deviceBusy || !selectedId"
      @click="submitSelected"
    >
      {{ drawFromImageLoading ? t('v2.detail.submitting') : t('v2.detail.galleryDrawSelected') }}
    </wd-button>
  </view>
</template>

<style lang="scss" scoped>
.title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 8rpx;

  .bento-title {
    margin-bottom: 0;
  }
}

.count-badge {
  min-width: 40rpx;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: var(--bg2);
  color: var(--dim);
  font-size: 22rpx;
  text-align: center;
}

.hint-text {
  display: block;
  font-size: 24rpx;
  color: var(--dim);
  margin-bottom: 20rpx;
}

/* M18:busy 是等待态非错误态,danger → amber */
.busy-hint {
  display: block;
  font-size: 24rpx;
  color: var(--amber);
  margin-bottom: 12rpx;
}

.upload-progress {
  position: relative;
  height: 12rpx;
  margin-bottom: 16rpx;
  border-radius: 999rpx;
  background: var(--bg2);
  overflow: hidden;
}

.upload-progress-bar {
  height: 100%;
  background: var(--accent);
  transition: width 0.2s ease;
}

.upload-progress-text {
  position: absolute;
  right: 0;
  top: 16rpx;
  font-size: 20rpx;
  color: var(--dim);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.gallery-scroll {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
  white-space: nowrap;
  margin-bottom: 20rpx;
}

.skeleton-row {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
}

.gallery-skeleton {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  background: linear-gradient(90deg, var(--bg2) 25%, var(--border) 37%, var(--bg2) 63%);
  background-size: 400% 100%;
  animation: gallery-shimmer 1.2s ease infinite;
}

@keyframes gallery-shimmer {
  0% {
    background-position: 100% 0;
  }

  100% {
    background-position: 0 0;
  }
}

.empty-hint {
  font-size: 24rpx;
  color: var(--dim);
  padding: 12rpx 0;
}

/* M15:空态块 */
.empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
  width: 100%;
}

.gallery-item {
  display: inline-flex;
  flex-direction: column;
  width: 160rpx;
  margin-right: 16rpx;
  vertical-align: top;

  &.selected .gallery-thumb,
  &.selected .thumb-fallback,
  &.selected .load-more-box {
    border-color: var(--accent);
    box-shadow: 0 0 0 2rpx var(--accent-glow);
  }
}

.thumb-wrap {
  position: relative;
}

/* M14:选中角标删除钮(视觉 56rpx,含 padding 触控 ≥72rpx) */
.delete-badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: var(--danger);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.thumb-wrap,
.gallery-thumb,
.thumb-fallback,
.load-more-box {
  width: 160rpx;
  height: 160rpx;
}

.gallery-thumb,
.thumb-fallback,
.load-more-box {
  border-radius: 16rpx;
  border: 2rpx solid var(--border);
  background: var(--bg2);
}

.thumb-fallback,
.load-more-box {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12rpx;
}

.load-more-box.tapable {
  border-style: dashed;
}

.fallback-text,
.load-more-text {
  font-size: 20rpx;
  color: var(--dim);
  text-align: center;
}

.gallery-name {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gallery-meta {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: var(--faint);
}

.draw-btn {
  margin-top: 8rpx;
}
</style>
