<route lang="jsonc" type="page">
{
  "layout": "tabbar",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "LiMa 星云"
  }
}
</route>

<script lang="ts" setup>
import { onShow, ref } from 'vue'
import { t } from '@/i18n'
import { v2GetDevices } from '@/api/v2'
import type { V2DeviceInfo } from '@/api/v2/types'

defineOptions({ name: 'NebulaCenter' })

const safeAreaTop = ref(0)
const devices = ref<V2DeviceInfo[]>([])
const loading = ref(false)

// 获取安全区域顶部
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

onShow(() => { loadDevices() })

async function loadDevices() {
  loading.value = true
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

// 核心能力跳转
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
  uni.showToast({ title: '数字人即将上线', icon: 'none' })
}

// 设备相关
function goDevices() {
  uni.switchTab({ url: '/pages/v2/device-list/index' })
}
function goDeviceDetail(deviceId: string) {
  uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${deviceId}` })
}
function goAddDevice() {
  uni.switchTab({ url: '/pages/v2/device-list/index' })
}
function goConfig() {
  uni.switchTab({ url: '/pages/device-config/index' })
}
function goSettings() {
  uni.switchTab({ url: '/pages/settings/index' })
}
</script>

<template>
  <view class="nebula-center" :style="{ paddingTop: safeAreaTop + 'px' }">
    <!-- 深空横幅 -->
    <view class="nebula-hero">
      <view class="hero-stars">
        <view class="star s1" />
        <view class="star s2" />
        <view class="star s3" />
        <view class="star s4" />
        <view class="star s5" />
      </view>
      <view class="hero-glow" />
      <view class="hero-content">
        <text class="hero-title">LiMa 星云</text>
        <text class="hero-subtitle">AI 智能设备星云系统</text>
      </view>
    </view>

    <!-- 核心能力 -->
    <view class="section">
      <view class="section-header">
        <text class="section-title">✨ 核心能力</text>
      </view>
      <view class="cap-grid">
        <view class="cap-card" @click="goChat">
          <view class="cap-icon chat">💬</view>
          <text class="cap-name">AI 对话</text>
          <text class="cap-desc">实时智能交流</text>
        </view>
        <view class="cap-card" @click="goDraw">
          <view class="cap-icon draw">🎨</view>
          <text class="cap-name">AI 绘图</text>
          <text class="cap-desc">文字驱动创作</text>
        </view>
        <view class="cap-card" @click="goWrite">
          <view class="cap-icon write">✍️</view>
          <text class="cap-name">AI 写字</text>
          <text class="cap-desc">文案驱动书写</text>
        </view>
        <view class="cap-card" @click="goDigitalHuman">
          <view class="cap-icon human">👤</view>
          <text class="cap-name">数字人</text>
          <text class="cap-desc">2D 语音交互</text>
        </view>
      </view>
    </view>

    <!-- 我的设备 -->
    <view class="section">
      <view class="section-header">
        <text class="section-title">🔧 我的设备</text>
        <text class="section-more" @click="goDevices">查看全部 ></text>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
      <view v-else-if="!devices.length" class="empty-tip">
        <text>暂无设备</text>
        <text class="empty-sub">点击添加设备开始使用</text>
      </view>
      <view v-else class="device-list">
        <view
          v-for="d in devices" :key="d.deviceId"
          class="device-card"
          @click="goDeviceDetail(d.deviceId)"
        >
          <view class="device-icon">{{ d.model?.includes('draw') ? '🎨' : d.model?.includes('write') ? '📝' : '🤖' }}</view>
          <view class="device-info">
            <text class="device-name">{{ d.model || '智能设备' }}</text>
            <text class="device-id">{{ d.deviceId }}</text>
          </view>
          <view class="device-meta">
            <text :class="['device-status', d.status === 'online' ? 'online' : 'offline']">
              {{ d.status === 'online' ? '● 在线' : '● 离线' }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <!-- 快捷操作 -->
    <view class="section">
      <view class="section-header">
        <text class="section-title">⚡ 快捷操作</text>
      </view>
      <view class="quick-actions">
        <view class="quick-btn" @click="goAddDevice">
          <text class="quick-icon">➕</text>
          <text class="quick-text">添加设备</text>
        </view>
        <view class="quick-btn" @click="goConfig">
          <text class="quick-icon">📡</text>
          <text class="quick-text">配网</text>
        </view>
        <view class="quick-btn" @click="goSettings">
          <text class="quick-icon">⚙️</text>
          <text class="quick-text">系统设置</text>
        </view>
      </view>
    </view>

    <!-- 底部留白 -->
    <view style="height: 40rpx;" />
  </view>
</template>

<style lang="scss" scoped>
.nebula-center {
  min-height: 100vh;
  background: linear-gradient(180deg, #07070f 0%, #0a0a14 40%, #0d0d1a 100%);
  position: relative;

  &::before {
    content: '';
    position: fixed;
    inset: 0;
    pointer-events: none;
    z-index: 0;
    background:
      radial-gradient(ellipse 600rpx 400rpx at 20% 10%, rgba(59, 130, 246, 0.04) 0%, transparent 70%),
      radial-gradient(ellipse 500rpx 350rpx at 80% 40%, rgba(139, 92, 246, 0.03) 0%, transparent 70%),
      radial-gradient(ellipse 400rpx 300rpx at 50% 70%, rgba(6, 182, 212, 0.02) 0%, transparent 70%);
  }
}

// ─── 深空横幅 ───
.nebula-hero {
  position: relative;
  padding: 60rpx 40rpx 80rpx;
  overflow: hidden;
  z-index: 1;

  .hero-stars {
    position: absolute;
    inset: 0;
    pointer-events: none;

    .star {
      position: absolute;
      width: 4rpx;
      height: 4rpx;
      background: #60a5fa;
      border-radius: 50%;
      box-shadow: 0 0 16rpx 2rpx rgba(96, 165, 250, 0.5);
      animation: twinkle 2.5s ease-in-out infinite;

      &.s1 { top: 20%; right: 15%; animation-delay: 0s; }
      &.s2 { top: 35%; right: 30%; animation-delay: -0.5s; background: #8b5cf6; box-shadow: 0 0 16rpx 2rpx rgba(139, 92, 246, 0.5); }
      &.s3 { top: 15%; right: 45%; animation-delay: -1s; background: #06b6d4; box-shadow: 0 0 16rpx 2rpx rgba(6, 182, 212, 0.5); }
      &.s4 { top: 50%; right: 10%; animation-delay: -1.5s; }
      &.s5 { top: 40%; right: 50%; animation-delay: -2s; background: #f59e0b; box-shadow: 0 0 16rpx 2rpx rgba(245, 158, 11, 0.4); }
    }
  }

  .hero-glow {
    position: absolute;
    top: -100rpx;
    right: -100rpx;
    width: 400rpx;
    height: 400rpx;
    background: radial-gradient(circle, rgba(59, 130, 246, 0.08) 0%, transparent 70%);
    border-radius: 50%;
    pointer-events: none;
  }

  .hero-content {
    position: relative;
    z-index: 2;

    .hero-title {
      display: block;
      font-size: 56rpx;
      font-weight: 800;
      color: #f0f4f8;
      letter-spacing: 4rpx;
      text-shadow: 0 2rpx 12rpx rgba(59, 130, 246, 0.3);
      margin-bottom: 12rpx;
    }

    .hero-subtitle {
      display: block;
      font-size: 28rpx;
      color: #8b95a8;
      font-weight: 400;
    }
  }
}

@keyframes twinkle {
  0%, 100% { opacity: 0.3; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.8); }
}

// ─── 通用区域 ───
.section {
  position: relative;
  z-index: 1;
  padding: 0 32rpx;
  margin-bottom: 40rpx;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;

  .section-title {
    font-size: 32rpx;
    font-weight: 700;
    color: #f0f4f8;
  }

  .section-more {
    font-size: 26rpx;
    color: #3b82f6;
  }
}

// ─── 核心能力网格 ───
.cap-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
}

.cap-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 24rpx;
  padding: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12rpx;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: 24rpx;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.06) 0%, transparent 60%, rgba(139, 92, 246, 0.04) 100%);
    pointer-events: none;
  }

  &:active {
    transform: scale(0.97);
    background: rgba(255, 255, 255, 0.06);
  }

  .cap-icon {
    width: 72rpx;
    height: 72rpx;
    border-radius: 20rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 36rpx;
    position: relative;
    z-index: 1;

    &.chat { background: rgba(59, 130, 246, 0.15); }
    &.draw { background: rgba(139, 92, 246, 0.15); }
    &.write { background: rgba(6, 182, 212, 0.15); }
    &.human { background: rgba(245, 158, 11, 0.15); }
  }

  .cap-name {
    font-size: 28rpx;
    font-weight: 600;
    color: #f0f4f8;
    position: relative;
    z-index: 1;
  }

  .cap-desc {
    font-size: 22rpx;
    color: #5a6372;
    position: relative;
    z-index: 1;
  }
}

// ─── 设备列表 ───
.loading-tip, .empty-tip {
  text-align: center;
  padding: 40rpx 0;
  color: #5a6372;
  font-size: 28rpx;
}
.empty-tip {
  display: flex;
  flex-direction: column;
  gap: 8rpx;

  .empty-sub {
    font-size: 24rpx;
    color: #3a4252;
  }
}

.device-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.device-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  transition: all 0.3s ease;

  &:active {
    background: rgba(255, 255, 255, 0.06);
  }

  .device-icon {
    width: 64rpx;
    height: 64rpx;
    border-radius: 16rpx;
    background: rgba(59, 130, 246, 0.1);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 32rpx;
    flex-shrink: 0;
  }

  .device-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;

    .device-name {
      font-size: 30rpx;
      font-weight: 600;
      color: #f0f4f8;
    }

    .device-id {
      font-size: 22rpx;
      color: #5a6372;
    }
  }

  .device-meta {
    flex-shrink: 0;

    .device-status {
      font-size: 24rpx;
      font-weight: 500;

      &.online { color: #34d399; }
      &.offline { color: #5a6372; }
    }
  }
}

// ─── 快捷操作 ───
.quick-actions {
  display: flex;
  gap: 16rpx;
}

.quick-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 20rpx;
  padding: 28rpx 0;
  transition: all 0.3s ease;

  &:active {
    background: rgba(255, 255, 255, 0.06);
  }

  .quick-icon {
    font-size: 40rpx;
  }

  .quick-text {
    font-size: 24rpx;
    color: #8b95a8;
  }
}
</style>
