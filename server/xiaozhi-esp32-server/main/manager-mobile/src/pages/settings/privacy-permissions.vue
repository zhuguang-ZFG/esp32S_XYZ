<route lang="jsonc" type="page">
{
  "layout": "default",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "隐私与权限"
  }
}
</route>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'

defineOptions({ name: 'PrivacyPermissionsPage' })

type PermissionId = 'microphone' | 'bluetooth' | 'wifi'
type PermissionState = 'unknown' | 'granted' | 'denied'

interface PermissionItem {
  id: PermissionId
  title: string
  purpose: string
  fallback: string
}

const toast = useToast()
const permissionState = ref<Record<PermissionId, PermissionState>>({
  microphone: 'unknown',
  bluetooth: 'unknown',
  wifi: 'unknown',
})
const fallbackHint = ref('')

const permissionItems: PermissionItem[] = [
  {
    id: 'microphone',
    title: '麦克风',
    purpose: '用于语音唤醒、语音指令和声纹录入。授权前不会采集录音。',
    fallback: '麦克风未授权时，语音指令和声纹录入不可用，可继续使用文字与手动控制。',
  },
  {
    id: 'bluetooth',
    title: '蓝牙',
    purpose: '用于 BLE 配网和近场设备发现。',
    fallback: '蓝牙未开启或未授权时，可切换 SoftAP / Wi-Fi 回退配网。',
  },
  {
    id: 'wifi',
    title: 'Wi-Fi',
    purpose: '用于扫描本地网络并把网络凭据写入设备。',
    fallback: 'Wi-Fi 未开启或未授权时，请手动连接设备热点后使用回退配网。',
  },
]

const stateLabel = computed(() => (state: PermissionState) => {
  if (state === 'granted')
    return '已授权'
  if (state === 'denied')
    return '未授权'
  return '待确认'
})

const stateTagType = computed(() => (state: PermissionState) => {
  if (state === 'granted')
    return 'success'
  if (state === 'denied')
    return 'danger'
  return 'default'
})

function goBack() {
  uni.navigateBack()
}

function openPrivacyPolicy() {
  uni.navigateTo({ url: '/pages/login/privacy-policy-zh' })
}

function openSystemPermissionSettings() {
  uni.openSetting({
    success: () => {
      toast.success('已打开系统授权设置')
    },
    fail: () => {
      toast.warning('请在系统设置中打开小程序权限')
    },
  })
}

function requestPermission(item: PermissionItem) {
  uni.showModal({
    title: `${item.title}授权`,
    content: item.purpose,
    confirmText: '继续',
    cancelText: '暂不授权',
    success: (res) => {
      if (!res.confirm) {
        setPermissionDenied(item)
        return
      }
      if (item.id === 'microphone')
        requestMicrophonePermission(item)
      else if (item.id === 'bluetooth')
        requestBluetoothPermission(item)
      else
        requestWifiPermission(item)
    },
  })
}

function requestMicrophonePermission(item: PermissionItem) {
  uni.authorize({
    scope: 'scope.record',
    success: () => setPermissionGranted(item),
    fail: () => setPermissionDenied(item),
  })
}

function requestBluetoothPermission(item: PermissionItem) {
  uni.openBluetoothAdapter({
    success: () => {
      setPermissionGranted(item)
      uni.closeBluetoothAdapter({})
    },
    fail: () => setPermissionDenied(item),
  })
}

function requestWifiPermission(item: PermissionItem) {
  uni.startWifi({
    success: () => setPermissionGranted(item),
    fail: () => setPermissionDenied(item),
  })
}

function setPermissionGranted(item: PermissionItem) {
  permissionState.value[item.id] = 'granted'
  fallbackHint.value = ''
  toast.success(`${item.title}已授权`)
}

function setPermissionDenied(item: PermissionItem) {
  permissionState.value[item.id] = 'denied'
  fallbackHint.value = item.fallback
  uni.showModal({
    title: `${item.title}未授权`,
    content: `${item.fallback}\n\n可稍后在系统授权设置中重新开启。`,
    confirmText: '去设置',
    cancelText: '稍后',
    success: (res) => {
      if (res.confirm)
        openSystemPermissionSettings()
    },
  })
}
</script>

<template>
  <view class="min-h-screen bg-[#f5f7fb]">
    <wd-navbar title="隐私与权限" left-arrow placeholder safe-area-inset-top fixed @click-left="goBack" />

    <view class="p-[24rpx]">
      <view class="mb-[32rpx] border border-[#eeeeee] rounded-[24rpx] bg-[#fbfbfb] p-[32rpx]" style="box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);">
        <text class="block text-[32rpx] text-[#232338] font-bold">
          隐私协议
        </text>
        <text class="mt-[12rpx] block text-[24rpx] text-[#65686f] leading-[1.6]">
          查看个人信息收集、使用、保存和第三方服务说明。
        </text>
        <wd-button type="primary" block custom-class="!mt-[24rpx] !h-[80rpx] !rounded-[16rpx]" @click="openPrivacyPolicy">
          查看隐私政策
        </wd-button>
      </view>

      <view class="mb-[24rpx] flex items-center">
        <text class="text-[32rpx] text-[#232338] font-bold">
          权限授权
        </text>
      </view>

      <view class="space-y-[20rpx]">
        <view
          v-for="item in permissionItems"
          :key="item.id"
          class="border border-[#eeeeee] rounded-[24rpx] bg-[#fbfbfb] p-[28rpx]"
          style="box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);"
        >
          <view class="mb-[16rpx] flex items-center justify-between">
            <text class="text-[30rpx] text-[#232338] font-semibold">
              {{ item.title }}
            </text>
            <wd-tag :type="stateTagType(permissionState[item.id])" size="mini">
              {{ stateLabel(permissionState[item.id]) }}
            </wd-tag>
          </view>
          <text class="block text-[24rpx] text-[#65686f] leading-[1.6]">
            {{ item.purpose }}
          </text>
          <text class="mt-[8rpx] block text-[24rpx] text-[#9d9ea3] leading-[1.6]">
            {{ item.fallback }}
          </text>
          <wd-button type="info" block plain custom-class="!mt-[20rpx] !h-[72rpx] !rounded-[16rpx]" @click="requestPermission(item)">
            单独授权
          </wd-button>
        </view>
      </view>

      <view v-if="fallbackHint" class="mt-[28rpx] border border-[#ffd6a6] rounded-[20rpx] bg-[#fff8ed] p-[24rpx]">
        <text class="block text-[26rpx] text-[#8a5a00] leading-[1.6]">
          {{ fallbackHint }}
        </text>
      </view>

      <wd-button type="default" block custom-class="!mt-[28rpx] !h-[80rpx] !rounded-[16rpx]" @click="openSystemPermissionSettings">
        打开系统授权设置
      </wd-button>
    </view>
  </view>
</template>
