<script setup lang="ts">
import { computed, ref } from 'vue'
import { SOFTAP_PROBE_TIMEOUT_MS, SOFTAP_SUBMIT_TIMEOUT_MS } from '@/config/timeouts'
import { t } from '@/i18n'
import { toast } from '@/utils/toast'
import { provisioningContract, softApUrl } from '../provisioning-contract'
import { getEnvBaseUrl } from '@/utils'
import { provisioningDlcSecret } from '@/utils/dlcToken'

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
// M39:配网结果页内条(成功 checklist / 失败原因+重试),toast 只做补充
const configResult = ref<'' | 'success' | 'fail'>('')
const configErrorMsg = ref('')
// S3: 未选网络时不常驻提示；用户点过提交区 / 已选网后才显示原因
const attemptedSubmit = ref(false)

// 计算属性
const canSubmit = computed(() => {
  if (!props.selectedNetwork)
    return false
  if (props.selectedNetwork.authmode > 0 && !props.password)
    return false
  return true
})

// M40:disabled 时明说原因
const disabledReason = computed(() => {
  if (!props.selectedNetwork)
    return t('deviceConfig.selectNetworkFirst')
  if (props.selectedNetwork.authmode > 0 && !props.password)
    return t('deviceConfig.passwordRequired')
  return ''
})

const showDisabledReason = computed(() => {
  if (!disabledReason.value)
    return false
  // 已选网络但缺密码 → 始终提示；未选网络 → 仅尝试提交后提示
  if (props.selectedNetwork)
    return true
  return attemptedSubmit.value
})

function onSubmitClick() {
  // S3: 始终可点；无效时只亮原因，不真正提交（disabled 按钮在部分端点不透事件）
  attemptedSubmit.value = true
  if (!canSubmit.value || configuring.value)
    return
  submitConfig()
}

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
// ⚠️ 安全提示：SoftAP 配网阶段通过 HTTP（非 HTTPS）向 ESP32 的 192.168.4.1 发送
// ssid / password / device_secret。这是 SoftAP 场景的行业常见做法，因 ESP32 SoftAP
// 无 TLS 证书能力。风险限于局域网单跳链路（手机<->ESP32），不会经过公网。
// 建议：若设备固件支持，优先启用 BluFi 蓝牙配网或 HTTPS 配网通道。
async function submitConfig() {
  if (!props.selectedNetwork)
    return

  configResult.value = ''
  configErrorMsg.value = ''

  // 检查ESP32连接
  const connected = await checkESP32Connection()
  if (!connected) {
    configResult.value = 'fail'
    configErrorMsg.value = t('deviceConfig.connectDlcHotspot')
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
      // M39:成功详情拆进页内 checklist,toast 只报一句
      configResult.value = 'success'
      toast.success(t('deviceConfig.configSuccess'))
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
      configResult.value = 'fail'
      configErrorMsg.value = errorMsg
      toast.error(errorMsg)
    }
  }
  catch (error) {
    console.error(`${t('deviceConfig.wifiConfigFailed')}:`, error)
    configResult.value = 'fail'
    configErrorMsg.value = `${t('deviceConfig.configFailed')}，${t('deviceConfig.pleaseCheckNetworkConnection')}`
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

    <!-- 配网按钮：S3 不用 canSubmit 做 disabled（点不透），无效时点一下只显示原因 -->
    <view class="submit-section">
      <wd-button
        type="primary"
        size="large"
        block
        :loading="configuring"
        :disabled="configuring"
        @click="onSubmitClick"
      >
        {{ configuring ? t('deviceConfig.configuring') : t('deviceConfig.startWifiConfigButton') }}
      </wd-button>
      <!-- M40/S3: 未选网时不常驻；点过或已选网后才显示 -->
      <text v-if="showDisabledReason" class="disabled-reason">
        {{ disabledReason }}
      </text>
    </view>

    <!-- M39:结果条(成功 checklist / 失败原因+重试) -->
    <view v-if="configResult === 'success'" class="result-banner result-success">
      <view class="result-title">
        {{ t('deviceConfig.configSuccess') }}
      </view>
      <view class="result-checklist">
        <text class="result-item">
          ✓ {{ t('deviceConfig.deviceWillConnectTo') }} {{ props.selectedNetwork?.ssid }}
        </text>
        <text class="result-item">
          ✓ {{ t('deviceConfig.deviceWillRestart') }}
        </text>
        <text class="result-item">
          ✓ {{ t('deviceConfig.pleaseDisconnectDlcHotspot') }}
        </text>
      </view>
    </view>
    <view v-else-if="configResult === 'fail'" class="result-banner result-fail">
      <view class="result-title">
        {{ t('deviceConfig.configFailed') }}
      </view>
      <text class="result-item">
        {{ configErrorMsg }}
      </text>
      <wd-button type="error" plain size="small" custom-class="!mt-[16rpx]" :disabled="configuring" @click="submitConfig">
        {{ t('deviceConfig.retry') }}
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

.disabled-reason {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--dim);
  text-align: center;
}

.result-banner {
  margin-bottom: 32rpx;
  padding: 24rpx;
  border-radius: 16rpx;
}

.result-success {
  background-color: var(--green-g);
  border: 1rpx solid var(--green);
}

.result-success .result-title {
  color: var(--green);
}

.result-fail {
  background-color: var(--danger-g);
  border: 1rpx solid var(--danger);
}

.result-fail .result-title {
  color: var(--danger);
}

.result-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.result-checklist {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.result-item {
  display: block;
  font-size: 24rpx;
  color: var(--muted);
  line-height: 1.5;
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
