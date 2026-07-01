<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { t } from '@/i18n'
import BlufiConfig from './components/blufi-config.vue'
import UltrasonicConfig from './components/ultrasonic-config.vue'
import WifiConfig from './components/wifi-config.vue'
import WifiSelector from './components/wifi-selector.vue'

// 类型定义
interface WiFiNetwork {
  ssid: string
  rssi: number
  authmode: number
  channel: number
}

// 配网类型
const configType = ref<'ble_blufi' | 'softap_http' | 'wifi' | 'ultrasonic'>('ble_blufi')

// 配网模式选择器状态
const configTypeSelectorShow = ref(false)

// WiFi选择器引用
const wifiSelectorRef = ref<InstanceType<typeof WifiSelector>>()

// 选择的WiFi网络信息
const selectedWifiInfo = ref<{
  network: WiFiNetwork | null
  password: string
}>({
  network: null,
  password: '',
})

// 配网模式选项
const configTypeOptions = [
  {
    name: 'BLE / BluFi',
    value: 'ble_blufi' as const,
  },
  {
    name: 'SoftAP HTTP',
    value: 'softap_http' as const,
  },
  // {
  //   name: t('deviceConfig.ultrasonicConfig'),
  //   value: 'ultrasonic' as const,
  // },
]

// 显示配网模式选择器
function showConfigTypeSelector() {
  configTypeSelectorShow.value = true
}

// 配网模式选择器确认
function onConfigTypeConfirm(item: { name: string, value: 'ble_blufi' | 'softap_http' | 'wifi' | 'ultrasonic' }) {
  configType.value = item.value
  configTypeSelectorShow.value = false
}

// 配网模式选择器取消
function onConfigTypeCancel() {
  configTypeSelectorShow.value = false
}

// WiFi网络选择事件
function onNetworkSelected(network: WiFiNetwork | null, password: string) {
  selectedWifiInfo.value = { network, password }
}

// ESP32连接状态变化事件
function onConnectionStatusChange(_connected: boolean) {
  // no-op
}

// 在组件挂载后设置导航栏标题
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
      <!-- 配网方式选择 -->
      <view class="pb-[20rpx] first:pt-[20rpx]">
        <text class="text-[32rpx] font-bold dc-text">
          {{ t('deviceConfig.configMethod') }}
        </text>
      </view>

      <view class="mb-[24rpx] border rounded-[20rpx] p-[24rpx] dc-card">
        <view class="flex cursor-pointer items-center justify-between border rounded-[12rpx] p-[20rpx] transition-all duration-300 dc-selector" @click="showConfigTypeSelector">
          <text class="text-[28rpx] font-medium dc-text">
            {{ t('deviceConfig.configMethod') }}
          </text>
          <text class="mx-[16rpx] flex-1 text-right text-[26rpx] dc-muted">
            {{ configType === 'ble_blufi' ? 'BLE / BluFi' : configType === 'softap_http' || configType === 'wifi' ? 'SoftAP HTTP' : t('deviceConfig.ultrasonicConfig') }}
          </text>
          <wd-icon name="arrow-right" custom-class="text-[20rpx] dc-dim" />
        </view>
      </view>

      <!-- WiFi网络选择 -->
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

      <!-- 配网操作 -->
      <view v-if="configType === 'ble_blufi' || selectedWifiInfo.network" class="flex-1">
        <!-- WiFi配网组件 -->
        <blufi-config
          v-if="configType === 'ble_blufi'"
          :selected-network="selectedWifiInfo.network"
          :password="selectedWifiInfo.password"
        />

        <wifi-config
          v-else-if="configType === 'softap_http' || configType === 'wifi'"
          :selected-network="selectedWifiInfo.network"
          :password="selectedWifiInfo.password"
        />

        <!-- 超声波配网组件 -->
        <ultrasonic-config
          v-else-if="configType === 'ultrasonic'"
          :selected-network="selectedWifiInfo.network"
          :password="selectedWifiInfo.password"
        />
      </view>
    </view>

    <!-- 配网模式选择器 -->
    <wd-action-sheet
      v-model="configTypeSelectorShow"
      :actions="configTypeOptions.map(item => ({ name: item.name, value: item.value }))"
      @close="onConfigTypeCancel"
      @select="({ item }) => onConfigTypeConfirm(item)"
    />
  </view>
</template>

<route lang="jsonc" type="page">
{
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
