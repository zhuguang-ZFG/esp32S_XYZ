<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useMessage } from 'wot-design-uni'
import { t } from '@/i18n'
import { buildEdgeAClientWsUrl } from '@/utils'
import { v2GetDeviceInfo, v2SubmitTask } from '@/api/v2'
import type { V2DeviceInfo } from '@/api/v2/types'

defineOptions({ name: 'V2DeviceDetail' })
const message = useMessage()

const deviceId = ref('')
const deviceInfo = ref<V2DeviceInfo | null>(null)
const homeLoading = ref(false)
const wsConnected = ref(false)
const logLines = ref<string[]>([])
const latestPhase = ref('—')
let socketTask: UniApp.SocketTask | null = null

onLoad((opt: any) => { deviceId.value = opt?.deviceId || '' })

onMounted(async () => {
  if (!deviceId.value) return
  try { deviceInfo.value = await v2GetDeviceInfo(deviceId.value) } catch { /* offline ok */ }
  connectWs()
})

onUnmounted(() => { socketTask?.close({}); socketTask = null })

function connectWs() {
  const url = buildEdgeAClientWsUrl()
  const token = uni.getStorageSync('token') || ''
  appendLog(`→ ${url}`)
  socketTask = uni.connectSocket({ url, header: { Authorization: `Bearer ${token}` } })
  socketTask.onOpen(() => { wsConnected.value = true; appendLog('connected'); socketTask?.send({ data: JSON.stringify({ op: 'auth', token }) }) })
  socketTask.onMessage(({ data }) => {
    try {
      const m = typeof data === 'string' ? JSON.parse(data) : data
      if (m.type === 'authed') { appendLog('authed'); socketTask?.send({ data: JSON.stringify({ op: 'subscribe_device', device_id: deviceId.value }) }) }
      else if (m.type === 'subscribed') appendLog(`subscribed ${m.topic}`)
      else if (m.type === 'event') { const p = m.event?.payload; appendLog(`seq=${m.event.seq} ${p?.capability||''} ${p?.phase||''}`); if (p?.phase) latestPhase.value = p.phase }
      else if (m.type === 'pong') appendLog('pong')
      else if (m.type === 'error') appendLog(`error: ${m.code}`)
    } catch { appendLog(`raw: ${String(data).slice(0, 120)}`) }
  })
  socketTask.onClose(() => { wsConnected.value = false; appendLog('disconnected') })
  socketTask.onError(() => { wsConnected.value = false; appendLog('transport error') })
}

function appendLog(msg: string) {
  logLines.value.push(`[${new Date().toLocaleTimeString()}] ${msg}`)
  if (logLines.value.length > 30) logLines.value = logLines.value.slice(-30)
}

async function handleHome() {
  homeLoading.value = true
  try { const r = await v2SubmitTask(deviceId.value, 'home'); message.toast('归零已提交'); appendLog(`home: ${r.taskId}`) }
  catch (e: any) { message.alert(e?.message || '提交失败') }
  finally { homeLoading.value = false }
}

const phaseColor = computed(() => {
  if (latestPhase.value === 'running' || latestPhase.value === 'accepted') return '#336cff'
  if (latestPhase.value === 'done') return '#07c160'
  if (latestPhase.value === 'failed') return '#ee0a24'
  return '#999'
})
</script>

<template>
  <wd-config-provider theme-color="#336cff" />
  <wd-navbar :title="t('v2.deviceDetail.title')" left-arrow fixed placeholder safe-area-inset-top @click-left="uni.navigateBack()" />

  <!-- device info -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="deviceInfo?.model || deviceId" :label="`HW ${deviceInfo?.hwRev||'—'} | FW ${deviceInfo?.fwRev||'—'}`">
      <template #value>
        <wd-tag :type="wsConnected ? 'success' : 'default'" size="mini">
          {{ wsConnected ? t('v2.deviceDetail.connected') : t('v2.deviceDetail.disconnected') }}
        </wd-tag>
      </template>
    </wd-cell>
  </wd-cell-group>

  <!-- task status -->
  <wd-cell-group border>
    <wd-cell title="最近任务">
      <template #value>
        <wd-tag :type="latestPhase === 'done' ? 'success' : latestPhase === 'failed' ? 'danger' : latestPhase === 'running' ? 'primary' : 'default'" size="mini">
          {{ latestPhase }}
        </wd-tag>
      </template>
    </wd-cell>
  </wd-cell-group>

  <!-- home button -->
  <wd-button type="primary" block round size="large" :loading="homeLoading" custom-class="!mx-[20rpx] !mt-[20rpx] !h-[96rpx] !text-[32rpx]" @click="handleHome">
    {{ homeLoading ? t('v2.deviceDetail.homing') : t('v2.deviceDetail.homeButton') }}
  </wd-button>

  <!-- Edge-A WSS log (following existing device/index.vue debug pattern) -->
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell :title="wsConnected ? 'Edge-A WSS 已订阅' : 'Edge-A WSS 未连接'" center>
      <template v-if="!wsConnected" #value>
        <wd-button type="text" size="mini" @click="connectWs">{{ t('v2.deviceDetail.connectAndSubscribe') }}</wd-button>
      </template>
    </wd-cell>
  </wd-cell-group>
  <scroll-view scroll-y class="bg-[#f5f5f5] rounded-[8rpx] mx-[20rpx] p-[16rpx]" style="max-height:300rpx">
    <wd-text v-for="(l,i) in logLines" :key="i" :text="l" size="20rpx" color="#666" custom-class="!leading-[36rpx]" />
    <wd-text v-if="!logLines.length" text="等待事件..." size="24rpx" color="#999" />
  </scroll-view>
</template>
