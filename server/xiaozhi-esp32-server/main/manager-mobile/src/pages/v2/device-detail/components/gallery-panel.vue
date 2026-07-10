<script lang="ts" setup>
import type { GalleryImage } from '@/api/gallery'
import { deleteGalleryImage, listGalleryImages, uploadGalleryImage } from '@/api/gallery'
import { t } from '@/i18n'
import { galleryThumbSrc, preloadGalleryThumbs } from '@/utils/galleryPreload'

const props = defineProps<{
  deviceBusy?: boolean
  drawFromImageLoading?: boolean
}>()

const emit = defineEmits<{
  drawFromImage: [image: GalleryImage]
}>()

const images = ref<GalleryImage[]>([])
const loading = ref(false)
const uploading = ref(false)
const selectedId = ref('')

async function loadGallery() {
  loading.value = true
  try {
    const res = await listGalleryImages()
    images.value = res.images
    preloadGalleryThumbs(res.images)
  }
  catch (error: any) {
    uni.showToast({ title: error?.message || t('v2.detail.galleryLoadFailed'), icon: 'none' })
  }
  finally {
    loading.value = false
  }
}

function chooseAndUpload() {
  if (props.deviceBusy || uploading.value)
    return
  uni.chooseMedia({
    count: 1,
    mediaType: ['image'],
    success: async (res) => {
      const file = res.tempFiles?.[0]
      if (!file?.tempFilePath)
        return
      uploading.value = true
      try {
        const image = await uploadGalleryImage(file.tempFilePath)
        images.value = [image, ...images.value.filter(row => row.id !== image.id)]
        preloadGalleryThumbs([image])
        selectedId.value = image.id
        uni.showToast({ title: t('v2.detail.galleryUploadSuccess'), icon: 'success' })
      }
      catch (error: any) {
        uni.showToast({ title: error?.message || t('v2.detail.galleryUploadFailed'), icon: 'none' })
      }
      finally {
        uploading.value = false
      }
    },
  })
}

function selectImage(image: GalleryImage) {
  selectedId.value = image.id
}

function confirmRemoveImage(image: GalleryImage) {
  uni.showModal({
    title: t('common.confirm'),
    content: t('v2.detail.galleryDeleteConfirm'),
    success: (res) => {
      if (res.confirm)
        removeImage(image)
    },
  })
}

async function removeImage(image: GalleryImage) {
  try {
    await deleteGalleryImage(image.id)
    images.value = images.value.filter(row => row.id !== image.id)
    if (selectedId.value === image.id)
      selectedId.value = ''
    uni.showToast({ title: t('v2.detail.galleryDeleted'), icon: 'success' })
  }
  catch (error: any) {
    uni.showToast({ title: error?.message || t('common.fail'), icon: 'none' })
  }
}

function submitSelected() {
  const image = images.value.find(row => row.id === selectedId.value)
  if (!image) {
    uni.showToast({ title: t('v2.detail.gallerySelectFirst'), icon: 'none' })
    return
  }
  emit('drawFromImage', image)
}

onMounted(() => {
  loadGallery()
})

defineExpose({ reload: loadGallery })
</script>

<template>
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.galleryTitle') }}
    </view>
    <text class="hint-text">
      {{ t('v2.detail.galleryDesc') }}
    </text>
    <view class="toolbar">
      <wd-button type="info" round plain size="small" :loading="uploading" :disabled="deviceBusy" @click="chooseAndUpload">
        {{ uploading ? t('v2.detail.submitting') : t('v2.detail.galleryUpload') }}
      </wd-button>
      <wd-button type="info" round plain size="small" :loading="loading" @click="loadGallery">
        {{ t('v2.detail.galleryRefresh') }}
      </wd-button>
    </view>
    <scroll-view scroll-x class="gallery-scroll" enable-flex>
      <view v-if="!images.length && !loading" class="empty-hint">
        {{ t('v2.detail.galleryEmpty') }}
      </view>
      <view
        v-for="item in images"
        :key="item.id"
        class="gallery-item"
        :class="{ selected: selectedId === item.id }"
        @click="selectImage(item)"
        @longpress="confirmRemoveImage(item)"
      >
        <image class="gallery-thumb" lazy-load mode="aspectFill" :src="galleryThumbSrc(item)" />
        <text class="gallery-name">{{ item.filename }}</text>
      </view>
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
.bento-card {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: var(--r);
  padding: 28rpx;
}

.bento-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8rpx;
}

.hint-text {
  display: block;
  font-size: 24rpx;
  color: var(--dim);
  margin-bottom: 20rpx;
}

.toolbar {
  display: flex;
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

.empty-hint {
  font-size: 24rpx;
  color: var(--dim);
  padding: 12rpx 0;
}

.gallery-item {
  display: inline-flex;
  flex-direction: column;
  width: 160rpx;
  margin-right: 16rpx;
  vertical-align: top;

  &.selected .gallery-thumb {
    border-color: var(--accent);
    box-shadow: 0 0 0 2rpx rgba(59, 130, 246, 0.35);
  }
}

.gallery-thumb {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  border: 2rpx solid var(--border);
  background: var(--bg2);
}

.gallery-name {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draw-btn {
  margin-top: 8rpx;
}
</style>
