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
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { t } from '@/i18n'
import { v2GetDevices } from '@/api/v2'
import type { V2DeviceInfo } from '@/api/v2/types'

defineOptions({ name: 'NebulaCenter' })

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0
const devices = ref<V2DeviceInfo[]>([])
const loading = ref(false)

onShow(() => { loadDevices() })

async function loadDevices() {
  loading.value = true
  try { const res = await v2GetDevices(); devices.value = res.rows || [] }
  catch (e) { console.error(e) }
  finally { loading.value = false }
}

function goChat() { uni.navigateTo({ url: '/pages/chat/chat' }) }
function goDraw() { uni.navigateTo({ url: '/pages/create/create?mode=draw' }) }
function goWrite() { uni.navigateTo({ url: '/pages/create/create?mode=write' }) }
function goDigitalHuman() { uni.showToast({ title: t('nebula.digitalHumanComingSoon'), icon: 'none' }) }
function goDevices() { uni.switchTab({ url: '/pages/v2/device-list/index' }) }
function goDeviceDetail(id: string) { uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${id}` }) }
function goConfig() { uni.switchTab({ url: '/pages/device-config/index' }) }
function goSettings() { uni.switchTab({ url: '/pages/settings/index' }) }

function deviceIcon(model?: string) {
  if (model?.includes('draw')) return 'photo'
  if (model?.includes('write')) return 'edit-2'
  return 'phone'
}
</script>

<template>
  <view class="nebula-center" :style="{ paddingTop: safeAreaTop + 'px' }">
    <view class="hero-banner">
      <view class="hero-content">
        <text class="hero-title">{{ t('nebula.title') }}</text>
        <text class="hero-subtitle">{{ t('nebula.subtitle') }}</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">{{ t('nebula.coreCapabilities') }}</text>
      <view class="cap-grid">
        <view class="cap-card" @click="goChat">
          <view class="cap-icon blue"><wd-icon name="chat" size="24" color="#336cff" /></view>
          <text class="cap-name">{{ t('nebula.aiChat') }}</text>
          <text class="cap-desc">{{ t('nebula.aiChatDesc') }}</text>
        </view>
        <view class="cap-card" @click="goDraw">
          <view class="cap-icon purple"><wd-icon name="photo" size="24" color="#8b5cf6" /></view>
          <text class="cap-name">{{ t('nebula.aiDraw') }}</text>
          <text class="cap-desc">{{ t('nebula.aiDrawDesc') }}</text>
        </view>
        <view class="cap-card" @click="goWrite">
          <view class="cap-icon cyan"><wd-icon name="edit-2" size="24" color="#06b6d4" /></view>
          <text class="cap-name">{{ t('nebula.aiWrite') }}</text>
          <text class="cap-desc">{{ t('nebula.aiWriteDesc') }}</text>
        </view>
        <view class="cap-card" @click="goDigitalHuman">
          <view class="cap-icon amber"><wd-icon name="user" size="24" color="#f59e0b" /></view>
          <text class="cap-name">{{ t('nebula.digitalHuman') }}</text>
          <text class="cap-desc">{{ t('nebula.digitalHumanDesc') }}</text>
        </view>
      </view>
    </view>

    <view class="section">
      <view class="section-header">
        <text class="section-title">{{ t('nebula.myDevices') }}</text>
        <text class="section-more" @click="goDevices">{{ t('nebula.viewAll') }} ></text>
      </view>
      <view v-if="loading" class="empty-tip">{{ t('nebula.loading') }}</view>
      <view v-else-if="!devices.length" class="empty-tip">
        <text>{{ t('nebula.noDevices') }}</text>
        <text class="empty-sub">{{ t('nebula.addDeviceHint') }}</text>
      </view>
      <view v-else class="device-list">
        <view v-for="d in devices" :key="d.deviceId" class="device-card" @click="goDeviceDetail(d.deviceId)">
          <view class="device-icon-wrap"><wd-icon :name="deviceIcon(d.model)" size="22" color="#336cff" /></view>
          <view class="device-info">
            <text class="device-name">{{ d.model || t('nebula.smartDevice') }}</text>
            <text class="device-id">{{ d.deviceId }}</text>
          </view>
          <text :class="['device-status', d.status === 'online' ? 'online' : 'offline']">●</text>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="section-title">{{ t('nebula.quickActions') }}</text>
      <view class="quick-actions">
        <view class="quick-btn" @click="goDevices">
          <wd-icon name="add-circle" size="24" color="#336cff" />
          <text class="quick-text">{{ t('nebula.addDevice') }}</text>
        </view>
        <view class="quick-btn" @click="goConfig">
          <wd-icon name="wifi" size="24" color="#336cff" />
          <text class="quick-text">{{ t('nebula.config') }}</text>
        </view>
        <view class="quick-btn" @click="goSettings">
          <wd-icon name="setting" size="24" color="#336cff" />
          <text class="quick-text">{{ t('nebula.systemSettings') }}</text>
        </view>
      </view>
    </view>

    <view style="height: env(safe-area-inset-bottom);" />
  </view>
</template>

<style lang="scss" scoped>
.nebula-center { min-height: 100vh; background: #f5f5f7; }
.hero-banner {
  background: linear-gradient(135deg, #336cff 0%, #5b8def 100%);
  padding: 48rpx 32rpx 60rpx; border-radius: 0 0 32rpx 32rpx; margin-bottom: 24rpx;
}
.hero-title { display: block; font-size: 48rpx; font-weight: 800; color: #fff; letter-spacing: 4rpx; margin-bottom: 8rpx; }
.hero-subtitle { display: block; font-size: 26rpx; color: rgba(255,255,255,0.8); }
.section { padding: 0 24rpx; margin-bottom: 32rpx; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.section-title { font-size: 30rpx; font-weight: 700; color: #1d1d1f; margin-bottom: 16rpx; }
.section-more { font-size: 26rpx; color: #336cff; }
.cap-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; }
.cap-card {
  background: #fff; border-radius: 20rpx; padding: 24rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
  display: flex; flex-direction: column; gap: 10rpx; transition: all 0.15s;
  &:active { transform: scale(0.97); }
}
.cap-icon {
  width: 56rpx; height: 56rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center;
  &.blue { background: rgba(51,108,255,0.1); }
  &.purple { background: rgba(139,92,246,0.1); }
  &.cyan { background: rgba(6,182,212,0.1); }
  &.amber { background: rgba(245,158,11,0.1); }
}
.cap-name { font-size: 28rpx; font-weight: 600; color: #1d1d1f; }
.cap-desc { font-size: 22rpx; color: #9d9ea3; }
.empty-tip { text-align: center; padding: 32rpx 0; color: #9d9ea3; font-size: 28rpx; .empty-sub { font-size: 24rpx; color: #c7c7cc; } }
.device-list { display: flex; flex-direction: column; gap: 12rpx; }
.device-card {
  display: flex; align-items: center; gap: 16rpx; background: #fff; border-radius: 16rpx;
  padding: 20rpx 24rpx; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
  &:active { background: #f9f9fb; }
}
.device-icon-wrap {
  width: 56rpx; height: 56rpx; border-radius: 14rpx; background: rgba(51,108,255,0.08);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.device-info { flex: 1; display: flex; flex-direction: column; gap: 2rpx; }
.device-name { font-size: 28rpx; font-weight: 600; color: #1d1d1f; }
.device-id { font-size: 22rpx; color: #9d9ea3; }
.device-status { font-size: 24rpx; flex-shrink: 0; &.online { color: #07c160; } &.offline { color: #c7c7cc; } }
.quick-actions { display: flex; gap: 16rpx; }
.quick-btn {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 10rpx;
  background: #fff; border-radius: 16rpx; padding: 24rpx 0; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
  &:active { background: #f9f9fb; }
}
.quick-text { font-size: 24rpx; color: #65686f; }
</style>
