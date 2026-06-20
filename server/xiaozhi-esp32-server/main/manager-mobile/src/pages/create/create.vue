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
import { v2GetDevices, v2SubmitTask, v2GetTask } from '@/api/v2'
import type { V2DeviceInfo, V2TaskInfo } from '@/api/v2/types'

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

// 模式: draw | write
const mode = ref<'draw' | 'write'>('draw')
const devices = ref<V2DeviceInfo[]>([])
const selectedDeviceId = ref('')
const prompt = ref('')
const submitting = ref(false)

// 任务追踪
const tasks = ref<V2TaskInfo[]>([])
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

onLoad((options: any) => {
  if (options?.mode === 'write') {
    mode.value = 'write'
  } else {
    mode.value = 'draw'
  }
})

onShow(() => { loadDevices() })

onUnmounted(() => {
  if (pollTimer.value) clearInterval(pollTimer.value)
})

async function loadDevices() {
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
    const online = devices.value.find(d => d.status === 'online')
    if (online) {
      selectedDeviceId.value = online.deviceId
    } else if (devices.value.length) {
      selectedDeviceId.value = devices.value[0].deviceId
    }
  } catch (e) { console.error(e) }
}

function switchMode(m: 'draw' | 'write') {
  mode.value = m
}

async function handleSubmit() {
  if (!selectedDeviceId.value) {
    uni.showToast({ title: '请先选择设备', icon: 'none' })
    return
  }
  if (!prompt.value.trim()) {
    uni.showToast({ title: '请输入提示词', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    const capability = mode.value === 'draw' ? 'draw' : 'write'
    const res = await v2SubmitTask(
      selectedDeviceId.value,
      capability,
      { prompt: prompt.value.trim() },
    )
    const newTask: V2TaskInfo = {
      taskId: res.taskId || 'unknown',
      status: 'pending',
      deviceId: selectedDeviceId.value,
      capability,
      params: { prompt: prompt.value.trim() },
      sent: res.sent,
      queueDepth: res.queueDepth,
      createdAt: new Date().toISOString(),
    }
    tasks.value.unshift(newTask)
    prompt.value = ''
    uni.showToast({ title: '任务已下发', icon: 'success' })
    startPolling(newTask.taskId)
  } catch (e: any) {
    uni.showToast({ title: `提交失败：${e.message || '未知错误'}`, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function startPolling(taskId: string) {
  if (pollTimer.value) clearInterval(pollTimer.value)
  pollTimer.value = setInterval(async () => {
    try {
      const task = await v2GetTask(taskId)
      const idx = tasks.value.findIndex(t => t.taskId === taskId)
      if (idx >= 0) {
        tasks.value[idx] = { ...tasks.value[idx], ...task }
        // 如果任务完成或失败，停止轮询
        if (task.status === 'completed' || task.status === 'failed' || task.status === 'error') {
          if (pollTimer.value) clearInterval(pollTimer.value)
          pollTimer.value = null
        }
      }
    } catch (e) {
      console.error('轮询任务状态失败', e)
    }
  }, 3000)
}

function getDeviceIcon(model?: string) {
  if (model?.includes('draw')) return '🎨'
  if (model?.includes('write')) return '📝'
  return '🤖'
}

function getModeLabel() {
  return mode.value === 'draw' ? 'AI 绘图' : 'AI 写字'
}

function getPlaceholder() {
  return mode.value === 'draw'
    ? '例如：一只在星云中飞翔的凤凰，赛博朋克风格'
    : '例如：欢迎参加 LiMa 星云发布会，科技感字体'
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    pending: '等待中',
    queued: '排队中',
    running: '执行中',
    completed: '已完成',
    failed: '失败',
    error: '出错',
    approved: '已批准',
    waiting_approval: '待审批',
  }
  return map[status] || status
}

function getStatusColor(status: string) {
  const map: Record<string, string> = {
    pending: '#f59e0b',
    queued: '#f59e0b',
    running: '#3b82f6',
    completed: '#34d399',
    failed: '#ef4444',
    error: '#ef4444',
    approved: '#3b82f6',
    waiting_approval: '#f59e0b',
  }
  return map[status] || '#8b95a8'
}

function previewImage(url: string) {
  uni.previewImage({ urls: [url], current: url })
}

function formatTime(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}
</script>

<template>
  <view class="create-page" :style="{ paddingTop: safeAreaTop + 'px' }">
    <!-- 导航栏 -->
    <view class="create-nav">
      <view class="nav-content">
        <view class="nav-back" @click="uni.navigateBack()">
          <text class="back-icon">‹</text>
        </view>
        <text class="nav-title">AI 创作</text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <!-- 模式切换 -->
    <view class="mode-tabs">
      <view
        class="mode-tab"
        :class="{ active: mode === 'draw' }"
        @click="switchMode('draw')"
      >
        <text class="tab-icon">🎨</text>
        <text class="tab-label">AI 绘图</text>
      </view>
      <view
        class="mode-tab"
        :class="{ active: mode === 'write' }"
        @click="switchMode('write')"
      >
        <text class="tab-icon">✍️</text>
        <text class="tab-label">AI 写字</text>
      </view>
    </view>

    <!-- 设备选择 -->
    <view class="section">
      <text class="section-title">选择设备</text>
      <view v-if="!devices.length" class="empty-tip">
        <text>暂无设备，请先添加设备</text>
      </view>
      <scroll-view v-else scroll-x class="device-scroll">
        <view class="device-list">
          <view
            v-for="d in devices"
            :key="d.deviceId"
            class="device-chip"
            :class="{ selected: selectedDeviceId === d.deviceId }"
            @click="selectedDeviceId = d.deviceId"
          >
            <text class="chip-icon">{{ getDeviceIcon(d.model) }}</text>
            <view class="chip-info">
              <text class="chip-name">{{ d.model || '设备' }}</text>
              <text class="chip-status" :class="d.status">{{ d.status === 'online' ? '● 在线' : '● 离线' }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 提示词输入 -->
    <view class="section">
      <text class="section-title">{{ getModeLabel() }}提示词</text>
      <view class="prompt-box">
        <textarea
          v-model="prompt"
          class="prompt-textarea"
          :placeholder="getPlaceholder()"
          placeholder-class="prompt-placeholder"
          maxlength="500"
          :disabled="submitting"
        />
        <text class="prompt-count">{{ prompt.length }}/500</text>
      </view>
    </view>

    <!-- 提交按钮 -->
    <view class="submit-section">
      <view
        class="submit-btn"
        :class="{ disabled: !selectedDeviceId || !prompt.trim() || submitting }"
        @click="handleSubmit"
      >
        <text v-if="submitting" class="submit-text">提交中...</text>
        <text v-else class="submit-text">
          {{ mode === 'draw' ? '🎨 开始绘图' : '✍️ 开始写字' }}
        </text>
      </view>
    </view>

    <!-- 任务追踪 -->
    <view v-if="tasks.length" class="section tasks-section">
      <text class="section-title">任务追踪</text>
      <view class="task-list">
        <view
          v-for="task in tasks"
          :key="task.taskId"
          class="task-card"
        >
          <view class="task-header">
            <view class="task-meta">
              <text class="task-capability">{{ task.capability === 'draw' || task.capability === 'draw_generated' ? '🎨 绘图' : '✍️ 写字' }}</text>
              <text class="task-time">{{ formatTime(task.createdAt) }}</text>
            </view>
            <view class="task-status" :style="{ color: getStatusColor(task.status) }">
              <text class="status-dot" :style="{ background: getStatusColor(task.status) }" />
              <text>{{ getStatusLabel(task.status) }}</text>
            </view>
          </view>

          <!-- 提示词 -->
          <text class="task-prompt">{{ task.params?.prompt || task.params?.text || '' }}</text>

          <!-- 进度条 -->
          <view v-if="task.status !== 'completed' && task.status !== 'failed' && task.status !== 'error'" class="task-progress">
            <view class="progress-bar">
              <view
                class="progress-fill"
                :style="{
                  width: task.status === 'running' ? '60%' : task.status === 'queued' ? '30%' : '10%',
                  background: getStatusColor(task.status)
                }"
              />
            </view>
          </view>

          <!-- 结果图片 -->
          <view v-if="task.imageUrl" class="task-result">
            <image
              class="result-image"
              :src="task.imageUrl"
              mode="aspectFill"
              @click="previewImage(task.imageUrl!)"
            />
            <text class="result-tip">点击图片预览</text>
          </view>

          <!-- 错误信息 -->
          <text v-if="task.error" class="task-error">{{ task.error }}</text>
        </view>
      </view>
    </view>

    <!-- 底部留白 -->
    <view style="height: 40rpx;" />
  </view>
</template>

<style lang="scss" scoped>
.create-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #07070f 0%, #0a0a14 100%);
}

/* 导航栏 */
.create-nav {
  background: rgba(7, 7, 15, 0.95);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);

  .nav-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 88rpx;
    padding: 0 24rpx;
  }

  .nav-back {
    width: 60rpx;
    display: flex;
    align-items: center;

    .back-icon {
      font-size: 48rpx;
      color: #f0f4f8;
      line-height: 1;
    }
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: #f0f4f8;
  }

  .nav-placeholder {
    width: 60rpx;
  }
}

/* 模式切换 */
.mode-tabs {
  display: flex;
  gap: 20rpx;
  padding: 32rpx;
}

.mode-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 28rpx 0;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 24rpx;
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.97);
  }

  &.active {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.1));
    border-color: rgba(59, 130, 246, 0.3);
  }

  .tab-icon {
    font-size: 48rpx;
  }

  .tab-label {
    font-size: 28rpx;
    font-weight: 600;
    color: #f0f4f8;
  }
}

/* 区域 */
.section {
  padding: 0 32rpx;
  margin-bottom: 32rpx;

  .section-title {
    display: block;
    font-size: 30rpx;
    font-weight: 600;
    color: #f0f4f8;
    margin-bottom: 20rpx;
  }
}

.empty-tip {
  padding: 40rpx 0;
  text-align: center;
  color: #5a6372;
  font-size: 28rpx;
}

/* 设备选择 */
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
  gap: 16rpx;
  padding: 20rpx 28rpx;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 20rpx;
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.97);
  }

  &.selected {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.1));
    border-color: rgba(59, 130, 246, 0.3);
  }

  .chip-icon {
    font-size: 40rpx;
  }

  .chip-info {
    display: flex;
    flex-direction: column;
    gap: 4rpx;

    .chip-name {
      font-size: 28rpx;
      font-weight: 600;
      color: #f0f4f8;
    }

    .chip-status {
      font-size: 22rpx;

      &.online { color: #34d399; }
      &.offline { color: #5a6372; }
    }
  }
}

/* 提示词输入 */
.prompt-box {
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 24rpx;
  padding: 24rpx;
  position: relative;
}

.prompt-textarea {
  width: 100%;
  height: 200rpx;
  font-size: 30rpx;
  color: #f0f4f8;
  line-height: 1.6;
}

.prompt-placeholder {
  color: #5a6372;
}

.prompt-count {
  position: absolute;
  bottom: 16rpx;
  right: 24rpx;
  font-size: 22rpx;
  color: #3a4252;
}

/* 提交区 */
.submit-section {
  padding: 0 32rpx;
  margin-top: 40rpx;
}

.submit-btn {
  width: 100%;
  padding: 28rpx 0;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.98);
    opacity: 0.9;
  }

  &.disabled {
    opacity: 0.4;
  }

  .submit-text {
    font-size: 32rpx;
    font-weight: 600;
    color: #fff;
  }
}

/* 任务追踪 */
.tasks-section {
  margin-top: 40rpx;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.task-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 24rpx;
  padding: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.task-capability {
  font-size: 26rpx;
  color: #f0f4f8;
  font-weight: 600;
}

.task-time {
  font-size: 22rpx;
  color: #5a6372;
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
  font-size: 28rpx;
  color: #c0c8d8;
  line-height: 1.5;
  word-break: break-word;
}

.task-progress {
  margin-top: 4rpx;
}

.progress-bar {
  height: 6rpx;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 3rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3rpx;
  transition: width 0.5s ease;
}

.task-result {
  margin-top: 8rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.result-image {
  width: 100%;
  height: 320rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.03);
}

.result-tip {
  font-size: 22rpx;
  color: #5a6372;
  text-align: center;
}

.task-error {
  font-size: 24rpx;
  color: #ef4444;
  margin-top: 4rpx;
}
</style>
