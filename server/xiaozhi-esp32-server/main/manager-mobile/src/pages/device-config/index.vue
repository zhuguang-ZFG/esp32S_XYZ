<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { t } from '@/i18n'
import WifiConfig from './components/wifi-config.vue'
import WifiSelector from './components/wifi-selector.vue'

interface WiFiNetwork {
  ssid: string
  rssi: number
  authmode: number
  channel: number
}

const wifiSelectorRef = ref<InstanceType<typeof WifiSelector>>()

const selectedWifiInfo = ref<{
  network: WiFiNetwork | null
  password: string
}>({
  network: null,
  password: '',
})

function onNetworkSelected(network: WiFiNetwork | null, password: string) {
  selectedWifiInfo.value = { network, password }
}

function onConnectionStatusChange(_connected: boolean) {
  // no-op
}

function openPrivacyPermissions() {
  uni.navigateTo({ url: '/pages/settings/privacy-permissions' })
}

onMounted(() => {
  uni.setNavigationBarTitle({
    title: t('deviceConfig.pageTitle'),
  })
})
</script>

<template>
  <view class="min-h-screen page-enter dc-page">
    <wd-navbar
      :title="t('deviceConfig.pageTitle')" safe-area-inset-top
      custom-class="dc-navbar"
      title-class="dc-navbar-title"
    />

    <view class="box-border px-[20rpx]">
      <view class="mb-[24rpx] mt-[20rpx] border rounded-[16rpx] p-[20rpx] dc-permission-card">
        <view class="flex items-center justify-between gap-[20rpx]">
          <view class="flex-1">
            <text class="block text-[26rpx] font-medium dc-text">
              {{ t('deviceConfig.permissionNotice') }}
            </text>
            <text class="mt-[6rpx] block text-[22rpx] leading-[1.5] dc-dim">
              {{ t('deviceConfig.permissionNoticeSub') }}
            </text>
          </view>
          <wd-button type="text" size="small" @click="openPrivacyPermissions">
            {{ t('deviceConfig.permissionSettings') }}
          </wd-button>
        </view>
      </view>

      <view class="pb-[20rpx] first:pt-[20rpx]">
        <text class="text-[32rpx] font-bold dc-text">
          {{ t('deviceConfig.configMethod') }}
        </text>
      </view>

      <view class="mb-[24rpx] border rounded-[20rpx] p-[24rpx] dc-card">
        <view class="flex items-center justify-between border rounded-[12rpx] p-[20rpx] dc-selector">
          <text class="text-[28rpx] font-medium dc-text">
            {{ t('deviceConfig.configMethod') }}
          </text>
          <text class="mx-[16rpx] flex-1 text-right text-[26rpx] dc-muted">
            SoftAP HTTP
          </text>
        </view>
      </view>

      <view class="pb-[20rpx]">
        <text class="text-[32rpx] font-bold dc-text">
          {{ t('deviceConfig.networkConfig') }}
        </text>
      </view>

      <view class="mb-[24rpx] border rounded-[20rpx] p-[24rpx] dc-card">
        <wifi-selector
          ref="wifiSelectorRef"
          @network-selected="onNetworkSelected"
          @connection-status="onConnectionStatusChange"
        />
      </view>

      <view v-if="selectedWifiInfo.network" class="flex-1">
        <wifi-config
          :selected-network="selectedWifiInfo.network"
          :password="selectedWifiInfo.password"
        />
      </view>
    </view>
  </view>
</template>

<route lang="jsonc" type="page">
{
  "needLogin": true,
  "style": {
    "navigationBarTitleText": "设备配置",
    "navigationStyle": "custom"
  }
}
</route>

<style lang="scss" scoped>
.dc-page {
  background: var(--bg);
}
.dc-navbar {
  background: var(--bg) !important;
}
.dc-navbar-title {
  color: var(--text) !important;
}
.dc-text {
  color: var(--text);
}
.dc-muted {
  color: var(--muted);
}
.dc-dim {
  color: var(--dim);
}
.dc-card {
  background: var(--surface);
  border-color: var(--border);
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.2);
}
.dc-selector {
  background: var(--bg2);
  border-color: var(--border);
}
.dc-permission-card {
  background: var(--accent-g);
  border-color: rgba(45, 212, 167, 0.12);
}
</style>
