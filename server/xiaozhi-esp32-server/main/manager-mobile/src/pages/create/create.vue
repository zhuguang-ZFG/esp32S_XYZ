<route lang="jsonc" type="page">
{
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "AI 创作"
  }
}
</route>

<script lang="ts" setup>
import { onLoad, onShow } from '@dcloudio/uni-app'
import { ref, onUnmounted } from 'vue'
import { t } from '@/i18n'
import { v2GetDevices, v2SubmitTask, v2GetTask } from '@/api/v2'
import type { V2DeviceInfo, V2TaskInfo } from '@/api/v2/types'

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

const mode = ref<'draw' | 'write'>('draw')
const devices = ref<V2DeviceInfo[]>([])
const selectedDeviceId = ref('')
const prompt = ref('')
const submitting = ref(false)
const tasks = ref<V2TaskInfo[]>([])
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

onLoad((options: any) => { mode.value = options?.mode === 'write' ? 'write' : 'draw' })
onShow(() => { loadDevices() })
onUnmounted(() => { if (pollTimer.value) clearInterval(pollTimer.value) })

async function loadDevices() {
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
    const online = devices.value.find(d => d.status === 'online')
    selectedDeviceId.value = online?.deviceId || devices.value[0]?.deviceId || ''
  }
  catch (e) { console.error(e) }
}

async function handleSubmit() {
  if (!selectedDeviceId.value) { uni.showToast({ title: t('create.pleaseSelectDevice'), icon: 'none' }); return }
  if (!prompt.value.trim()) { uni.showToast({ title: t('create.pleaseEnterPrompt'), icon: 'none' }); return }
  submitting.value = true
  try {
    const capability = mode.value === 'draw' ? 'draw_generated' : 'write_text'
    const res = await v2SubmitTask(selectedDeviceId.value, capability, { prompt: prompt.value.trim() })
    const newTask: V2TaskInfo = {
      taskId: res.taskId || 'unknown', status: 'pending', deviceId: selectedDeviceId.value,
      capability, params: { prompt: prompt.value.trim() }, createdAt: new Date().toISOString(),
    }
    tasks.value.unshift(newTask)
    prompt.value = ''
    uni.showToast({ title: t('create.taskSubmitted'), icon: 'success' })
    startPolling(newTask.taskId)
  }
  catch (e: any) { uni.showToast({ title: `${t('create.submitFailed')}: ${e.message || ''}`, icon: 'none' }) }
  finally { submitting.value = false }
}

function startPolling(taskId: string) {
  if (pollTimer.value) clearInterval(pollTimer.value)
  pollTimer.value = setInterval(async () => {
    try {
      const task = await v2GetTask(taskId)
      const idx = tasks.value.findIndex(t => t.taskId === taskId)
      if (idx >= 0) {
        tasks.value[idx] = { ...tasks.value[idx], ...task }
        if (['completed', 'failed', 'error'].includes(task.status)) {
          if (pollTimer.value) clearInterval(pollTimer.value)
          pollTimer.value = null
        }
      }
    }
    catch (e) { console.error('poll failed', e) }
  }, 3000)
}

function getStatusLabel(status: string) { return t(`create.status.${status}` as any) || status }
function getStatusColor(status: string) {
  const map: Record<string, string> = { pending: '#f59e0b', queued: '#f59e0b', running: '#336cff', completed: '#07c160', failed: '#ff4d4f', error: '#ff4d4f' }
  return map[status] || '#9d9ea3'
}
function getProgressPercent(status: string) {
  const map: Record<string, number> = { pending: 10, queued: 30, running: 60, completed: 100, failed: 100, error: 100 }
  return map[status] ?? 10
}
function previewImage(url: string) { uni.previewImage({ urls: [url], current: url }) }
function formatTime(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
function navigateBack() { uni.navigateBack() }
</script>

<template>
  <view class="create-page" :style="{ paddingTop: safeAreaTop + 'px' }">
    <view class="create-nav">
      <view class="nav-content">
        <view class="nav-back" @click="navigateBack">
          <wd-icon name="arrow-left" size="20" color="#1d1d1f" />
        </view>
        <text class="nav-title">{{ t('create.title') }}</text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <view class="mode-tabs">
      <view class="mode-tab" :class="{ active: mode === 'draw' }" @click="mode = 'draw'">
        <wd-icon name="photo" size="28" :color="mode === 'draw' ? '#336cff' : '#9d9ea3'" />
        <text class="tab-label">{{ t('create.drawTab') }}</text>
      </view>
      <view class="mode-tab" :class="{ active: mode === 'write' }" @click="mode = 'write'">
        <wd-icon name="edit-2" size="28" :color="mode === 'write' ? '#336cff' : '#9d9ea3'" />
        <text class="tab-label">{{ t('create.writeTab') }}</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">{{ t('create.selectDevice') }}</text>
      <view v-if="!devices.length" class="empty-tip">{{ t('create.noDevices') }}</view>
      <scroll-view v-else scroll-x class="device-scroll">
        <view class="device-list">
          <view v-for="d in devices" :key="d.deviceId" class="device-chip" :class="{ selected: selectedDeviceId === d.deviceId }" @click="selectedDeviceId = d.deviceId">
            <wd-icon name="phone" size="20" :color="selectedDeviceId === d.deviceId ? '#336cff' : '#666'" />
            <view class="chip-info">
              <text class="chip-name">{{ d.model || 'Device' }}</text>
              <text class="chip-status" :class="d.status">{{ d.status === 'online' ? `● ${t('create.device.online')}` : `● ${t('create.device.offline')}` }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="section">
      <text class="section-title">{{ t('create.promptTitle') }}</text>
      <view class="prompt-box">
        <textarea v-model="prompt" class="prompt-textarea" :placeholder="mode === 'draw' ? t('create.drawPlaceholder') : t('create.writePlaceholder')" placeholder-class="prompt-placeholder" maxlength="500" :disabled="submitting" />
        <text class="prompt-count">{{ prompt.length }}/500</text>
      </view>
    </view>

    <view class="submit-section">
      <view class="submit-btn" :class="{ disabled: !selectedDeviceId || !prompt.trim() || submitting }" @click="handleSubmit">
        <text class="submit-text">{{ submitting ? t('create.submitting') : (mode === 'draw' ? t('create.submitDraw') : t('create.submitWrite')) }}</text>
      </view>
    </view>

    <view v-if="tasks.length" class="section tasks-section">
      <text class="section-title">{{ t('create.taskTracking') }}</text>
      <view class="task-list">
        <view v-for="task in tasks" :key="task.taskId" class="task-card">
          <view class="task-header">
            <text class="task-cap">{{ task.capability === 'draw_generated' ? t('create.drawTab') : t('create.writeTab') }}</text>
            <view class="task-status" :style="{ color: getStatusColor(task.status) }">
              <view class="status-dot" :style="{ background: getStatusColor(task.status) }" />
              <text>{{ getStatusLabel(task.status) }}</text>
            </view>
          </view>
          <text class="task-prompt">{{ task.params?.prompt || '' }}</text>
          <view v-if="!['completed', 'failed', 'error'].includes(task.status)" class="task-progress">
            <view class="progress-bar">
              <view class="progress-fill" :style="{ width: getProgressPercent(task.status) + '%', background: getStatusColor(task.status) }" />
            </view>
          </view>
          <view v-if="task.imageUrl" class="task-result">
            <image class="result-image" :src="task.imageUrl" mode="aspectFill" @click="previewImage(task.imageUrl!)" />
            <text class="result-tip">{{ t('create.clickToPreview') }}</text>
          </view>
          <text v-if="task.error" class="task-error">{{ task.error }}</text>
        </view>
      </view>
    </view>

    <view style="height: 40rpx;" />
  </view>
</template>

<style lang="scss" scoped>
.create-page { min-height: 100vh; background: #f5f5f7; }
.create-nav {
  background: #fff; border-bottom: 1rpx solid #eee;
  .nav-content { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; }
  .nav-back { width: 60rpx; display: flex; align-items: center; }
  .nav-title { font-size: 34rpx; font-weight: 600; color: #1d1d1f; }
  .nav-placeholder { width: 60rpx; }
}
.mode-tabs { display: flex; gap: 20rpx; padding: 24rpx; }
.mode-tab {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 12rpx;
  padding: 24rpx 0; background: #fff; border-radius: 20rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
  &:active { transform: scale(0.97); }
  &.active { background: #eef3ff; box-shadow: 0 0 0 2rpx #336cff; }
  .tab-label { font-size: 26rpx; font-weight: 600; color: #1d1d1f; }
}
.section { padding: 0 24rpx; margin-bottom: 24rpx; }
.section-title { display: block; font-size: 28rpx; font-weight: 600; color: #1d1d1f; margin-bottom: 16rpx; }
.empty-tip { padding: 32rpx 0; text-align: center; color: #9d9ea3; font-size: 28rpx; }
.device-scroll { white-space: nowrap; }
.device-list { display: inline-flex; gap: 16rpx; padding-bottom: 8rpx; }
.device-chip {
  display: inline-flex; align-items: center; gap: 12rpx; padding: 16rpx 24rpx;
  background: #fff; border-radius: 16rpx; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
  &:active { transform: scale(0.97); }
  &.selected { background: #eef3ff; box-shadow: 0 0 0 2rpx #336cff; }
  .chip-info { display: flex; flex-direction: column; gap: 2rpx; }
  .chip-name { font-size: 26rpx; font-weight: 600; color: #1d1d1f; }
  .chip-status { font-size: 22rpx; &.online { color: #07c160; } &.offline { color: #9d9ea3; } }
}
.prompt-box { background: #fff; border-radius: 20rpx; padding: 20rpx; position: relative; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04); }
.prompt-textarea { width: 100%; height: 200rpx; font-size: 28rpx; color: #1d1d1f; line-height: 1.6; }
.prompt-placeholder { color: #9d9ea3; }
.prompt-count { position: absolute; bottom: 12rpx; right: 20rpx; font-size: 22rpx; color: #c7c7cc; }
.submit-section { padding: 0 24rpx; margin-top: 32rpx; }
.submit-btn {
  width: 100%; padding: 28rpx 0; background: #336cff; border-radius: 20rpx;
  display: flex; align-items: center; justify-content: center;
  &:active { opacity: 0.9; }
  &.disabled { opacity: 0.4; }
  .submit-text { font-size: 32rpx; font-weight: 600; color: #fff; }
}
.tasks-section { margin-top: 32rpx; }
.task-list { display: flex; flex-direction: column; gap: 16rpx; }
.task-card { background: #fff; border-radius: 20rpx; padding: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04); display: flex; flex-direction: column; gap: 12rpx; }
.task-header { display: flex; justify-content: space-between; align-items: center; }
.task-cap { font-size: 26rpx; color: #1d1d1f; font-weight: 600; }
.task-status { display: flex; align-items: center; gap: 8rpx; font-size: 24rpx; font-weight: 600; }
.status-dot { width: 12rpx; height: 12rpx; border-radius: 50%; }
.task-prompt { font-size: 26rpx; color: #65686f; line-height: 1.5; word-break: break-word; }
.progress-bar { height: 8rpx; background: #edf1f7; border-radius: 4rpx; overflow: hidden; }
.progress-fill { height: 100%; border-radius: 4rpx; transition: width 0.5s ease; }
.task-result { display: flex; flex-direction: column; gap: 8rpx; }
.result-image { width: 100%; height: 320rpx; border-radius: 16rpx; background: #f5f5f7; }
.result-tip { font-size: 22rpx; color: #9d9ea3; text-align: center; }
.task-error { font-size: 24rpx; color: #ff4d4f; }
</style>
