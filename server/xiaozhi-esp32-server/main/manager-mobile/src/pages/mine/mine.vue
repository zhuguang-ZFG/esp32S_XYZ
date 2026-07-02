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
import { ref } from 'vue'
import { t } from '@/i18n'

defineOptions({ name: 'MinePage' })

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

const appVersion = import.meta.env.VITE_APP_VERSION || '1.0.0'

function goSettings() {
  uni.switchTab({ url: '/pages/settings/index' })
}
function goDigitalHuman() {
  uni.showToast({ title: t('nebula.digitalHumanComingSoon'), icon: 'none' })
}
function goVoiceprint() {
  uni.navigateTo({ url: '/pages/voiceprint/index' })
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

    <!-- Feature Menu -->
    <view class="menu-section">
      <text class="menu-title">
        {{ t('mine.featureCenter') }}
      </text>
      <view class="menu-list">
        <view class="menu-item" @click="goDigitalHuman">
          <wd-icon name="user" size="22" color="#2dd4a7" custom-class="menu-icon" />
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
        <view class="menu-item" @click="goVoiceprint">
          <wd-icon name="sound" size="22" color="#2dd4a7" custom-class="menu-icon" />
          <view class="menu-content">
            <text class="menu-name">
              {{ t('mine.voiceprint') }}
            </text>
            <text class="menu-desc">
              {{ t('mine.voiceprintDesc') }}
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
  background: var(--accent);
  border-radius: 24rpx;
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