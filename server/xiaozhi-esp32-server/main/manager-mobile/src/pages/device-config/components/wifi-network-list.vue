<script setup lang="ts">
import { t } from '@/i18n'
import type { WiFiNetwork } from '../composables/useWifiSelector'
import { getSignalStrength, getSignalColor } from '../composables/useWifiSelector'

interface Props {
  visible: boolean
  scanning: boolean
  wifiNetworks: WiFiNetwork[]
}

defineProps<Props>()

const emit = defineEmits<{
  close: []
  scan: []
  select: [network: WiFiNetwork]
}>()
</script>

<template>
  <view v-if="visible" class="network-list-overlay">
    <view class="network-list-container">
      <view class="list-header">
        <text class="list-title">
          {{ t('deviceConfig.selectWifiNetwork') }}
        </text>
        <view class="list-actions">
          <wd-button
            type="primary"
            size="small"
            :loading="scanning"
            @click="emit('scan')"
          >
            {{ scanning ? t('deviceConfig.scanning') : t('deviceConfig.refreshScan') }}
          </wd-button>
          <wd-button
            size="small"
            @click="emit('close')"
          >
            {{ t('deviceConfig.cancel') }}
          </wd-button>
        </view>
      </view>

      <view class="network-list">
        <!-- M38:扫描中给骨架行 + 文案,不再空白 -->
        <view v-if="scanning && wifiNetworks.length === 0" class="scanning-state">
          <text class="scanning-text">
            {{ t('deviceConfig.scanningHint') }}
          </text>
          <view v-for="n in 3" :key="n" class="skeleton skeleton-row" />
        </view>

        <view v-else-if="wifiNetworks.length === 0 && !scanning" class="empty-state">
          <text class="empty-text">
            {{ t('deviceConfig.noWifiNetworks') }}
          </text>
          <text class="empty-tip">
            {{ t('deviceConfig.clickRefreshScan') }}
          </text>
        </view>

        <view v-else class="wifi-list">
          <view
            v-for="network in wifiNetworks"
            :key="network.ssid"
            class="wifi-item"
            @click="emit('select', network)"
          >
            <view class="wifi-info">
              <view class="wifi-name">
                {{ network.ssid }}
              </view>
              <view class="wifi-details">
                <text class="wifi-signal">
                  {{ t('deviceConfig.signal') }}: {{ network.rssi }}dBm
                </text>
                <text class="wifi-channel">
                  {{ t('deviceConfig.channel') }}: {{ network.channel }}
                </text>
              </view>
            </view>
            <view class="wifi-security">
              <text class="security-icon">
                {{ network.authmode === 0 ? t('deviceConfig.open') : t('deviceConfig.encryptedNetwork') }}
              </text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.network-list-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}

.network-list-container {
  width: 100%;
  max-height: 70vh;
  background-color: var(--surface);
  border-radius: 20rpx 20rpx 0 0;
  padding: 32rpx;
  box-sizing: border-box;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid var(--border);
}

.list-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text);
}

.list-actions {
  display: flex;
  gap: 16rpx;
}

.network-list {
  max-height: 50vh;
  overflow-y: auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 20rpx;
  background-color: var(--bg2);
  border-radius: 16rpx;
  border: 1rpx solid var(--border);
}

.empty-text {
  font-size: 32rpx;
  color: var(--muted);
  margin-bottom: 16rpx;
}

.empty-tip {
  font-size: 24rpx;
  color: var(--dim);
}

.scanning-state {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.scanning-text {
  font-size: 24rpx;
  color: var(--muted);
  margin-bottom: 8rpx;
}

.skeleton-row {
  height: 96rpx;
  border-radius: 16rpx;
}

.wifi-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.wifi-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 24rpx;
  background-color: var(--bg2);
  border: 2rpx solid var(--border);
  border-radius: 16rpx;
  transition: all 0.3s ease;
  cursor: pointer;
}

.wifi-item:active {
  transform: scale(0.98);
  background-color: var(--bg2);
  border-color: var(--accent);
}

.wifi-info {
  flex: 1;
}

.wifi-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8rpx;
}

.wifi-details {
  display: flex;
  gap: 24rpx;
}

.wifi-signal,
.wifi-channel {
  font-size: 24rpx;
  color: var(--muted);
}

.security-icon {
  font-size: 24rpx;
  color: var(--muted);
  margin-left: 20rpx;
}
</style>