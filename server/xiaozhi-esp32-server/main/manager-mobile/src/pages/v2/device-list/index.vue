<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { t } from '@/i18n'
import { toast } from '@/utils/toast'
import { v2BindDevice, v2GetDevices } from '@/api/v2'
import type { V2DeviceInfo } from '@/api/v2/types'

defineOptions({ name: 'V2DeviceList' })

const loading = ref(false)
const devices = ref<V2DeviceInfo[]>([])
const showBindPopup = ref(false)
const bindSn = ref('')
const bindCode = ref('')
const bindLoading = ref(false)

const hasDevices = computed(() => devices.value.length > 0)

onMounted(() => {
  loadDevices()
})

async function loadDevices() {
  loading.value = true
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
  }
  catch (e) {
    console.error('load devices failed', e)
  }
  finally {
    loading.value = false
  }
}

function openBind() {
  bindSn.value = ''
  bindCode.value = ''
  showBindPopup.value = true
}

async function handleBind() {
  if (!bindSn.value.trim() || !bindCode.value.trim()) return
  bindLoading.value = true
  try {
    await v2BindDevice(bindSn.value.trim(), bindCode.value.trim())
    showBindPopup.value = false
    toast.success(t('v2.deviceList.confirm'))
    await loadDevices()
  }
  catch (e: any) {
    toast.error(e?.message || t('v2.deviceDetail.toastBindFailed'))
  }
  finally {
    bindLoading.value = false
  }
}

function goDetail(device: V2DeviceInfo) {
  uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${device.deviceId}` })
}

function statusColor(status: string) {
  if (!status || status === 'offline') return '#999'
  return '#07c160'
}

function statusText(status: string) {
  if (!status || status === 'offline') return t('v2.deviceList.statusOffline')
  return t('v2.deviceList.statusActive')
}
</script>

<template>
  <view class="page-container min-h-screen bg-[#fbfbfb] p-[20rpx]">
    <!-- #ifdef MP-WEIXIN -->
    <view class="safe-area-top" />
    <!-- #endif -->

    <wd-navbar :title="t('v2.deviceList.title')" left-arrow fixed placeholder />

    <view v-if="loading" class="flex justify-center py-[200rpx]">
      <wd-loading color="#336cff" />
    </view>

    <view v-else-if="hasDevices" class="mt-[20rpx]">
      <view
        v-for="device in devices"
        :key="device.deviceId"
        class="status-card rounded-[16rpx] bg-white p-[24rpx] mb-[20rpx]"
        @click="goDetail(device)"
      >
        <view class="flex justify-between items-center">
          <text class="text-[28rpx] font-medium text-[#333]">
            {{ device.model || device.deviceId }}
          </text>
          <text class="text-[24rpx]" :style="{ color: statusColor(device.status) }">
            {{ statusText(device.status) }}
          </text>
        </view>
        <view class="text-[24rpx] text-[#999] mt-[8rpx]">
          {{ device.deviceId }}
        </view>
      </view>
    </view>

    <view v-else class="flex flex-col items-center justify-center py-[200rpx]">
      <text class="text-[28rpx] text-[#999] mb-[40rpx]">{{ t('v2.deviceList.empty') }}</text>
    </view>

    <wd-button
      type="primary"
      block
      round
      icon="add"
      custom-class="!mt-[40rpx]"
      @click="openBind"
    >
      {{ t('v2.deviceList.addDevice') }}
    </wd-button>

    <wd-popup
      v-model="showBindPopup"
      position="bottom"
      custom-style="border-radius: 32rpx 32rpx 0 0; padding: 40rpx;"
    >
      <view class="text-[32rpx] font-medium mb-[24rpx] text-center">
        {{ t('v2.deviceList.addDevice') }}
      </view>
      <wd-input
        v-model="bindSn"
        :placeholder="t('v2.deviceList.enterSn')"
        clearable
        custom-cell-class="!mb-[20rpx]"
      />
      <wd-input
        v-model="bindCode"
        :placeholder="t('v2.deviceList.enterCode')"
        clearable
      />
      <view class="flex gap-[20rpx] mt-[40rpx]">
        <wd-button type="default" block round @click="showBindPopup = false">
          {{ t('v2.deviceList.cancel') }}
        </wd-button>
        <wd-button type="primary" block round :loading="bindLoading" @click="handleBind">
          {{ t('v2.deviceList.confirm') }}
        </wd-button>
      </view>
    </wd-popup>

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
</style>
