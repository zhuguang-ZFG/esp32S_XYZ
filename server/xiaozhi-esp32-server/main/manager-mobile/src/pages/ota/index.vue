<route lang="jsonc" type="page">
{
  "layout": "default",
  "style": {
    "navigationBarTitleText": "固件升级",
    "navigationStyle": "custom"
  }
}
</route>

<script lang="ts" setup>
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { v2CheckOta, v2StartOta } from '@/api/v2'

interface OtaStatus {
  deviceId: string
  currentVersion: string
  availableVersion: string | null
  firmware: Record<string, string> | null
  releaseNotes: string
  status: 'no_release' | 'up_to_date' | 'available_not_selected' | 'available_selected'
  selected: boolean
  rollbackAvailable: boolean
}

defineOptions({ name: 'OtaPage' })

const toast = useToast()
const deviceId = ref('')
const loading = ref(false)
const actionLoading = ref(false)
const ota = ref<OtaStatus | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const statusText = computed(() => {
  const s = ota.value?.status
  if (s === 'no_release') return '暂无可用固件'
  if (s === 'up_to_date') return '当前已是最新版本'
  if (s === 'available_not_selected') return '有新版本可升级'
  if (s === 'available_selected') return '已加入升级队列，等待设备下载'
  return '加载中...'
})

const progressText = computed(() => {
  if (ota.value?.status === 'available_selected') return '设备正在下载并安装新固件，请保持在线...'
  return ''
})

async function loadOtaStatus(showLoading = true) {
  if (!deviceId.value) return
  if (showLoading) loading.value = true
  try {
    ota.value = await v2CheckOta(deviceId.value) as OtaStatus
  }
  catch (error: any) {
    console.error('check ota failed:', error)
    toast.error(error?.message || '查询升级状态失败')
  }
  finally {
    if (showLoading) loading.value = false
  }
}

async function handleStart() {
  if (!deviceId.value || actionLoading.value) return
  actionLoading.value = true
  try {
    const res = await v2StartOta(deviceId.value) as OtaStatus & { ok: boolean }
    ota.value = res
    toast.success('已启动升级')
    startPolling()
  }
  catch (error: any) {
    toast.error(error?.message || '启动升级失败')
  }
  finally {
    actionLoading.value = false
  }
}

async function handleRollback() {
  if (!deviceId.value || actionLoading.value) return
  actionLoading.value = true
  try {
    const res = await v2StartOta(deviceId.value, true) as OtaStatus & { ok: boolean }
    ota.value = res
    toast.success('已取消升级')
  }
  catch (error: any) {
    toast.error(error?.message || '回滚失败')
  }
  finally {
    actionLoading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => loadOtaStatus(false), 5000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onLoad((opt: any) => { deviceId.value = opt?.deviceId || '' })
onMounted(() => { loadOtaStatus(); startPolling() })
onUnload(() => { stopPolling() })
</script>

<template>
  <view class="min-h-screen" style="background: #07070f;">
    <wd-navbar
      title="固件升级" placeholder safe-area-inset-top fixed
      custom-class="!bg-[#07070f]"
      title-class="!text-[#f0f4f8]"
    />

    <view class="p-[24rpx]">
      <view
        class="mb-[32rpx] overflow-hidden border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
        style="background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
      >
        <view class="mb-[24rpx] flex items-center justify-between">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            设备固件
          </text>
          <wd-tag v-if="ota" :type="ota.status === 'up_to_date' ? 'success' : 'warning'" size="small" round>
            {{ statusText }}
          </wd-tag>
        </view>

        <view class="space-y-[16rpx]">
          <view class="flex items-center justify-between">
            <text class="text-[28rpx] text-[#8b95a8]">设备 ID</text>
            <text class="max-w-[60%] truncate text-[28rpx] text-[#f0f4f8]">{{ deviceId || '—' }}</text>
          </view>
          <view class="flex items-center justify-between">
            <text class="text-[28rpx] text-[#8b95a8]">当前版本</text>
            <text class="text-[28rpx] text-[#f0f4f8]">{{ ota?.currentVersion || '—' }}</text>
          </view>
          <view class="flex items-center justify-between">
            <text class="text-[28rpx] text-[#8b95a8]">可用版本</text>
            <text class="text-[28rpx] text-[#f0f4f8]">{{ ota?.availableVersion || '—' }}</text>
          </view>
        </view>

        <view v-if="ota?.releaseNotes" class="mt-[24rpx] rounded-[16rpx] p-[24rpx]" style="background: #0a0a14;">
          <text class="mb-[8rpx] block text-[28rpx] text-[#f0f4f8] font-semibold">更新说明</text>
          <text class="block text-[26rpx] text-[#8b95a8] leading-[40rpx]">{{ ota.releaseNotes }}</text>
        </view>
      </view>

      <view
        v-if="ota?.status === 'available_selected'"
        class="mb-[32rpx] overflow-hidden border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
        style="background: rgba(255,255,255,0.03);"
      >
        <text class="mb-[16rpx] block text-[28rpx] text-[#f0f4f8]">升级进度</text>
        <wd-progress :percentage="0" stroke-width="16rpx" color="#336cff" indeterminate />
        <text class="mt-[16rpx] block text-[24rpx] text-[#8b95a8]">{{ progressText }}</text>
      </view>

      <view class="flex gap-[16rpx]">
        <wd-button
          v-if="ota?.status === 'available_not_selected'"
          type="primary"
          block
          round
          :loading="actionLoading"
          custom-class="flex-1 h-[88rpx] rounded-[20rpx] text-[28rpx] font-semibold border-none"
          @click="handleStart"
        >
          立即升级
        </wd-button>
        <wd-button
          v-if="ota?.rollbackAvailable"
          type="default"
          block
          round
          :loading="actionLoading"
          custom-class="flex-1 h-[88rpx] rounded-[20rpx] text-[28rpx] font-semibold border-[rgba(255,255,255,0.04)] text-[#8b95a8]"
          @click="handleRollback"
        >
          回滚 / 取消升级
        </wd-button>
      </view>

      <view v-if="loading" class="mt-[40rpx] flex justify-center">
        <wd-loading color="#336cff" />
      </view>

      <view style="height: env(safe-area-inset-bottom);" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
</style>
