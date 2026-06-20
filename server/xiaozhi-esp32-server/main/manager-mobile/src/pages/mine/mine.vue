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
import { onShow, ref } from 'vue'
import { v2GetDevices } from '@/api/v2'
import type { V2DeviceInfo } from '@/api/v2/types'
import { getEnvBaseUrl } from '@/utils'

defineOptions({ name: 'MinePage' })

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

const devices = ref<V2DeviceInfo[]>([])
const onlineCount = ref(0)
const loading = ref(false)

onShow(() => { loadDevices() })

async function loadDevices() {
  loading.value = true
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
    onlineCount.value = devices.value.filter(d => d.status === 'online').length
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

function goDevices() {
  uni.navigateTo({ url: '/pages/v2/device-list/index' })
}
function goConfig() {
  uni.navigateTo({ url: '/pages/device-config/index' })
}
function goSettings() {
  uni.navigateTo({ url: '/pages/settings/index' })
}
function goDigitalHuman() {
  const url = getEnvBaseUrl().replace(/\/$/, '') + '/digital-human'
  uni.navigateTo({ url: `/pages-sub/demo/index?url=${encodeURIComponent(url)}` })
}
function goLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        uni.clearStorageSync()
        uni.reLaunch({ url: '/pages/v2/login/index' })
      }
    }
  })
}
</script>

<template>
  <view class="mine-page" :style="{ paddingTop: safeAreaTop + 'px' }">
    <!-- 用户信息 -->
    <view class="user-card">
      <view class="user-avatar">
        <text class="avatar-text">👤</text>
      </view>
      <view class="user-info">
        <text class="user-name">LiMa 用户</text>
        <text class="user-sub">AI 智能设备星云系统</text>
      </view>
      <view class="user-badge">
        <text class="badge-text">VIP</text>
      </view>
    </view>

    <!-- 状态概览 -->
    <view class="stats-row">
      <view class="stat-card">
        <text class="stat-num">{{ devices.length }}</text>
        <text class="stat-label">设备</text>
      </view>
      <view class="stat-card">
        <text class="stat-num" style="color: #34d399;">{{ onlineCount }}</text>
        <text class="stat-label">在线</text>
      </view>
      <view class="stat-card">
        <text class="stat-num" style="color: #f59e0b;">0</text>
        <text class="stat-label">任务</text>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section">
      <text class="menu-title">功能中心</text>
      <view class="menu-list">
        <view class="menu-item" @click="goDevices">
          <view class="menu-icon">🔧</view>
          <view class="menu-content">
            <text class="menu-name">设备管理</text>
            <text class="menu-desc">查看和管理所有设备</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="goConfig">
          <view class="menu-icon">📡</view>
          <view class="menu-content">
            <text class="menu-name">设备配网</text>
            <text class="menu-desc">为新设备配置 WiFi 网络</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="goDigitalHuman">
          <view class="menu-icon">👤</view>
          <view class="menu-content">
            <text class="menu-name">数字人</text>
            <text class="menu-desc">2D 语音交互数字人</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 系统菜单 -->
    <view class="menu-section">
      <text class="menu-title">系统</text>
      <view class="menu-list">
        <view class="menu-item" @click="goSettings">
          <view class="menu-icon">⚙️</view>
          <view class="menu-content">
            <text class="menu-name">设置</text>
            <text class="menu-desc">网络、缓存、语言、隐私</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="uni.showToast({ title: '当前版本 1.0.0', icon: 'none' })">
          <view class="menu-icon">ℹ️</view>
          <view class="menu-content">
            <text class="menu-name">关于</text>
            <text class="menu-desc">LiMa 星云 v1.0.0</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <view class="logout-btn" @click="goLogout">
        <text class="logout-text">退出登录</text>
      </view>
    </view>

    <!-- 底部留白 -->
    <view style="height: 40rpx;" />
  </view>
</template>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #07070f 0%, #0a0a14 100%);
}

/* 用户卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin: 32rpx;
  padding: 32rpx;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.12), rgba(139, 92, 246, 0.08));
  border: 1rpx solid rgba(59, 130, 246, 0.15);
  border-radius: 24rpx;

  .user-avatar {
    width: 96rpx;
    height: 96rpx;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.08);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    .avatar-text {
      font-size: 48rpx;
    }
  }

  .user-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 8rpx;

    .user-name {
      font-size: 36rpx;
      font-weight: 700;
      color: #f0f4f8;
    }

    .user-sub {
      font-size: 24rpx;
      color: #8b95a8;
    }
  }

  .user-badge {
    padding: 6rpx 20rpx;
    background: linear-gradient(135deg, #3b82f6, #8b5cf6);
    border-radius: 100rpx;

    .badge-text {
      font-size: 22rpx;
      font-weight: 600;
      color: #fff;
    }
  }
}

/* 状态概览 */
.stats-row {
  display: flex;
  gap: 16rpx;
  padding: 0 32rpx;
  margin-bottom: 32rpx;
}

.stat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 28rpx 0;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 20rpx;

  .stat-num {
    font-size: 44rpx;
    font-weight: 800;
    color: #3b82f6;
  }

  .stat-label {
    font-size: 24rpx;
    color: #8b95a8;
  }
}

/* 菜单 */
.menu-section {
  padding: 0 32rpx;
  margin-bottom: 32rpx;

  .menu-title {
    display: block;
    font-size: 28rpx;
    font-weight: 600;
    color: #8b95a8;
    margin-bottom: 16rpx;
    padding-left: 8rpx;
  }
}

.menu-list {
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 24rpx;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx 24rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);
  transition: background 0.3s ease;

  &:last-child {
    border-bottom: none;
  }

  &:active {
    background: rgba(255, 255, 255, 0.05);
  }

  .menu-icon {
    font-size: 40rpx;
    width: 56rpx;
    text-align: center;
    flex-shrink: 0;
  }

  .menu-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;

    .menu-name {
      font-size: 30rpx;
      font-weight: 600;
      color: #f0f4f8;
    }

    .menu-desc {
      font-size: 24rpx;
      color: #5a6372;
    }
  }

  .menu-arrow {
    font-size: 36rpx;
    color: #5a6372;
    flex-shrink: 0;
  }
}

/* 退出登录 */
.logout-section {
  padding: 0 32rpx;
  margin-top: 20rpx;
}

.logout-btn {
  padding: 28rpx 0;
  background: rgba(255, 77, 79, 0.08);
  border: 1rpx solid rgba(255, 77, 79, 0.15);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;

  &:active {
    background: rgba(255, 77, 79, 0.15);
  }

  .logout-text {
    font-size: 30rpx;
    font-weight: 600;
    color: #ff4d4f;
  }
}
</style>
