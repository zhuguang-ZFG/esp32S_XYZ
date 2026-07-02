<route lang="jsonc" type="page">
{
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "AI 生图"
  }
}
</route>

<script lang="ts" setup>
import type { V2TaskInfo } from '@/api/v2/types'
import { ref } from 'vue'
import { generateImage } from '@/api/images'
import { v2SubmitTask } from '@/api/v2'
import { t } from '@/i18n'
import ImagePicker from './components/image-picker.vue'
import { saveImageToAlbum, svgToDataUri } from './create-utils'
import { useCreateShared } from './useCreateShared'

defineOptions({ name: 'AiDrawPage' })

const {
  safeAreaTop,
  devices,
  selectedDeviceId,
  submitting,
  tasks,
  imageErrorMap,
  previewSvgContent,
  showPreview,
  onImageError,
  openPreview,
  previewImage,
  navigateBack,
  startPolling,
  deleteTask,
  clearAllTasks,
} = useCreateShared()

// 文生图 / 图生图
const aiSubMode = ref<'text' | 'image'>('text')
const prompt = ref('')
const imageGenerating = ref(false)
const imageResultUrl = ref('')
const imageResultBackend = ref('')
const referenceImageUrl = ref('')

async function handleAiDraw() {
  if (!prompt.value.trim() && aiSubMode.value === 'text') {
    uni.showToast({ title: t('create.pleaseEnterPrompt'), icon: 'none' })
    return
  }
  if (aiSubMode.value === 'image' && !referenceImageUrl.value) {
    uni.showToast({ title: t('create.pleaseSelectReferenceImage'), icon: 'none' })
    return
  }
  imageGenerating.value = true
  try {
    const res = await generateImage({
      prompt: prompt.value.trim(),
      size: '1024x1024',
      n: 1,
      ...(aiSubMode.value === 'image' ? { image_url: referenceImageUrl.value } : {}),
    })
    imageResultUrl.value = res.data?.[0]?.url || ''
    imageResultBackend.value = res.backend || ''
    if (!imageResultUrl.value) {
      uni.showToast({ title: t('create.imageNoResult'), icon: 'none' })
    }
  }
  catch (e: any) {
    uni.showToast({ title: `${t('create.imageFailed')}: ${e.message || ''}`, icon: 'none' })
  }
  finally {
    imageGenerating.value = false
  }
}

function sendImageToDevice() {
  if (!imageResultUrl.value || !selectedDeviceId.value) {
    uni.showToast({ title: t('create.imageSelectDeviceFirst'), icon: 'none' })
    return
  }
  submitting.value = true
  const params = {
    imageUrl: imageResultUrl.value,
    ...(prompt.value.trim() ? { prompt: prompt.value.trim() } : {}),
  }
  v2SubmitTask(selectedDeviceId.value, 'draw_generated', params)
    .then((res) => {
      const newTask: V2TaskInfo = {
        taskId: res.taskId || 'unknown',
        status: 'pending',
        deviceId: selectedDeviceId.value,
        capability: 'draw_generated',
        params,
        createdAt: new Date().toISOString(),
      }
      tasks.value.unshift(newTask)
      imageResultUrl.value = ''
      imageResultBackend.value = ''
      prompt.value = ''
      referenceImageUrl.value = ''
      uni.showToast({ title: t('create.taskSubmitted'), icon: 'success' })
      startPolling(newTask.taskId)
    })
    .catch((e: any) => {
      uni.showToast({ title: `${t('create.submitFailed')}: ${e.message || ''}`, icon: 'none' })
    })
    .finally(() => {
      submitting.value = false
    })
}
</script>

<template>
  <view class="create-page page-enter" :style="{ paddingTop: `${safeAreaTop}px` }">
    <view class="create-nav">
      <view class="nav-content">
        <view class="nav-back" @click="navigateBack">
          <wd-icon name="arrow-left" size="20" color="var(--text)" />
        </view>
        <text class="nav-title">
          {{ t('create.aiDrawTab') }}
        </text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <!-- 文生图 / 图生图 子模式 -->
    <view class="sub-mode-tabs">
      <view class="sub-mode-tab" :class="{ active: aiSubMode === 'text' }" @click="aiSubMode = 'text'">
        {{ t('create.subModeText') }}
      </view>
      <view class="sub-mode-tab" :class="{ active: aiSubMode === 'image' }" @click="aiSubMode = 'image'">
        {{ t('create.subModeImage') }}
      </view>
    </view>

    <!-- 参考图（仅图生图） -->
    <view v-if="aiSubMode === 'image'" class="section">
      <text class="section-title">
        {{ t('create.referenceImage') }}
      </text>
      <ImagePicker v-model="referenceImageUrl" />
      <view v-if="!referenceImageUrl" class="hint-text">
        {{ t('create.noReferenceImageHint') }}
      </view>
    </view>

    <!-- 提示词 -->
    <view class="section">
      <text class="section-title">
        {{ t('create.promptTitle') }}
      </text>
      <view class="prompt-box">
        <textarea v-model="prompt" class="prompt-textarea" :placeholder="t('create.drawPlaceholder')" placeholder-class="prompt-placeholder" :maxlength="500" :disabled="submitting || imageGenerating" />
        <text class="prompt-count">
          {{ prompt.length }}/500
        </text>
      </view>
    </view>

    <!-- 目标设备（发送到设备用） -->
    <view class="section">
      <text class="section-title">
        {{ t('create.selectDevice') }}
      </text>
      <view v-if="!devices.length" class="empty-tip">
        {{ t('create.noDevices') }}
      </view>
      <scroll-view v-else scroll-x class="device-scroll">
        <view class="device-list">
          <view v-for="d in devices" :key="d.deviceId" class="device-chip" :class="{ selected: selectedDeviceId === d.deviceId }" @click="selectedDeviceId = d.deviceId">
            <wd-icon name="phone" size="20" :color="selectedDeviceId === d.deviceId ? 'var(--accent)' : 'var(--muted)'" />
            <view class="chip-info">
              <text class="chip-name">
                {{ d.model || 'Device' }}
              </text>
              <text class="chip-status" :class="d.status">
                {{ d.status === 'online' ? `● ${t('create.device.online')}` : `● ${t('create.device.offline')}` }}
              </text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 提交按钮 -->
    <view class="submit-section">
      <view class="submit-btn" :class="{ disabled: imageGenerating || (aiSubMode === 'text' ? !prompt.trim() : !referenceImageUrl) }" @click="handleAiDraw">
        <text class="submit-text">
          {{ imageGenerating ? t('create.imageGenerating') : t('create.submitAiDraw') }}
        </text>
      </view>
    </view>

    <!-- 生成结果 -->
    <view v-if="imageResultUrl" class="section image-result-section">
      <view class="section-header-row">
        <text class="section-title">
          {{ t('create.imageResult') }}
        </text>
        <text v-if="imageResultBackend" class="backend-tag">
          {{ imageResultBackend }}
        </text>
      </view>
      <view class="image-wrapper">
        <image
          class="result-image"
          :src="imageResultUrl"
          mode="aspectFill"
          @click="previewImage(imageResultUrl)"
        />
      </view>
      <view class="result-actions">
        <view class="secondary action-btn" @click="saveImageToAlbum(imageResultUrl)">
          <wd-icon name="download" size="16" color="var(--accent)" />
          <text>{{ t('create.saveToAlbum') }}</text>
        </view>
        <view class="action-btn primary" :class="{ disabled: !selectedDeviceId || submitting }" @click="sendImageToDevice">
          <wd-icon name="phone" size="16" color="#fff" />
          <text>{{ t('create.sendToDevice') }}</text>
        </view>
      </view>
    </view>

    <!-- 任务追踪 -->
    <view v-if="tasks.length" class="section tasks-section">
      <view class="section-header-row">
        <text class="section-title">
          {{ t('create.taskTracking') }}
        </text>
        <text class="clear-btn" @click="clearAllTasks">
          {{ t('create.clearAll') }}
        </text>
      </view>
      <view class="task-list">
        <view v-for="task in tasks" :key="task.taskId" class="task-card">
          <view class="task-header">
            <text class="task-cap">
              {{ task.capability === 'draw_generated' ? t('create.aiDrawTab') : task.capability }}
            </text>
          </view>
          <text class="task-prompt">
            {{ task.params?.text || task.params?.prompt || '' }}
          </text>
          <view v-if="task.params?.preview_svg" class="task-preview" @click="openPreview(task.params.preview_svg)">
            <image
              class="preview-thumb"
              :src="svgToDataUri(task.params.preview_svg)"
              mode="aspectFit"
            />
            <text class="preview-hint">
              {{ t('create.previewHint') }}
            </text>
          </view>
          <view v-if="task.imageUrl" class="task-result">
            <view class="image-wrapper">
              <image
                class="result-image"
                :src="task.imageUrl"
                mode="aspectFill"
                @click="previewImage(task.imageUrl!)"
                @error="onImageError(task.taskId)"
              />
              <view v-if="imageErrorMap[task.taskId]" class="image-error-overlay">
                <text class="image-error-icon">
                  🖼️
                </text>
                <text class="image-error-text">
                  {{ t('create.imageLoadFailed') }}
                </text>
              </view>
            </view>
            <view class="result-actions">
              <text class="result-tip">
                {{ t('create.clickToPreview') }}
              </text>
              <text class="save-btn" @click="saveImageToAlbum(task.imageUrl!)">
                {{ t('create.saveToAlbum') }}
              </text>
            </view>
          </view>
          <text v-if="task.error" class="task-error">
            {{ task.error }}
          </text>
          <view class="task-actions-bar">
            <text class="task-delete-btn" @click="deleteTask(task.taskId)">
              {{ t('create.deleteTask') }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <view style="height: 40rpx;" />

    <!-- SVG 预览弹窗 -->
    <view v-if="showPreview" class="preview-modal" @click="showPreview = false">
      <view class="preview-modal-content" @click.stop>
        <view class="preview-modal-header">
          <text class="preview-modal-title">
            {{ t('create.previewTitle') }}
          </text>
          <text class="preview-modal-close" @click="showPreview = false">
            ✕
          </text>
        </view>
        <image
          v-if="previewSvgContent"
          class="preview-modal-img"
          :src="svgToDataUri(previewSvgContent)"
          mode="aspectFit"
        />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import './create-shared.scss';
</style>