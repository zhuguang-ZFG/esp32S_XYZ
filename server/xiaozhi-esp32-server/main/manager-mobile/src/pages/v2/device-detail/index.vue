<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { t } from '@/i18n'
import { toast } from '@/utils/toast'
import { buildEdgeAClientWsUrl } from '@/utils'
import { v2GetDeviceInfo, v2SubmitTask } from '@/api/v2'
import type { V2DeviceInfo } from '@/api/v2/types'

defineOptions({ name: 'V2DeviceDetail' })

const deviceId = ref('')
const deviceInfo = ref<V2DeviceInfo | null>(null)
const homeLoading = ref(false)
const wsConnected = ref(false)
const logLines = ref<string[]>([])
const latestStatus = ref('—')
let socketTask: UniApp.SocketTask | null = null

const MAX_LOG_LINES = 25

onLoad((options: any) => {
  deviceId.value = options?.deviceId || ''
})

onMounted(async () => {
  if (!deviceId.value) return
  try {
    deviceInfo.value = await v2GetDeviceInfo(deviceId.value)
  }
  catch (e) {
    console.error('get device info failed', e)
  }
  connectWs()
})

onUnmounted(() => {
  disconnectWs()
})

function connectWs() {
  const url = buildEdgeAClientWsUrl()
  const token = uni.getStorageSync('token') || ''
  appendLog(`connecting to ${url}`)

  socketTask = uni.connectSocket({
    url,
    header: { Authorization: `Bearer ${token}` },
    success: () => appendLog('socket created'),
    fail: (e: any) => appendLog(`socket fail: ${JSON.stringify(e)}`),
  })

  socketTask.onOpen(() => {
    wsConnected.value = true
    appendLog('connected, sending auth...')
    socketTask?.send({
      data: JSON.stringify({ op: 'auth', token }),
      success: () => appendLog('auth sent'),
    })
  })

  socketTask.onMessage(({ data }) => {
    try {
      const msg = typeof data === 'string' ? JSON.parse(data) : data
      if (msg.type === 'authed') {
        appendLog('authed')
        // 订阅设备事件
        socketTask?.send({
          data: JSON.stringify({ op: 'subscribe_device', device_id: deviceId.value }),
        })
      }
      else if (msg.type === 'subscribed') {
        appendLog(`subscribed to ${msg.topic}`)
      }
      else if (msg.type === 'event') {
        const phase = msg.event?.payload?.phase
        const capability = msg.event?.payload?.capability
        const seq = msg.event?.seq
        appendLog(`event seq=${seq} ${capability || ''} ${phase || ''}`)
        if (phase) latestStatus.value = phase
      }
      else if (msg.type === 'error') {
        appendLog(`error: ${msg.code} ${msg.message}`)
      }
    }
    catch {
      appendLog(`raw: ${typeof data === 'string' ? data.slice(0, 200) : '[binary]'}`)
    }
  })

  socketTask.onClose(() => {
    wsConnected.value = false
    appendLog('disconnected')
  })

  socketTask.onError((e: any) => {
    wsConnected.value = false
    appendLog(`error: ${JSON.stringify(e)}`)
  })
}

function disconnectWs() {
  socketTask?.close({})
  socketTask = null
  wsConnected.value = false
}

function appendLog(msg: string) {
  logLines.value.push(`[${new Date().toLocaleTimeString()}] ${msg}`)
  if (logLines.value.length > MAX_LOG_LINES) {
    logLines.value = logLines.value.slice(-MAX_LOG_LINES)
  }
}

async function handleHome() {
  if (!deviceId.value) return
  homeLoading.value = true
  try {
    const res = await v2SubmitTask(deviceId.value, 'home')
    appendLog(`home task submitted: ${res.taskId}`)
    toast.success('归零任务已提交')
  }
  catch (e: any) {
    toast.error(e?.message || '提交失败')
  }
  finally {
    homeLoading.value = false
  }
}

const statusColorClass = computed(() => {
  const s = latestStatus.value
  if (s === 'running' || s === 'accepted') return '#336cff'
  if (s === 'done') return '#07c160'
  if (s === 'failed' || s === 'cancelled') return '#ee0a24'
  return '#999'
})
</script>

<template>
  <view class="page-container min-h-screen bg-[#fbfbfb] p-[20rpx]">
    <!-- #ifdef MP-WEIXIN -->
    <view class="safe-area-top" />
    <!-- #endif -->

    <wd-navbar :title="t('v2.deviceDetail.title')" left-arrow fixed placeholder />

    <!-- 设备信息卡片 -->
    <view class="status-card rounded-[16rpx] bg-white p-[24rpx] mb-[20rpx] mt-[20rpx]">
      <view class="flex justify-between items-center">
        <text class="text-[28rpx] font-medium">{{ deviceInfo?.model || deviceId }}</text>
        <text class="text-[24rpx]" :style="{ color: wsConnected ? '#07c160' : '#999' }">
          {{ wsConnected ? t('v2.deviceDetail.connected') : t('v2.deviceDetail.disconnected') }}
        </text>
      </view>
      <view class="text-[24rpx] text-[#999] mt-[8rpx]" v-if="deviceInfo">
        HW: {{ deviceInfo.hwRev || '—' }} | FW: {{ deviceInfo.fwRev || '—' }}
      </view>
    </view>

    <!-- 任务状态卡片 -->
    <view class="status-card rounded-[16rpx] bg-white p-[24rpx] mb-[20rpx]">
      <view class="flex justify-between items-center">
        <text class="text-[28rpx] font-medium">{{ t('v2.deviceDetail.taskStatus') }}</text>
        <text class="text-[24rpx]" :style="{ color: statusColorClass }">{{ latestStatus }}</text>
      </view>
      <view class="text-[24rpx] text-[#999] mt-[8rpx]" v-if="latestStatus === '—'">
        {{ t('v2.deviceDetail.noTask') }}
      </view>
    </view>

    <!-- 归零按钮 -->
    <wd-button
      type="primary"
      block
      round
      size="large"
      :loading="homeLoading"
      custom-class="!h-[96rpx] !text-[32rpx] !mb-[20rpx]"
      @click="handleHome"
    >
      {{ homeLoading ? t('v2.deviceDetail.homing') : t('v2.deviceDetail.homeButton') }}
    </wd-button>

    <!-- Edge-A 事件日志区域 -->
    <view class="rounded-[16rpx] bg-white p-[24rpx] mb-[20rpx]">
      <view class="flex justify-between items-center mb-[12rpx]">
        <text class="text-[24rpx] font-medium text-[#666]">Edge-A 事件流</text>
        <text v-if="!wsConnected" class="text-[20rpx] text-[#336cff]" @click="connectWs">
          {{ t('v2.deviceDetail.connectAndSubscribe') }}
        </text>
      </view>
      <scroll-view
        scroll-y
        class="bg-[#f5f5f5] rounded-[8rpx] p-[16rpx]"
        style="max-height: 400rpx"
      >
        <text
          v-for="(line, i) in logLines"
          :key="i"
          class="block text-[20rpx] text-[#666] font-mono leading-[36rpx]"
        >
          {{ line }}
        </text>
        <text v-if="!logLines.length" class="text-[24rpx] text-[#999]">等待事件...</text>
      </scroll-view>
    </view>

    <!-- #ifdef MP-WEIXIN -->
    <view class="safe-area-bottom" />
    <!-- #endif -->
  </view>
</template>

<style scoped>
.safe-area-top {
  height: constant(safe-area-inset-top);
  height: env(safe-area-inset-top);
}
.safe-area-bottom {
  height: constant(safe-area-inset-bottom);
  height: env(safe-area-inset-bottom);
}
.font-mono {
  font-family: monospace;
}
</style>
