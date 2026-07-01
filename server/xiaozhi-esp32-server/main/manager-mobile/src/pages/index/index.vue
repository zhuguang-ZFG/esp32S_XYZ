<route lang="jsonc" type="page">
{
  "layout": "tabbar",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "LiMa 工坊"
  }
}
</route>

<script lang="ts" setup>
import type { V2DeviceInfo, V2TaskInfo } from '@/api/v2/types'
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { v2GetDevices, v2GetTask, v2ListTasks } from '@/api/v2'
import { t } from '@/i18n'

defineOptions({ name: 'WorkshopHome' })

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

const devices = ref<V2DeviceInfo[]>([])
const loading = ref(false)
const recentTasks = ref<V2TaskInfo[]>([])

// 主要在线设备（取第一个在线设备用于遥测面板）
const primaryDevice = computed(() => {
  return devices.value.find(d => d.status === 'online') || devices.value[0] || null
})

onShow(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
    // 加载最近任务
    if (primaryDevice.value) {
      try {
        const taskRes = await v2ListTasks(primaryDevice.value.deviceId, '', 3)
        recentTasks.value = taskRes.tasks || []
      }
      catch { recentTasks.value = [] }
    }
  }
  catch (e) { console.error(e) }
  finally { loading.value = false }
}

function goChat() {
  uni.navigateTo({ url: '/pages/chat/chat' })
}
function goDraw() {
  uni.navigateTo({ url: '/pages/create/create?mode=draw' })
}
function goWrite() {
  uni.navigateTo({ url: '/pages/create/create?mode=write' })
}
function goDigitalHuman() {
  uni.showToast({ title: t('nebula.digitalHumanComingSoon'), icon: 'none' })
}
function goDevices() {
  uni.switchTab({ url: '/pages/v2/device-list/index' })
}
function goDeviceDetail(id: string) {
  uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${id}` })
}
function goConfig() {
  uni.switchTab({ url: '/pages/device-config/index' })
}
function goSettings() {
  uni.switchTab({ url: '/pages/settings/index' })
}

function taskStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: t('create.status.pending'),
    queued: t('create.status.queued'),
    running: t('create.status.running'),
    completed: t('create.status.completed'),
    failed: t('create.status.failed'),
    error: t('create.status.error'),
  }
  return map[status] || status
}

function taskStatusColor(status: string): string {
  const map: Record<string, string> = {
    pending: 'var(--amber)',
    queued: 'var(--amber)',
    running: 'var(--accent)',
    completed: 'var(--green)',
    failed: 'var(--danger)',
    error: 'var(--danger)',
  }
  return map[status] || 'var(--muted)'
}

function taskProgress(status: string): number {
  const map: Record<string, number> = {
    pending: 10, queued: 20, running: 60, completed: 100, failed: 100, error: 100,
  }
  return map[status] ?? 0
}
</script>

<template>
  <view class="workshop-home workshop-bg page-enter" :style="{ paddingTop: `${safeAreaTop}px` }">
    <!-- 标题栏 -->
    <view class="home-header">
      <text class="home-title neon-text">
        {{ t('workshop.title') }}
      </text>
      <text class="home-subtitle">
        {{ t('workshop.subtitle') }}
      </text>
    </view>

    <!-- ══ 设备遥测面板 ══ -->
    <view class="section">
      <view v-if="primaryDevice" class="telemetry-panel workshop-panel" @click="goDeviceDetail(primaryDevice.deviceId)">
        <!-- 面板头部 -->
        <view class="panel-header">
          <view class="panel-title-row">
            <text class="panel-title">
              ◢ {{ t('workshop.status') }}
            </text>
            <view class="panel-status">
              <view class="pulse-dot" :class="{ offline: primaryDevice.status !== 'online' }" />
              <text class="panel-status-text" :class="{ online: primaryDevice.status === 'online' }">
                {{ primaryDevice.status === 'online' ? t('workshop.online') : t('workshop.offline') }}
              </text>
            </view>
          </view>
          <text class="panel-device-name">
            {{ primaryDevice.model || t('workshop.device') }}
          </text>
        </view>

        <!-- 遥测数据网格 -->
        <view class="telemetry-grid">
          <view class="telemetry-item">
            <text class="telemetry-label">{{ t('workshop.workspace') }}</text>
            <text class="telemetry-value">
              {{ primaryDevice.workspaceMm?.x || 0 }} × {{ primaryDevice.workspaceMm?.y || 0 }} mm
            </text>
          </view>
          <view class="telemetry-item">
            <text class="telemetry-label">{{ t('workshop.firmware') }}</text>
            <text class="telemetry-value">v{{ primaryDevice.fwRev || '—' }}</text>
          </view>
          <view class="telemetry-item">
            <text class="telemetry-label">{{ t('workshop.deviceId') }}</text>
            <text class="telemetry-value mono">{{ primaryDevice.deviceId.slice(0, 12) }}</text>
          </view>
          <view class="telemetry-item">
            <text class="telemetry-label">{{ t('workshop.devices') }}</text>
            <text class="telemetry-value">{{ devices.length }} {{ t('workshop.units') }}</text>
          </view>
        </view>

        <!-- 扫描线装饰（在线时） -->
        <view v-if="primaryDevice.status === 'online'" class="panel-scan-line scan-line" />
      </view>

      <!-- 无设备状态 -->
      <view v-else-if="!loading" class="empty-panel workshop-panel" @click="goDevices">
        <text class="empty-icon">📡</text>
        <text class="empty-text">{{ t('workshop.noDevices') }}</text>
        <text class="empty-hint">{{ t('workshop.addDeviceHint') }}</text>
      </view>
    </view>

    <!-- ══ AI 创作入口 ══ -->
    <view class="section">
      <text class="section-title neon-text">
        ◢ {{ t('workshop.aiCreate') }}
      </text>
      <view class="create-grid">
        <view class="create-card workshop-panel-interactive" @click="goDraw">
          <view class="create-icon draw">
            <text>✦</text>
          </view>
          <text class="create-name">
            {{ t('workshop.aiDraw') }}
          </text>
          <text class="create-desc">
            {{ t('workshop.aiDrawDesc') }}
          </text>
        </view>
        <view class="create-card workshop-panel-interactive" @click="goWrite">
          <view class="create-icon write">
            <text>✎</text>
          </view>
          <text class="create-name">
            {{ t('workshop.aiWrite') }}
          </text>
          <text class="create-desc">
            {{ t('workshop.aiWriteDesc') }}
          </text>
        </view>
        <view class="create-card workshop-panel-interactive" @click="goChat">
          <view class="create-icon chat">
            <text>💬</text>
          </view>
          <text class="create-name">
            {{ t('workshop.aiChat') }}
          </text>
          <text class="create-desc">
            {{ t('workshop.aiChatDesc') }}
          </text>
        </view>
        <view class="create-card workshop-panel-interactive" @click="goDigitalHuman">
          <view class="create-icon human">
            <text> humanoid</text>
          </view>
          <text class="create-name">
            {{ t('workshop.digitalHuman') }}
          </text>
          <text class="create-desc">
            {{ t('workshop.digitalHumanDesc') }}
          </text>
        </view>
      </view>
    </view>

    <!-- ══ 最近任务 ══ -->
    <view v-if="recentTasks.length" class="section">
      <view class="section-header">
        <text class="section-title neon-text">
          ◢ {{ t('workshop.recentTasks') }}
        </text>
        <text class="section-more" @click="goDeviceDetail(primaryDevice?.deviceId || '')">
          {{ t('workshop.viewAll') }} →
        </text>
      </view>
      <view class="task-list">
        <view v-for="task in recentTasks" :key="task.taskId" class="task-item workshop-panel">
          <view class="task-info">
            <text class="task-cap">
              {{ task.capability === 'draw_generated' ? '✦' : '✎' }} {{ task.capability === 'draw_generated' ? t('workshop.aiDraw') : t('workshop.aiWrite') }}
            </text>
            <text class="task-status-text" :style="{ color: taskStatusColor(task.status) }">
              {{ taskStatusLabel(task.status) }}
            </text>
          </view>
          <!-- 进度轨道 -->
          <view class="task-track">
            <view class="task-track-fill" :style="{ width: `${taskProgress(task.status)}%`, background: taskStatusColor(task.status) }" />
          </view>
        </view>
      </view>
    </view>

    <!-- ══ 快捷操作 ══ -->
    <view class="section">
      <view class="quick-row">
        <view class="quick-btn workshop-panel-interactive" @click="goDevices">
          <text class="quick-icon">📡</text>
          <text class="quick-text">{{ t('workshop.myDevices') }}</text>
        </view>
        <view class="quick-btn workshop-panel-interactive" @click="goConfig">
          <text class="quick-icon">📶</text>
          <text class="quick-text">{{ t('workshop.config') }}</text>
        </view>
        <view class="quick-btn workshop-panel-interactive" @click="goSettings">
          <text class="quick-icon">⚙</text>
          <text class="quick-text">{{ t('workshop.systemSettings') }}</text>
        </view>
      </view>
    </view>

    <view style="height: env(safe-area-inset-bottom);" />
  </view>
</template>

<style lang="scss" scoped>
.workshop-home {
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

// ── 标题栏 ──
.home-header {
  padding: 32rpx;
}
.home-title {
  display: block;
  font-size: 48rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}
.home-subtitle {
  display: block;
  font-size: 24rpx;
  color: var(--muted);
  margin-top: 8rpx;
}

// ── 分区 ──
.section {
  padding: 0 24rpx;
  margin-bottom: 32rpx;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 16rpx;
}
.section-more {
  font-size: 24rpx;
  color: var(--accent);
}

// ── 遥测面板 ──
.telemetry-panel {
  padding: 28rpx;
}
.panel-header {
  margin-bottom: 20rpx;
}
.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 2rpx;
}
.panel-status {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.panel-status-text {
  font-size: 24rpx;
  color: var(--dim);
  &.online { color: var(--accent); }
}
.panel-device-name {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text);
  margin-top: 8rpx;
}

// ── 遥测数据网格 ──
.telemetry-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.telemetry-item {
  background: rgba(0, 255, 170, 0.03);
  border: 1rpx solid var(--border);
  border-radius: var(--r-sm);
  padding: 16rpx 20rpx;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.telemetry-label {
  font-size: 20rpx;
  color: var(--dim);
  text-transform: uppercase;
  letter-spacing: 1rpx;
}
.telemetry-value {
  font-size: 26rpx;
  color: var(--text);
  font-weight: 600;
  &.mono { font-family: monospace; }
}

.panel-scan-line {
  height: 2rpx;
  margin-top: 16rpx;
  border-radius: 1rpx;
}

// ── 空面板 ──
.empty-panel {
  padding: 48rpx 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.empty-icon { font-size: 64rpx; }
.empty-text { font-size: 28rpx; color: var(--muted); }
.empty-hint { font-size: 22rpx; color: var(--dim); }

// ── AI 创作卡片 ──
.create-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.create-card {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  min-height: 180rpx;
}
.create-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  margin-bottom: 4rpx;
  &.draw { background: rgba(0, 255, 170, 0.12); color: var(--accent); }
  &.write { background: rgba(0, 212, 255, 0.12); color: var(--accent2); }
  &.chat { background: rgba(139, 92, 246, 0.12); color: var(--violet); }
  &.human { background: rgba(255, 184, 0, 0.12); color: var(--amber); }
}
.create-name {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--text);
}
.create-desc {
  font-size: 20rpx;
  color: var(--dim);
  line-height: 1.4;
}

// ── 任务列表 ──
.task-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.task-item {
  padding: 20rpx 24rpx;
}
.task-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.task-cap {
  font-size: 24rpx;
  color: var(--text);
  font-weight: 600;
}
.task-status-text {
  font-size: 22rpx;
  font-weight: 600;
}
.task-track {
  height: 4rpx;
  background: rgba(0, 255, 170, 0.06);
  border-radius: 2rpx;
  overflow: hidden;
}
.task-track-fill {
  height: 100%;
  border-radius: 2rpx;
  transition: width 0.5s ease;
}

// ── 快捷按钮 ──
.quick-row {
  display: flex;
  gap: 16rpx;
}
.quick-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 24rpx 0;
}
.quick-icon { font-size: 36rpx; }
.quick-text { font-size: 22rpx; color: var(--muted); }
</style>
