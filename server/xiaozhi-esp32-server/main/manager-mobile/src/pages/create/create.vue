<route lang="jsonc" type="page">
{
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "AI 创作"
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

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

const mode = ref<'draw' | 'write' | 'image'>('draw')
const imageGenerating = ref(false)
const imageResultUrl = ref('')
// 品牌标签（「LiMa 生图」），非真实后端名；后端真实模型对外不可见。
const imageResultBackend = ref('')
const devices = ref<V2DeviceInfo[]>([])
const selectedDeviceId = ref('')
const prompt = ref('')
const submitting = ref(false)
const tasks = ref<V2TaskInfo[]>([])
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

onLoad((options: any) => {
  const allowed: Array<'draw' | 'write' | 'image'> = ['draw', 'write', 'image']
  mode.value = allowed.includes(options?.mode) ? options.mode : 'draw'
})
onShow(() => {
  if (mode.value !== 'image')
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

async function handleSubmit() {
  if (!selectedDeviceId.value) {
    uni.showToast({ title: t('create.pleaseSelectDevice'), icon: 'none' })
    return
  }
  if (!prompt.value.trim()) {
    uni.showToast({ title: t('create.pleaseEnterPrompt'), icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const capability = mode.value === 'draw' ? 'draw_generated' : 'write_text'
    const res = await v2SubmitTask(selectedDeviceId.value, capability, { prompt: prompt.value.trim() })
    const newTask: V2TaskInfo = {
      taskId: res.taskId || 'unknown',
      status: 'pending',
      deviceId: selectedDeviceId.value,
      capability,
      params: { prompt: prompt.value.trim() },
      createdAt: new Date().toISOString(),
    }
    tasks.value.unshift(newTask)
    prompt.value = ''
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

async function handleImageGenerate() {
  if (!prompt.value.trim()) {
    uni.showToast({ title: t('create.pleaseEnterPrompt'), icon: 'none' })
    return
  }
  imageGenerating.value = true
  try {
    const res = await generateImage({ prompt: prompt.value.trim(), size: '1024x1024', n: 1 })
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
  v2SubmitTask(selectedDeviceId.value, 'draw_generated', { imageUrl: imageResultUrl.value, prompt: prompt.value.trim() })
    .then(() => {
      uni.showToast({ title: t('create.taskSubmitted'), icon: 'success' })
      imageResultUrl.value = ''
      imageResultBackend.value = ''
      prompt.value = ''
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
      failCount = 0 // 成功后重置失败计数
      const idx = tasks.value.findIndex(t => t.taskId === taskId)
      if (idx < 0) {
        // 任务已被删除，停止轮询避免泄漏
        if (pollTimer.value)
          clearInterval(pollTimer.value)
        pollTimer.value = null
        return
      }
      tasks.value[idx] = { ...tasks.value[idx], ...task }
      if (['completed', 'failed', 'error'].includes(task.status)) {
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
    pending: '#f59e0b',
    queued: '#f59e0b',
    running: '#00ffaa',
    completed: '#34d399',
    failed: '#ff4d6d',
    error: '#ff4d6d',
  }
  return map[status] || '#9d9ea3'
}
function getProgressPercent(status: string) {
  const map: Record<string, number> = {
    pending: 10,
    queued: 30,
    running: 60,
    completed: 100,
    failed: 100,
    error: 100,
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

// 图片加载错误
const imageErrorMap = ref<Record<string, boolean>>({})
function onImageError(taskId: string) {
  imageErrorMap.value[taskId] = true
}

// 保存图片到相册
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

// 删除单个任务
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

// 清空全部任务
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

    <view class="mode-tabs">
      <view class="mode-tab" :class="{ active: mode === 'draw' }" @click="mode = 'draw'">
        <wd-icon name="photo" size="28" :color="mode === 'draw' ? '#00ffaa' : 'var(--dim)'" />
        <text class="tab-label">
          {{ t('create.drawTab') }}
        </text>
      </view>
      <view class="mode-tab" :class="{ active: mode === 'write' }" @click="mode = 'write'">
        <wd-icon name="edit-2" size="28" :color="mode === 'write' ? '#00ffaa' : 'var(--dim)'" />
        <text class="tab-label">
          {{ t('create.writeTab') }}
        </text>
      </view>
      <view class="mode-tab" :class="{ active: mode === 'image' }" @click="mode = 'image'">
        <wd-icon name="cloud" size="28" :color="mode === 'image' ? '#00ffaa' : 'var(--dim)'" />
        <text class="tab-label">
          {{ t('create.imageTab') }}
        </text>
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
            <wd-icon name="phone" size="20" :color="selectedDeviceId === d.deviceId ? '#00ffaa' : 'var(--muted)'" />
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

    <view class="section">
      <text class="section-title">
        {{ t('create.promptTitle') }}
      </text>
      <view class="prompt-box">
        <textarea v-model="prompt" class="prompt-textarea" :placeholder="mode === 'image' ? t('create.imagePlaceholder') : (mode === 'draw' ? t('create.drawPlaceholder') : t('create.writePlaceholder'))" placeholder-class="prompt-placeholder" :maxlength="500" :disabled="submitting || imageGenerating" />
        <text class="prompt-count">
          {{ prompt.length }}/500
        </text>
      </view>
    </view>

    <view class="submit-section">
      <view v-if="mode === 'image'" class="submit-btn" :class="{ disabled: !prompt.trim() || imageGenerating }" @click="handleImageGenerate">
        <text class="submit-text">
          {{ imageGenerating ? t('create.imageGenerating') : t('create.submitImage') }}
        </text>
      </view>
      <view v-else class="submit-btn" :class="{ disabled: !selectedDeviceId || !prompt.trim() || submitting }" @click="handleSubmit">
        <text class="submit-text">
          {{ submitting ? t('create.submitting') : (mode === 'draw' ? t('create.submitDraw') : t('create.submitWrite')) }}
        </text>
      </view>
    </view>

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
        <view class="action-btn secondary" @click="saveImageToAlbum(imageResultUrl)">
          <wd-icon name="download" size="16" color="#00ffaa" />
          <text>{{ t('create.saveToAlbum') }}</text>
        </view>
        <view class="action-btn primary" :class="{ disabled: !selectedDeviceId || submitting }" @click="sendImageToDevice">
          <wd-icon name="phone" size="16" color="#fff" />
          <text>{{ t('create.sendToDevice') }}</text>
        </view>
      </view>
    </view>

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
              {{ task.capability === 'draw_generated' ? t('create.drawTab') : t('create.writeTab') }}
            </text>
            <view class="task-status" :style="{ color: getStatusColor(task.status) }">
              <view class="status-dot" :style="{ background: getStatusColor(task.status) }" />
              <text>{{ getStatusLabel(task.status) }}</text>
            </view>
          </view>
          <text class="task-prompt">
            {{ task.params?.prompt || '' }}
          </text>
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
    background: rgba(0, 255, 170, 0.1);
    border: 2rpx solid var(--accent);
    box-shadow: none;
  }
  .tab-label {
    font-size: 26rpx;
    font-weight: 600;
    color: var(--text);
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
    background: rgba(0, 255, 170, 0.1);
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
      color: #34d399;
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
.submit-section {
  padding: 0 24rpx;
  margin-top: 32rpx;
}
.submit-btn {
  width: 100%;
  padding: 28rpx 0;
  background: var(--accent);
  border-radius: 20rpx;
  box-shadow: 0 0 20rpx rgba(0, 255, 170, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    opacity: 0.9;
    box-shadow: 0 0 12rpx rgba(0, 255, 170, 0.15);
  }
  &.disabled {
    opacity: 0.4;
    box-shadow: none;
  }
  .submit-text {
    font-size: 32rpx;
    font-weight: 700;
    color: #050810;
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
  color: #ff4d6d;
}

.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.clear-btn {
  font-size: 26rpx;
  color: #ff4d6d;
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
  background: rgba(0, 255, 170, 0.1);
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
  background: rgba(0, 255, 170, 0.1);
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
    background: rgba(0, 255, 170, 0.1);
    color: var(--accent);
  }
}
</style>
