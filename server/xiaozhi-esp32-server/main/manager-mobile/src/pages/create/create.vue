<route lang="jsonc" type="page">
{
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "AI 绘画"
  }
}
</route>

<script lang="ts" setup>
import type { V2DeviceInfo, V2TaskInfo } from '@/api/v2/types'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { onUnmounted, ref } from 'vue'
import { generateImage } from '@/api/images'
import { v2GetDevices, v2GetTask, v2SubmitTask } from '@/api/v2'
import { t } from '@/i18n'
import DrawParamsPanel from './components/draw-params-panel.vue'
import ImagePicker from './components/image-picker.vue'

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

// 顶部模式：AI 绘画 / 图片绘画
const mode = ref<'ai-draw' | 'image-draw'>('ai-draw')
// AI 绘画子模式：文生图 / 图生图
const aiSubMode = ref<'text' | 'image'>('text')

const imageGenerating = ref(false)
const imageResultUrl = ref('')
const imageResultBackend = ref('')
const referenceImageUrl = ref('')

const devices = ref<V2DeviceInfo[]>([])
const selectedDeviceId = ref('')
const prompt = ref('')
const submitting = ref(false)
const tasks = ref<V2TaskInfo[]>([])
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
const drawParams = ref<Record<string, unknown>>({})

onLoad((options: any) => {
  // 保留旧入口 mode=draw 的兼容，默认都是 AI 绘画
  mode.value = options?.mode === 'image' ? 'image-draw' : 'ai-draw'
})
onShow(() => {
  loadDevices()
})
onUnmounted(() => {
  if (pollTimer.value)
    clearInterval(pollTimer.value)
})

async function loadDevices() {
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
    const online = devices.value.find(d => d.status === 'online')
    selectedDeviceId.value = online?.deviceId || devices.value[0]?.deviceId || ''
  }
  catch (e) {
    console.error('loadDevices failed', e)
    uni.showToast({ title: t('create.loadDevicesFailed'), icon: 'none' })
  }
}

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

function sendImageToDevice() {
  if (!imageResultUrl.value || !selectedDeviceId.value) {
    uni.showToast({ title: t('create.imageSelectDeviceFirst'), icon: 'none' })
    return
  }
  submitting.value = true
  const params = {
    imageUrl: imageResultUrl.value,
    ...(prompt.value.trim() ? { prompt: prompt.value.trim() } : {}),
    ...drawParams.value,
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

function startPolling(taskId: string) {
  if (pollTimer.value)
    clearInterval(pollTimer.value)
  let failCount = 0
  const MAX_POLL_FAILS = 5
  pollTimer.value = setInterval(async () => {
    try {
      const task = await v2GetTask(taskId)
      failCount = 0
      const idx = tasks.value.findIndex(t => t.taskId === taskId)
      if (idx < 0) {
        if (pollTimer.value)
          clearInterval(pollTimer.value)
        pollTimer.value = null
        return
      }
      tasks.value[idx] = { ...tasks.value[idx], ...task }
      if (['done', 'failed', 'cancelled', 'dead_letter', 'completed', 'error'].includes(task.status)) {
        if (pollTimer.value)
          clearInterval(pollTimer.value)
        pollTimer.value = null
      }
    }
    catch (e) {
      failCount++
      console.error('poll failed', e)
      if (failCount >= MAX_POLL_FAILS) {
        if (pollTimer.value)
          clearInterval(pollTimer.value)
        pollTimer.value = null
        uni.showToast({ title: t('create.pollFailed'), icon: 'none' })
      }
    }
  }, 3000)
}

function getStatusLabel(status: string) {
  return t(`create.status.${status}` as any) || status
}
function getStatusColor(status: string) {
  const map: Record<string, string> = {
    pending: '#fbbf24',
    queued: '#fbbf24',
    created: '#fbbf24',
    dispatching: '#fbbf24',
    dispatched: '#fbbf24',
    accepted: '#2dd4a7',
    running: '#2dd4a7',
    progress: '#2dd4a7',
    done: '#4ade80',
    completed: '#4ade80',
    failed: '#f87171',
    error: '#f87171',
    cancelled: '#8b95a3',
    dead_letter: '#f87171',
  }
  return map[status] || '#8b95a3'
}
function getProgressPercent(status: string) {
  const map: Record<string, number> = {
    pending: 10,
    queued: 20,
    created: 10,
    dispatching: 25,
    dispatched: 30,
    accepted: 35,
    running: 60,
    progress: 70,
    done: 100,
    completed: 100,
    failed: 100,
    error: 100,
    cancelled: 100,
    dead_letter: 100,
  }
  return map[status] ?? 10
}
function previewImage(url: string) {
  uni.previewImage({ urls: [url], current: url })
}
function formatTime(iso?: string) {
  if (!iso)
    return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
function navigateBack() {
  uni.navigateBack()
}

const imageErrorMap = ref<Record<string, boolean>>({})
function onImageError(taskId: string) {
  imageErrorMap.value[taskId] = true
}

function saveImageToAlbum(url: string) {
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200) {
        uni.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: () => uni.showToast({ title: t('create.savedToAlbum'), icon: 'success' }),
          fail: () => uni.showToast({ title: t('create.saveFailed'), icon: 'none' }),
        })
      }
      else {
        uni.showToast({ title: t('create.downloadFailed'), icon: 'none' })
      }
    },
    fail: () => uni.showToast({ title: t('create.downloadFailed'), icon: 'none' }),
  })
}

function svgToDataUri(svg: string): string {
  if (!svg)
    return ''
  try {
    // #ifdef MP-WEIXIN
    return `data:image/svg+xml;base64,${uni.arrayBufferToBase64(stringToArrayBuffer(svg))}`
    // #endif
    // #ifndef MP-WEIXIN
    return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
    // #endif
  }
  catch {
    return ''
  }
}

function stringToArrayBuffer(str: string): ArrayBuffer {
  const bytes = new Uint8Array(str.length)
  for (let i = 0; i < str.length; i++)
    bytes[i] = str.charCodeAt(i)
  return bytes.buffer
}

const previewSvgContent = ref('')
const showPreview = ref(false)
function openPreview(svg: string) {
  previewSvgContent.value = svg
  showPreview.value = true
}

function deleteTask(taskId: string) {
  uni.showModal({
    title: t('create.deleteConfirmTitle'),
    content: t('create.deleteConfirmContent'),
    success: (res) => {
      if (res.confirm) {
        tasks.value = tasks.value.filter(t => t.taskId !== taskId)
        uni.showToast({ title: t('create.deleted'), icon: 'success' })
      }
    },
  })
}

function clearAllTasks() {
  if (!tasks.value.length)
    return
  uni.showModal({
    title: t('create.clearConfirmTitle'),
    content: t('create.clearConfirmContent'),
    success: (res) => {
      if (res.confirm) {
        tasks.value = []
        uni.showToast({ title: t('create.cleared'), icon: 'success' })
      }
    },
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
          {{ t('create.title') }}
        </text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <!-- 顶部模式切换 -->
    <view class="mode-tabs">
      <view class="mode-tab" :class="{ active: mode === 'ai-draw' }" @click="mode = 'ai-draw'">
        <wd-icon name="photo" size="28" :color="mode === 'ai-draw' ? 'var(--accent)' : 'var(--dim)'" />
        <text class="tab-label">
          {{ t('create.aiDrawTab') }}
        </text>
      </view>
      <view class="mode-tab" :class="{ active: mode === 'image-draw' }" @click="mode = 'image-draw'">
        <wd-icon name="picture" size="28" :color="mode === 'image-draw' ? 'var(--accent)' : 'var(--dim)'" />
        <text class="tab-label">
          {{ t('create.imageDrawTab') }}
        </text>
      </view>
    </view>

    <!-- AI 绘画子模式 -->
    <view v-if="mode === 'ai-draw'" class="sub-mode-tabs">
      <view class="sub-mode-tab" :class="{ active: aiSubMode === 'text' }" @click="aiSubMode = 'text'">
        {{ t('create.subModeText') }}
      </view>
      <view class="sub-mode-tab" :class="{ active: aiSubMode === 'image' }" @click="aiSubMode = 'image'">
        {{ t('create.subModeImage') }}
      </view>
    </view>

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

    <!-- 参考图（图生图 / 图片绘画） -->
    <view v-if="mode === 'image-draw' || aiSubMode === 'image'" class="section">
      <text class="section-title">
        {{ mode === 'image-draw' ? t('create.selectReferenceImage') : t('create.referenceImage') }}
      </text>
      <ImagePicker v-model="referenceImageUrl" />
      <view v-if="mode === 'image-draw'" class="hint-text">
        {{ t('create.imageDrawHint') }}
      </view>
      <view v-else-if="aiSubMode === 'image' && !referenceImageUrl" class="hint-text">
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

    <!-- 参数面板 -->
    <view class="section">
      <DrawParamsPanel @update:params="drawParams = $event" />
    </view>

    <!-- 提交按钮 -->
    <view class="submit-section">
      <view v-if="mode === 'ai-draw'" class="submit-btn" :class="{ disabled: imageGenerating || (aiSubMode === 'text' ? !prompt.trim() : !referenceImageUrl) }" @click="handleAiDraw">
        <text class="submit-text">
          {{ imageGenerating ? t('create.imageGenerating') : t('create.submitAiDraw') }}
        </text>
      </view>
      <view v-else class="submit-btn" :class="{ disabled: !selectedDeviceId || !referenceImageUrl || submitting }" @click="handleImageDraw">
        <text class="submit-text">
          {{ submitting ? t('create.submitting') : t('create.submitImageDraw') }}
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

    <!-- SVG 路径预览弹窗 -->
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
.create-page {
  min-height: 100vh;
  background: var(--bg);
}
.create-nav {
  background: var(--surface);
  border-bottom: 1rpx solid var(--border);
  .nav-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 88rpx;
    padding: 0 24rpx;
  }
  .nav-back {
    width: 80rpx;
    display: flex;
    align-items: center;
  }
  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: var(--text);
  }
  .nav-placeholder {
    width: 80rpx;
  }
}
.mode-tabs {
  display: flex;
  gap: 20rpx;
  padding: 24rpx;
}
.mode-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 24rpx 0;
  background: var(--surface);
  border: 2rpx solid var(--border);
  border-radius: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
  &:active {
    transform: scale(0.97);
  }
  &.active {
    background: rgba(45, 212, 167, 0.1);
    border: 2rpx solid var(--accent);
    box-shadow: none;
  }
  .tab-label {
    font-size: 26rpx;
    font-weight: 600;
    color: var(--text);
  }
}
.sub-mode-tabs {
  display: flex;
  gap: 16rpx;
  padding: 0 24rpx 24rpx;
}
.sub-mode-tab {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: 12rpx;
  font-size: 26rpx;
  color: var(--muted);
  &.active {
    background: rgba(45, 212, 167, 0.1);
    border-color: var(--accent);
    color: var(--accent);
    font-weight: 600;
  }
}
.section {
  padding: 0 24rpx;
  margin-bottom: 24rpx;
}
.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 16rpx;
}
.empty-tip {
  padding: 32rpx 0;
  text-align: center;
  color: var(--dim);
  font-size: 28rpx;
}
.device-scroll {
  white-space: nowrap;
}
.device-list {
  display: inline-flex;
  gap: 16rpx;
  padding-bottom: 8rpx;
}
.device-chip {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  background: var(--surface);
  border: 2rpx solid var(--border);
  border-radius: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
  &:active {
    transform: scale(0.97);
  }
  &.selected {
    background: rgba(45, 212, 167, 0.1);
    border: 2rpx solid var(--accent);
    box-shadow: none;
  }
  .chip-info {
    display: flex;
    flex-direction: column;
    gap: 2rpx;
  }
  .chip-name {
    font-size: 26rpx;
    font-weight: 600;
    color: var(--text);
  }
  .chip-status {
    font-size: 22rpx;
    &.online {
      color: var(--green);
    }
    &.offline {
      color: var(--dim);
    }
  }
}
.prompt-box {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: 20rpx;
  padding: 20rpx;
  position: relative;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
}
.prompt-textarea {
  width: 100%;
  height: 200rpx;
  font-size: 28rpx;
  color: var(--text);
  line-height: 1.6;
}
.prompt-placeholder {
  color: var(--dim);
}
.prompt-count {
  position: absolute;
  bottom: 12rpx;
  right: 20rpx;
  font-size: 22rpx;
  color: var(--dim);
}
.hint-text {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--dim);
}
.submit-section {
  padding: 0 24rpx;
  margin-top: 32rpx;
}
.submit-btn {
  width: 100%;
  padding: 28rpx 0;
  background: var(--accent);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    opacity: 0.9;
  }
  &.disabled {
    opacity: 0.4;
  }
  .submit-text {
    font-size: 32rpx;
    font-weight: 600;
    color: #0b0e13;
  }
}
.tasks-section {
  margin-top: 32rpx;
}
.task-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.task-card {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: 20rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-cap {
  font-size: 26rpx;
  color: var(--text);
  font-weight: 600;
}
.task-status {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 24rpx;
  font-weight: 600;
}
.status-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
}
.task-prompt {
  font-size: 26rpx;
  color: var(--muted);
  line-height: 1.5;
  word-break: break-word;
}
.progress-bar {
  height: 8rpx;
  background: var(--border);
  border-radius: 4rpx;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 4rpx;
  transition: width 0.5s ease;
}
.task-result {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.image-wrapper {
  position: relative;
  width: 100%;
}
.result-image {
  width: 100%;
  height: 320rpx;
  border-radius: 16rpx;
  background: var(--bg);
}
.result-tip {
  font-size: 22rpx;
  color: var(--dim);
  text-align: center;
}
.task-error {
  font-size: 24rpx;
  color: var(--danger);
}

.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.clear-btn {
  font-size: 26rpx;
  color: var(--danger);
  padding: 16rpx 24rpx;
}

.result-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.save-btn {
  font-size: 24rpx;
  color: var(--accent);
  padding: 16rpx 24rpx;
  background: rgba(45, 212, 167, 0.1);
  border-radius: 8rpx;
}

.image-error-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  background: var(--bg);
  border-radius: 16rpx;
}
.image-error-icon {
  font-size: 48rpx;
}
.image-error-text {
  font-size: 26rpx;
  color: var(--dim);
}

.task-actions-bar {
  display: flex;
  justify-content: flex-end;
  border-top: 1rpx solid var(--border);
  padding-top: 16rpx;
  margin-top: 4rpx;
}
.task-delete-btn {
  font-size: 24rpx;
  color: var(--dim);
  padding: 16rpx 24rpx;
}
.image-result-section {
  margin-top: 32rpx;
}
.backend-tag {
  font-size: 22rpx;
  color: var(--accent);
  background: rgba(45, 212, 167, 0.1);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.action-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 24rpx;
  border-radius: 12rpx;
  font-size: 26rpx;
  font-weight: 600;
  &:active {
    opacity: 0.9;
  }
  &.disabled {
    opacity: 0.4;
  }
  &.primary {
    background: var(--accent);
    color: #fff;
  }
  &.secondary {
    background: rgba(45, 212, 167, 0.1);
    color: var(--accent);
  }
}

/* 任务路径预览缩略图 */
.task-preview {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
  padding: 12rpx;
  background: var(--bg);
  border-radius: 12rpx;
}
.preview-thumb {
  width: 120rpx;
  height: 80rpx;
  border-radius: 8rpx;
  background: #fafafa;
}
.preview-hint {
  font-size: 22rpx;
  color: var(--muted);
}

/* SVG 全屏预览弹窗 */
.preview-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}
.preview-modal-content {
  width: 100%;
  max-width: 600rpx;
  background: var(--surface);
  border-radius: var(--r);
  overflow: hidden;
}
.preview-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid var(--border);
}
.preview-modal-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text);
}
.preview-modal-close {
  font-size: 32rpx;
  color: var(--muted);
  padding: 8rpx 16rpx;
}
.preview-modal-img {
  width: 100%;
  height: 500rpx;
  background: #fafafa;
}
</style>
