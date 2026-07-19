<script setup lang="ts">
import { t } from '@/i18n'
import { useWifiSelector } from '../composables/useWifiSelector'
import WifiConnectionStatus from './wifi-connection-status.vue'
import WifiNetworkList from './wifi-network-list.vue'

interface Props {
  autoConnect?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  autoConnect: true,
})

const emit = defineEmits<{
  'network-selected': [network: any | null, password: string]
  'connection-status': [connected: boolean]
}>()

const {
  isConnectedToESP32,
  checkingConnection,
  scanning,
  wifiNetworks,
  selectedNetwork,
  password,
  selectorExpanded,
  networkDisplayText,
  checkESP32Connection,
  scanWifi,
  showNetworkSelector,
  selectNetwork,
  onPasswordChange,
  getSelectedNetworkInfo,
  reset,
} = useWifiSelector(props.autoConnect, emit)

defineExpose({
  checkESP32Connection,
  scanWifi,
  getSelectedNetworkInfo,
  reset,
})
</script>

<template>
  <view class="wifi-selector">
    <WifiConnectionStatus
      :auto-connect="props.autoConnect"
      :is-connected-to-esp32="isConnectedToESP32"
      :checking-connection="checkingConnection"
      @recheck="checkESP32Connection"
    />

    <view class="network-selector">
      <view class="selector-item" @click="showNetworkSelector">
        <text class="selector-label">
          {{ t('deviceConfig.wifiNetwork') }}
        </text>
        <text class="selector-value">
          {{ networkDisplayText }}
        </text>
        <wd-icon name="arrow-right" custom-class="arrow-icon" />
      </view>
    </view>

    <WifiNetworkList
      :visible="selectorExpanded"
      :scanning="scanning"
      :wifi-networks="wifiNetworks"
      @close="selectorExpanded = false"
      @scan="scanWifi"
      @select="selectNetwork"
    />

    <view v-if="selectedNetwork && selectedNetwork.authmode > 0" class="password-section">
      <view class="password-item">
        <text class="password-label">
          {{ t('deviceConfig.wifiPassword') }}
        </text>
        <wd-input
          v-model="password"
          :placeholder="t('deviceConfig.enterWifiPassword')"
          show-password
          clearable
          @input="onPasswordChange"
        />
      </view>
    </view>
  </view>
</template>

<style scoped>
.wifi-selector {
  width: 100%;
}

.network-selector {
  margin-bottom: 24rpx;
}

.selector-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx;
  background: var(--bg2);
  border-radius: 12rpx;
  border: 1rpx solid var(--border);
  cursor: pointer;
  transition: all 0.3s ease;
}

.selector-item:active {
  background: var(--bg2);
  border-color: var(--accent);
}

.selector-label {
  font-size: 28rpx;
  color: var(--text);
  font-weight: 500;
}

.selector-value {
  flex: 1;
  text-align: right;
  font-size: 26rpx;
  color: var(--muted);
  margin: 0 16rpx;
}

:deep(.arrow-icon) {
  font-size: 20rpx;
  color: #9d9ea3;
}

.password-section {
  margin-top: 24rpx;
}

.password-item {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.password-label {
  font-size: 28rpx;
  color: var(--text);
  font-weight: 500;
}
</style>