<script setup lang="ts">
import { computed, ref } from 'vue'
import { SOFTAP_PROBE_TIMEOUT_MS, SOFTAP_SUBMIT_TIMEOUT_MS } from '@/config/timeouts'
import { t } from '@/i18n'
import { toast } from '@/utils/toast'
import { provisioningContract, softApUrl } from '../provisioning-contract'

// 类型定义
interface WiFiNetwork {
  ssid: string
  rssi: number
  authmode: number
  channel: number
}

// Props
interface Props {
  selectedNetwork: WiFiNetwork | null
  password: string
}

const props = defineProps<Props>()

// 响应式数据
const configuring = ref(false)

// 计算属性
const canSubmit = computed(() => {
  if (!props.selectedNetwork)
    return false
  if (props.selectedNetwork.authmode > 0 && !props.password)
    return false
  return true
})

// ESP32连接检查
async function checkESP32Connection() {
  try {
    const response = await uni.request({
      url: softApUrl(provisioningContract.softApScanPath),
      method: 'GET',
      timeout: SOFTAP_PROBE_TIMEOUT_MS,
    })
    return response.statusCode === 200
  }
  catch {
    return false
  }
}

// 提交配网
async function submitConfig() {
  if (!props.selectedNetwork)
    return

  // 检查ESP32连接
  const connected = await checkESP32Connection()
  if (!connected) {
    toast.error(t('deviceConfig.connectDlcHotspot'))
    return
  }

  configuring.value = true

  try {
    const response = await uni.request({
      url: softApUrl(provisioningContract.softApSubmitPath),
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
      },
      data: {
        ssid: props.selectedNetwork.ssid,
        password: props.selectedNetwork.authmode > 0 ? props.password : '',
        server_host: getEnvBaseUrl().replace(/^https?:\/\//, ''),
        device_secret: provisioningDlcSecret(),
      },
      timeout: SOFTAP_SUBMIT_TIMEOUT_MS,
    })

    if (response.statusCode === 200 && (response.data as any)?.success) {
      toast.success(`${t('deviceConfig.configSuccess')}！${t('deviceConfig.deviceWillConnectTo')} ${props.selectedNetwork.ssid}，${t('deviceConfig.deviceWillRestart')}。${t('deviceConfig.pleaseDisconnectDlcHotspot')}`)
      // 设备退出配网模式
      setTimeout(() => {
        uni.request({
          url: softApUrl(provisioningContract.softApExitPath),
          method: 'POST',
          timeout: SOFTAP_SUBMIT_TIMEOUT_MS,
        })
      }, 1500)
    }
    else {
      const errorMsg = (response.data as any)?.error || t('deviceConfig.configFailed')
      toast.error(errorMsg)
    }
  }
  catch (error) {
    console.error(`${t('deviceConfig.wifiConfigFailed')}:`, error)
    toast.error(`${t('deviceConfig.configFailed')}，${t('deviceConfig.pleaseCheckNetworkConnection')}`)
  }
  finally {
    configuring.value = false
  }
}
</script>

<template>
  <view class="wifi-config">
    <!-- 选中的网络信息 -->
    <view v-if="props.selectedNetwork" class="selected-network">
      <view class="network-info">
        <view class="network-name">
          {{ t('deviceConfig.selectedNetwork') }}: {{ props.selectedNetwork.ssid }}
        </view>
        <view class="network-details">
          <text class="network-signal">
            {{ t('deviceConfig.signal') }}: {{ props.selectedNetwork.rssi }}dBm
          </text>
          <text class="network-security">
            {{ props.selectedNetwork.authmode === 0 ? t('deviceConfig.openNetwork') : t('deviceConfig.encryptedNetwork') }}
          </text>
        </view>
      </view>
    </view>

    <!-- 配网按钮 -->
    <view class="submit-section">
      <wd-button
        type="primary"
        size="large"
        block
        :loading="configuring"
        :disabled="!canSubmit"
        @click="submitConfig"
      >
        {{ configuring ? t('deviceConfig.configuring') : t('deviceConfig.startWifiConfigButton') }}
      </wd-button>
    </view>

    <!-- 使用说明 -->
    <view class="help-section">
      <view class="help-title">
        {{ t('deviceConfig.wifiConfigInstructions') }}
      </view>
      <view class="help-content">
        <text class="help-item">
          1. {{ t('deviceConfig.phoneConnectDlcHotspot') }} (DLC-XXXXXX)
        </text>
        <text class="help-item">
          2. {{ t('deviceConfig.selectTargetWifiNetwork') }}
        </text>
        <text class="help-item">
          3. {{ t('deviceConfig.enterWifiPasswordIfNeeded') }}
        </text>
        <text class="help-item">
          4. {{ t('deviceConfig.clickStartConfigAndWait') }}
        </text>
        <text class="help-tip">
          {{ t('deviceConfig.afterConfigSuccessDeviceWillRestart') }}
        </text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.wifi-config {
  padding: 20rpx 0;
}

.selected-network {
  margin-bottom: 32rpx;
}

.network-info {
  padding: 24rpx;
  background-color: var(--bg2);
  border: 1rpx solid var(--accent);
  border-radius: 16rpx;
}

.network-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8rpx;
}

.network-details {
  display: flex;
  gap: 24rpx;
}

.network-signal,
.network-security {
  font-size: 24rpx;
  color: var(--muted);
}

.submit-section {
  margin-bottom: 32rpx;
}

.help-section {
  padding: 32rpx 24rpx;
  background-color: var(--bg2);
  border-radius: 16rpx;
  border: 1rpx solid var(--border);
}

.help-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 20rpx;
}

.help-content {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.help-item {
  font-size: 24rpx;
  color: var(--muted);
  line-height: 1.5;
}

.help-tip {
  font-size: 24rpx;
  color: var(--accent);
  font-weight: 500;
  margin-top: 8rpx;
}
</style>
