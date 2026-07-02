<route lang="jsonc" type="page">
{
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "图片绘画"
  }
}
</route>

<script lang="ts" setup>
import type { V2TaskInfo } from '@/api/v2/types'
import { ref } from 'vue'
import { v2SubmitTask } from '@/api/v2'
import { t } from '@/i18n'
import DrawParamsPanel from './components/draw-params-panel.vue'
import ImagePicker from './components/image-picker.vue'
import { getProgressPercent, getStatusColor, getStatusLabel, saveImageToAlbum, svgToDataUri } from './create-utils'
import { useCreateShared } from './useCreateShared'

defineOptions({ name: 'ImageDrawPage' })

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

const prompt = ref('')
const referenceImageUrl = ref('')
const drawParams = ref<Record<string, unknown>>({})

async function handleImageDraw() {
  if (!selectedDeviceId.value) {
    uni.showToast({ title: t('create.pleaseSelectDevice'), icon: 'none' })
    return
  }
  if (!referenceImageUrl.value) {
    uni.showToast({ title: t('create.pleaseSelectImage'), icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const params: Record<string, unknown> = {
      imageUrl: referenceImageUrl.value,
      ...(prompt.value.trim() ? { prompt: prompt.value.trim() } : {}),
      ...drawParams.value,
    }
    const res = await v2SubmitTask(selectedDeviceId.value, 'draw_generated', params)
    const newTask: V2TaskInfo = {
      taskId: res.taskId || 'unknown',
      status: 'pending',
      deviceId: selectedDeviceId.value,
      capability: 'draw_generated',
      params,
      createdAt: new Date().toISOString(),
    }
    tasks.value.unshift(newTask)
    prompt.value = ''
    referenceImageUrl.value = ''
    uni.showToast({ title: t('create.taskSubmitted'), icon: 'success' })
    startPolling(newTask.taskId)
  }
  catch (e: any) {
    uni.showToast({ title: `${t('create.submitFailed')}: ${e.message || ''}`, icon: 'none' })
  }
  finally {
    submitting.value = false
  }
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
          {{ t('create.imageDrawTab') }}
        </text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <!-- 目标设备 -->
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

    <!-- 参考图（必填） -->
    <view class="section">
      <text class="section-title">
        {{ t('create.selectReferenceImage') }}
      </text>
      <ImagePicker v-model="referenceImageUrl" />
      <view class="hint-text">
        {{ t('create.imageDrawHint') }}
      </view>
    </view>

    <!-- 提示词 -->
    <view class="section">
      <text class="section-title">
        {{ t('create.promptTitle') }}
      </text>
      <view class="prompt-box">
        <textarea v-model="prompt" class="prompt-textarea" :placeholder="t('create.drawPlaceholder')" placeholder-class="prompt-placeholder" :maxlength="500" :disabled="submitting" />
        <text class="prompt-count">
          {{ prompt.length }}/500
        </text>
      </view>
    </view>

    <!-- 参数面板 -->
    <view class="section">
      <DrawParamsPanel @update:params="drawParams = $event" />
    </view>

    <!-- 提交按钮 -->
    <view class="submit-section">
      <view class="submit-btn" :class="{ disabled: !selectedDeviceId || !referenceImageUrl || submitting }" @click="handleImageDraw">
        <text class="submit-text">
          {{ submitting ? t('create.submitting') : t('create.submitImageDraw') }}
        </text>
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
            <view class="task-status" :style="{ color: getStatusColor(task.status) }">
              <view class="status-dot" :style="{ background: getStatusColor(task.status) }" />
              <text>{{ getStatusLabel(task.status) }}</text>
            </view>
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
          <view v-if="!['completed', 'failed', 'error'].includes(task.status)" class="task-progress">
            <view class="progress-bar">
              <view class="progress-fill" :style="{ width: `${getProgressPercent(task.status)}%`, background: getStatusColor(task.status) }" />
            </view>
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