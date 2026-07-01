<route lang="jsonc" type="page">
{
  "layout": "tabbar",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "我的"
  }
}
</route>

<script lang="ts" setup>
import type { V2DeviceInfo } from '@/api/v2/types'
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { v2GetDevices, v2ListTasks } from '@/api/v2'
import { t } from '@/i18n'

defineOptions({ name: 'MinePage' })

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

const devices = ref<V2DeviceInfo[]>([])
const onlineCount = ref(0)
const taskCount = ref(0)
const loading = ref(false)
const appVersion = import.meta.env.VITE_APP_VERSION || '1.0.0'

onShow(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
    onlineCount.value = devices.value.filter(d => d.status === 'online').length

    // 并行查询所有设备的活跃任务数（替代串行 for...of）
    const taskResults = await Promise.all(
      devices.value.map(d => v2ListTasks(d.deviceId, 'running', 100).catch(() => ({ count: 0 }))),
    )
    taskCount.value = taskResults.reduce((sum, r) => sum + (r.count || 0), 0)
  }
  catch (e) { console.error(e) }
  finally { loading.value = false }
}

function goDevices() {
  uni.switchTab({ url: '/pages/v2/device-list/index' })
}
function goConfig() {
  uni.switchTab({ url: '/pages/device-config/index' })
}
function goSettings() {
  uni.switchTab({ url: '/pages/settings/index' })
}
function goDigitalHuman() {
  uni.showToast({ title: t('nebula.digitalHumanComingSoon'), icon: 'none' })
}
function goLogout() {
  uni.vibrateShort({ type: 'medium' })
  uni.showModal({
    title: t('mine.logoutConfirmTitle'),
    content: t('mine.logoutConfirmContent'),
    success: (res) => {
      if (res.confirm) {
        uni.clearStorageSync()
        uni.reLaunch({ url: '/pages/v2/login/index' })
      }
    },
  })
}
function showAbout() {
  uni.showToast({ title: `${t('mine.versionToast')} ${appVersion}`, icon: 'none' })
}
</script>

<template>
  <view class="mine-page page-enter" :style="{ paddingTop: `${safeAreaTop}px` }">
    <!-- Nav -->
    <view class="mine-nav">
      <view class="nav-content">
        <text class="nav-title">
          {{ t('mine.title') }}
        </text>
      </view>
    </view>

    <!-- User Card -->
    <view class="user-card">
      <view class="user-avatar">
        <wd-icon name="person" size="48" color="#fff" />
      </view>
      <view class="user-info">
        <text class="user-name">
          {{ t('mine.userDefault') }}
        </text>
        <text class="user-sub">
          {{ t('mine.subtitle') }}
        </text>
      </view>
    </view>

    <!-- Stats -->
    <view class="stats-row">
      <view class="stat-card">
        <view v-if="loading" class="skeleton" style="width: 60rpx; height: 48rpx;" />
        <text v-else class="stat-num">
          {{ devices.length }}
        </text>
        <text class="stat-label">
          {{ t('mine.devices') }}
        </text>
      </view>
      <view class="stat-card">
        <view v-if="loading" class="skeleton" style="width: 60rpx; height: 48rpx;" />
        <text v-else class="stat-num online">
          {{ onlineCount }}
        </text>
        <text class="stat-label">
          {{ t('mine.online') }}
        </text>
      </view>
      <view class="stat-card">
        <view v-if="loading" class="skeleton" style="width: 60rpx; height: 48rpx;" />
        <text v-else class="stat-num tasks">
          {{ taskCount }}
        </text>
        <text class="stat-label">
          {{ t('mine.tasks') }}
        </text>
      </view>
    </view>

    <!-- Feature Menu -->
    <view class="menu-section">
      <text class="menu-title">
        {{ t('mine.featureCenter') }}
      </text>
      <view class="menu-list">
        <view class="menu-item" @click="goDevices">
          <wd-icon name="setting" size="22" color="#3b82f6" custom-class="menu-icon" />
          <view class="menu-content">
            <text class="menu-name">
              {{ t('mine.deviceMgmt') }}
            </text>
            <text class="menu-desc">
              {{ t('mine.deviceMgmtDesc') }}
            </text>
          </view>
          <wd-icon name="arrow-right" size="16" color="#c7c7cc" />
        </view>
        <view class="menu-item" @click="goConfig">
          <wd-icon name="wifi" size="22" color="#3b82f6" custom-class="menu-icon" />
          <view class="menu-content">
            <text class="menu-name">
              {{ t('mine.deviceConfig') }}
            </text>
            <text class="menu-desc">
              {{ t('mine.deviceConfigDesc') }}
            </text>
          </view>
          <wd-icon name="arrow-right" size="16" color="#c7c7cc" />
        </view>
        <view class="menu-item" @click="goDigitalHuman">
          <wd-icon name="user" size="22" color="#3b82f6" custom-class="menu-icon" />
          <view class="menu-content">
            <text class="menu-name">
              {{ t('mine.digitalHuman') }}
            </text>
            <text class="menu-desc">
              {{ t('mine.digitalHumanDesc') }}
            </text>
          </view>
          <wd-icon name="arrow-right" size="16" color="#c7c7cc" />
        </view>
      </view>
    </view>

    <!-- System Menu -->
    <view class="menu-section">
      <text class="menu-title">
        {{ t('mine.system') }}
      </text>
      <view class="menu-list">
        <view class="menu-item" @click="goSettings">
          <wd-icon name="setting" size="22" color="#666" custom-class="menu-icon" />
          <view class="menu-content">
            <text class="menu-name">
              {{ t('mine.settings') }}
            </text>
            <text class="menu-desc">
              {{ t('mine.settingsDesc') }}
            </text>
          </view>
          <wd-icon name="arrow-right" size="16" color="#c7c7cc" />
        </view>
        <view class="menu-item" @click="showAbout">
          <wd-icon name="info-circle" size="22" color="#666" custom-class="menu-icon" />
          <view class="menu-content">
            <text class="menu-name">
              {{ t('mine.about') }}
            </text>
            <text class="menu-desc">
              {{ t('mine.aboutDesc') }} v{{ appVersion }}
            </text>
          </view>
          <wd-icon name="arrow-right" size="16" color="#c7c7cc" />
        </view>
      </view>
    </view>

    <!-- Logout -->
    <view class="logout-section">
      <view class="logout-btn" @click="goLogout">
        <text class="logout-text">
          {{ t('mine.logout') }}
        </text>
      </view>
    </view>

    <view style="height: env(safe-area-inset-bottom);" />
  </view>
</template>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  background: var(--bg);
}

.mine-nav {
  background: var(--surface);
  border-bottom: 1rpx solid var(--border);

  .nav-content {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 88rpx;
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: var(--text);
  }
}

.user-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin: 24rpx;
  padding: 32rpx;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent2) 100%);
  border-radius: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(59, 130, 246, 0.25);
}

.user-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.user-name {
  font-size: 36rpx;
  font-weight: 700;
  color: #fff;
}

.user-sub {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.stats-row {
  display: flex;
  gap: 16rpx;
  padding: 0 24rpx;
  margin-bottom: 24rpx;
}

.stat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 0;
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
}

.stat-num {
  font-size: 44rpx;
  font-weight: 800;
  color: var(--accent);

  &.online {
    color: var(--green);
  }
  &.tasks {
    color: var(--amber);
  }
}

.stat-label {
  font-size: 24rpx;
  color: var(--muted);
}

.menu-section {
  padding: 0 24rpx;
  margin-bottom: 24rpx;
}

.menu-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 12rpx;
  padding-left: 8rpx;
}

.menu-list {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: 24rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx 24rpx;
  border-bottom: 1rpx solid var(--border);
  transition: background 0.15s ease;

  &:last-child {
    border-bottom: none;
  }
  &:active {
    background: var(--surface-h);
  }
}

.menu-icon {
  width: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.menu-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.menu-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text);
}

.menu-desc {
  font-size: 24rpx;
  color: var(--dim);
}

.logout-section {
  padding: 0 24rpx;
  margin-top: 16rpx;
}

.logout-btn {
  padding: 28rpx 0;
  background: var(--surface);
  border: 1rpx solid rgba(239, 68, 68, 0.15);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    background: rgba(239, 68, 68, 0.08);
  }
}

.logout-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #ef4444;
}
</style>
