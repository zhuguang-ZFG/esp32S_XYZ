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
function onConnectionStatusChange(connected: boolean) {
  console.log('ESP32连接状态:', connected)
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
  <view class="min-h-screen" style="background: #07070f;">
    <wd-navbar
      :title="t('deviceConfig.pageTitle')" safe-area-inset-top
      custom-class="!bg-[#07070f]"
      title-class="!text-[#f0f4f8]"
    />

    <view class="box-border px-[20rpx]">
      <view class="mb-[24rpx] mt-[20rpx] border rounded-[16rpx] p-[20rpx]" style="background: rgba(59,130,246,0.06); border-color: rgba(59,130,246,0.12);">
        <view class="flex items-center justify-between gap-[20rpx]">
          <view class="flex-1">
            <text class="block text-[26rpx] text-[#f0f4f8] font-medium">
              {{ t('deviceConfig.permissionNotice') }}
            </text>
            <text class="mt-[6rpx] block text-[22rpx] text-[#5a6372] leading-[1.5]">
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
        <text class="text-[32rpx] text-[#f0f4f8] font-bold">
          {{ t('deviceConfig.configMethod') }}
        </text>
      </view>

      <view class="mb-[24rpx] border rounded-[20rpx] p-[24rpx]" style="background: rgba(255,255,255,0.03); border-color: rgba(255,255,255,0.04); box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.2);">
        <view class="flex cursor-pointer items-center justify-between border rounded-[12rpx] p-[20rpx] transition-all duration-300" style="background: #0a0a14; border-color: rgba(255,255,255,0.04);" @click="showConfigTypeSelector">
          <text class="text-[28rpx] text-[#f0f4f8] font-medium">
            {{ t('deviceConfig.configMethod') }}
          </text>
          <text class="mx-[16rpx] flex-1 text-right text-[26rpx] text-[#8b95a8]">
            {{ configType === 'ble_blufi' ? 'BLE / BluFi' : configType === 'softap_http' || configType === 'wifi' ? 'SoftAP HTTP' : t('deviceConfig.ultrasonicConfig') }}
          </text>
          <wd-icon name="arrow-right" custom-class="text-[20rpx] text-[#5a6372]" />
        </view>
      </view>

      <!-- WiFi网络选择 -->
      <view class="pb-[20rpx]">
        <text class="text-[32rpx] text-[#f0f4f8] font-bold">
          {{ t('deviceConfig.networkConfig') }}
        </text>
      </view>

      <view class="mb-[24rpx] border rounded-[20rpx] p-[24rpx]" style="background: rgba(255,255,255,0.03); border-color: rgba(255,255,255,0.04); box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.2);">
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
