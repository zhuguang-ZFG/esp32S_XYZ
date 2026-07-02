<route lang="jsonc" type="page">
{
  "layout": "tabbar",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "LiMa"
  }
}
</route>

<script lang="ts" setup>
import type { V2DeviceInfo, V2TaskInfo } from '@/api/v2/types'
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { v2GetDevices, v2ListTasks } from '@/api/v2'
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
  uni.navigateTo({ url: '/pages/create/ai-draw' })
}
function goImageDraw() {
  uni.navigateTo({ url: '/pages/create/image-draw' })
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
    created: t('create.status.pending'),
    dispatching: t('create.status.queued'),
    dispatched: t('create.status.queued'),
    accepted: t('create.status.queued'),
    running: t('create.status.running'),
    progress: t('create.status.running'),
    done: t('create.status.completed'),
    completed: t('create.status.completed'),
    failed: t('create.status.failed'),
    error: t('create.status.error'),
    cancelled: t('create.status.failed'),
    dead_letter: t('create.status.failed'),
  }
  return map[status] || status
}

function taskStatusColor(status: string): string {
  const map: Record<string, string> = {
    pending: 'var(--amber)',
    queued: 'var(--amber)',
    created: 'var(--amber)',
    dispatching: 'var(--amber)',
    dispatched: 'var(--amber)',
    accepted: 'var(--accent)',
    running: 'var(--accent)',
    progress: 'var(--accent)',
    done: 'var(--green)',
    completed: 'var(--green)',
    failed: 'var(--danger)',
    error: 'var(--danger)',
    cancelled: 'var(--muted)',
    dead_letter: 'var(--danger)',
  }
  return map[status] || 'var(--muted)'
}

function taskProgress(status: string): number {
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
  return map[status] ?? 0
}
</script>

<template>
  <view class="page-enter home" :style="{ paddingTop: `${safeAreaTop}px` }">
    <!-- 顶部标题（纯文字，无装饰） -->
    <view class="home-header">
      <text class="home-title">
        {{ t('workshop.title') }}
      </text>
      <text class="home-subtitle">
        {{ t('workshop.subtitle') }}
      </text>
    </view>

    <!-- ══ 设备状态卡（数据为核心）══ -->
    <view class="section">
      <view v-if="primaryDevice" class="device-hero workshop-panel" @click="goDeviceDetail(primaryDevice.deviceId)">
        <!-- 状态行：点灯 + 名称 -->
        <view class="hero-top">
          <view class="hero-status">
            <view class="pulse-dot" :class="primaryDevice.status === 'online' ? 'online' : 'offline'" />
            <text class="hero-status-text" :class="{ online: primaryDevice.status === 'online' }">
              {{ primaryDevice.status === 'online' ? t('workshop.online') : t('workshop.offline') }}
            </text>
          </view>
          <text class="hero-model">
            {{ primaryDevice.model || t('workshop.device') }}
          </text>
        </view>

        <!-- 大数字：工作幅面（唯一的 hero number） -->
        <view class="hero-metric">
          <text class="metric-value">
            {{ primaryDevice.workspaceMm?.x || 0 }}<text class="metric-x">
              ×
            </text>{{ primaryDevice.workspaceMm?.y || 0 }}
          </text>
          <text class="metric-unit">
            mm 工作幅面
          </text>
        </view>

        <!-- 次要数据行（中性色，不抢眼） -->
        <view class="hero-sub">
          <view class="sub-item">
            <text class="sub-label">
              固件
            </text>
            <text class="sub-value">
              v{{ primaryDevice.fwRev || '—' }}
            </text>
          </view>
          <view class="sub-divider" />
          <view class="sub-item">
            <text class="sub-label">
              设备
            </text>
            <text class="sub-value">
              {{ devices.length }} 台
            </text>
          </view>
          <view class="sub-divider" />
          <view class="sub-item">
            <text class="sub-label">
              编号
            </text>
            <text class="sub-value mono">
              {{ primaryDevice.deviceId.slice(0, 8) }}
            </text>
          </view>
        </view>
      </view>

      <!-- 无设备状态 -->
      <view v-else-if="!loading" class="workshop-panel empty-hero" @click="goDevices">
        <text class="empty-icon">
          ＋
        </text>
        <text class="empty-text">
          {{ t('workshop.noDevices') }}
        </text>
        <text class="empty-hint">
          {{ t('workshop.addDeviceHint') }}
        </text>
      </view>
    </view>

    <!-- ══ AI 创作入口 ══ -->
    <view class="section">
      <text class="section-title">
        {{ t('workshop.aiCreate') }}
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
        <view class="create-card workshop-panel-interactive" @click="goImageDraw">
          <view class="create-icon image">
            <text>🖼️</text>
          </view>
          <text class="create-name">
            {{ t('workshop.imageDraw') }}
          </text>
          <text class="create-desc">
            {{ t('workshop.imageDrawDesc') }}
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
            <text>◉</text>
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
        <text class="section-title">
          {{ t('workshop.recentTasks') }}
        </text>
        <text class="section-more" @click="goDeviceDetail(primaryDevice?.deviceId || '')">
          {{ t('workshop.viewAll') }}
        </text>
      </view>
      <view class="task-list">
        <view v-for="task in recentTasks" :key="task.taskId" class="workshop-panel task-item">
          <view class="task-info">
            <text class="task-cap">
              {{ task.params?.imageUrl && !task.params?.prompt ? '🖼️' : '✦' }} {{ task.params?.imageUrl && !task.params?.prompt ? t('workshop.imageDraw') : t('workshop.aiDraw') }}
            </text>
            <text class="task-status-text" :style="{ color: taskStatusColor(task.status) }">
              {{ taskStatusLabel(task.status) }}
            </text>
          </view>
          <view class="task-track">
            <view class="task-track-fill" :style="{ width: `${taskProgress(task.status)}%`, background: taskStatusColor(task.status) }" />
          </view>
        </view>
      </view>
    </view>

    <!-- ══ 快捷操作 ══ -->
    <view class="section">
      <view class="quick-row">
        <view class="workshop-panel-interactive quick-btn" @click="goDevices">
          <text class="quick-icon">
            ▣
          </text>
          <text class="quick-text">
            {{ t('workshop.myDevices') }}
          </text>
        </view>
        <view class="quick-btn workshop-panel-interactive" @click="goConfig">
          <text class="quick-icon">
            ⌗
          </text>
          <text class="quick-text">
            {{ t('workshop.config') }}
          </text>
        </view>
        <view class="quick-btn workshop-panel-interactive" @click="goSettings">
          <text class="quick-icon">
            ⚙
          </text>
          <text class="quick-text">
            {{ t('workshop.systemSettings') }}
          </text>
        </view>
      </view>
    </view>

    <view style="height: env(safe-area-inset-bottom);" />
  </view>
</template>

<style lang="scss" scoped>
.home {
  min-height: 100vh;
  background: var(--bg);
}

// ── 标题栏（克制：纯白标题 + 灰副标）──
.home-header {
  padding: 32rpx 32rpx 24rpx;
}
.home-title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: var(--text);
  letter-spacing: 1rpx;
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
  margin-bottom: 36rpx;
}
.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 20rpx;
}
.section-more {
  font-size: 24rpx;
  color: var(--muted);
}

// ── 设备状态 Hero 卡（数据为核心）──
.device-hero {
  padding: 32rpx 28rpx;
}
.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28rpx;
}
.hero-status {
  display: flex;
  align-items: center;
  gap: 10rpx;
}
.hero-status-text {
  font-size: 24rpx;
  color: var(--dim);
  &.online {
    color: var(--green);
    font-weight: 600;
  }
}
.hero-model {
  font-size: 26rpx;
  color: var(--muted);
  font-weight: 500;
}

// hero 大数字（全页唯一的视觉焦点）
.hero-metric {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-bottom: 28rpx;
}
.metric-value {
  font-size: 72rpx;
  font-weight: 700;
  color: var(--text);
  line-height: 1;
  letter-spacing: -1rpx;
}
.metric-x {
  font-size: 48rpx;
  color: var(--dim);
  font-weight: 400;
  margin: 0 4rpx;
}
.metric-unit {
  font-size: 24rpx;
  color: var(--muted);
}

// 次要数据行
.hero-sub {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid var(--border);
}
.sub-item {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.sub-label {
  font-size: 20rpx;
  color: var(--dim);
}
.sub-value {
  font-size: 26rpx;
  color: var(--text);
  font-weight: 500;
  &.mono {
    font-family: monospace;
    font-size: 24rpx;
  }
}
.sub-divider {
  width: 1rpx;
  height: 40rpx;
  background: var(--border);
}

// ── 空状态 ──
.empty-hero {
  padding: 64rpx 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.empty-icon {
  font-size: 56rpx;
  color: var(--dim);
}
.empty-text {
  font-size: 28rpx;
  color: var(--text);
}
.empty-hint {
  font-size: 22rpx;
  color: var(--muted);
}

// ── AI 创作卡片 ──
.create-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.create-card {
  padding: 28rpx;
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
  margin-bottom: 6rpx;
  // 图标底色保持低饱和
  &.draw {
    background: var(--accent-g);
    color: var(--accent);
  }
  &.write {
    background: var(--cyan-g);
    color: var(--cyan);
  }
  &.chat {
    background: var(--violet-g);
    color: var(--violet);
  }
  &.human {
    background: var(--amber-g);
    color: var(--amber);
  }
}
.create-name {
  font-size: 28rpx;
  font-weight: 600;
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
  padding: 24rpx 28rpx;
}
.task-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}
.task-cap {
  font-size: 26rpx;
  color: var(--text);
  font-weight: 500;
}
.task-status-text {
  font-size: 22rpx;
  font-weight: 600;
}
.task-track {
  height: 4rpx;
  background: rgba(255, 255, 255, 0.06);
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
  gap: 12rpx;
  padding: 28rpx 0;
}
.quick-icon {
  font-size: 36rpx;
  color: var(--muted);
}
.quick-text {
  font-size: 22rpx;
  color: var(--muted);
}
</style>
