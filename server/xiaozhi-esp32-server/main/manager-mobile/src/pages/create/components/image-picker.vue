<script lang="ts" setup>
import type { GalleryImage } from '@/api/gallery'
import { ref, watch } from 'vue'
import { getGalleryUploadUrl, listGalleryItems } from '@/api/gallery'
import { t } from '@/i18n'
import { useUpload } from '@/utils/uploadFile'

defineOptions({ name: 'ImagePicker' })

const props = defineProps<{
  modelValue?: string
  uploadOnSelect?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', url: string): void
}>()

const selectedUrl = ref(props.modelValue || '')
const showGallery = ref(false)
const galleryItems = ref<GalleryImage[]>([])
const loadingGallery = ref(false)

watch(() => props.modelValue, (val) => {
  selectedUrl.value = val || ''
})

watch(selectedUrl, (val) => {
  emit('update:modelValue', val)
})

function clear() {
  selectedUrl.value = ''
}

async function chooseFromAlbum() {
  const uploader = useUpload<GalleryImage>(
    getGalleryUploadUrl(),
    {},
    {
      maxSize: 10,
      sourceType: ['album'],
      sizeType: ['compressed'],
      onSuccess: (res) => {
        const item = res as unknown as GalleryImage
        if (item?.thumbUrl) {
          selectedUrl.value = item.thumbUrl
        }
        else {
          uni.showToast({ title: t('create.imageNoResult'), icon: 'none' })
        }
      },
      onError: (err) => {
        console.error('upload gallery image failed', err)
        uni.showToast({ title: t('create.imageFailed'), icon: 'none' })
      },
    },
  )
  uploader.run()
}

async function openGallery() {
  showGallery.value = true
  loadingGallery.value = true
  try {
    const res = await listGalleryItems(100, 0)
    galleryItems.value = res.images || []
  }
  catch (e) {
    console.error('load gallery failed', e)
    uni.showToast({ title: t('create.loadDevicesFailed'), icon: 'none' })
  }
  finally {
    loadingGallery.value = false
  }
}

function selectGalleryItem(item: GalleryImage) {
  selectedUrl.value = item.thumbUrl
  showGallery.value = false
}

function closeGallery() {
  showGallery.value = false
}

function previewSelected() {
  if (selectedUrl.value) {
    uni.previewImage({ urls: [selectedUrl.value], current: selectedUrl.value })
  }
}
</script>

<template>
  <view class="image-picker">
    <!-- 已选图片预览 -->
    <view v-if="selectedUrl" class="selected-preview">
      <image class="preview-img" :src="selectedUrl" mode="aspectFill" @click="previewSelected" />
      <view class="clear-btn" @click="clear">
        <wd-icon name="close" size="14" color="#fff" />
      </view>
    </view>

    <!-- 选择按钮 -->
    <view v-else class="picker-actions">
      <view class="picker-btn" @click="chooseFromAlbum">
        <wd-icon name="picture" size="28" color="var(--accent)" />
        <text>{{ t('create.chooseFromAlbum') }}</text>
      </view>
      <view class="picker-btn" @click="openGallery">
        <wd-icon name="folder-open" size="28" color="var(--accent)" />
        <text>{{ t('create.chooseFromGallery') }}</text>
      </view>
    </view>

    <!-- 已选时仍可重新选择 -->
    <view v-if="selectedUrl" class="picker-actions compact">
      <view class="picker-btn secondary" @click="chooseFromAlbum">
        <wd-icon name="picture" size="20" color="var(--accent)" />
        <text>{{ t('create.chooseFromAlbum') }}</text>
      </view>
      <view class="picker-btn secondary" @click="openGallery">
        <wd-icon name="folder-open" size="20" color="var(--accent)" />
        <text>{{ t('create.chooseFromGallery') }}</text>
      </view>
    </view>

    <!-- 素材库弹窗 -->
    <view v-if="showGallery" class="gallery-modal" @click="closeGallery">
      <view class="gallery-content" @click.stop>
        <view class="gallery-header">
          <text class="gallery-title">
            {{ t('create.gallery') }}
          </text>
          <text class="gallery-close" @click="closeGallery">
            ✕
          </text>
        </view>
        <view v-if="loadingGallery" class="gallery-loading">
          <wd-loading color="var(--accent)" />
        </view>
        <view v-else-if="!galleryItems.length" class="gallery-empty">
          {{ t('create.galleryEmpty') }}
        </view>
        <scroll-view v-else scroll-y class="gallery-grid">
          <view class="gallery-list">
            <view v-for="item in galleryItems" :key="item.id" class="gallery-item" @click="selectGalleryItem(item)">
              <image class="gallery-thumb" :src="item.thumbUrl" mode="aspectFill" />
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.image-picker {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.selected-preview {
  position: relative;
  width: 100%;
  height: 320rpx;
  border-radius: 20rpx;
  overflow: hidden;
  background: var(--bg);

  .preview-img {
    width: 100%;
    height: 100%;
  }

  .clear-btn {
    position: absolute;
    top: 16rpx;
    right: 16rpx;
    width: 48rpx;
    height: 48rpx;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.picker-actions {
  display: flex;
  gap: 16rpx;

  &.compact {
    .picker-btn {
      flex: 1;
      padding: 16rpx 0;
      background: var(--surface);
      border: 1rpx solid var(--border);
    }
  }
}

.picker-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 32rpx 0;
  background: var(--surface);
  border: 2rpx dashed var(--border);
  border-radius: 20rpx;
  color: var(--text);
  font-size: 26rpx;

  &:active {
    opacity: 0.9;
  }

  &.secondary {
    font-size: 24rpx;
    gap: 8rpx;
  }
}

.gallery-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}

.gallery-content {
  width: 100%;
  max-width: 600rpx;
  max-height: 70vh;
  background: var(--surface);
  border-radius: var(--r);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.gallery-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid var(--border);

  .gallery-title {
    font-size: 30rpx;
    font-weight: 600;
    color: var(--text);
  }

  .gallery-close {
    font-size: 32rpx;
    color: var(--muted);
    padding: 8rpx 16rpx;
  }
}

.gallery-loading,
.gallery-empty {
  padding: 64rpx 0;
  text-align: center;
  color: var(--dim);
  font-size: 28rpx;
}

.gallery-grid {
  flex: 1;
  padding: 16rpx;
}

.gallery-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.gallery-item {
  aspect-ratio: 1;
  border-radius: 12rpx;
  overflow: hidden;
  background: var(--bg);

  &:active {
    opacity: 0.8;
  }
}

.gallery-thumb {
  width: 100%;
  height: 100%;
}
</style>
