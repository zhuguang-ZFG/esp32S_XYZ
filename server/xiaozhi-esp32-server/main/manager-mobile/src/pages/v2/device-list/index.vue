<route lang="jsonc" type="home">
{
  "layout": "tabbar",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "我的设备"
  }
}
</route>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { t } from '@/i18n'
import { updateM6PendingTabBarBadge } from '@/utils'
import { v2AcceptDeviceTransfer, v2BindDevice, v2GetDevices, v2ListPendingIncomingDeviceTransfers } from '@/api/v2'
import type { V2DeviceInfo, V2DeviceTransferResponse } from '@/api/v2/types'

defineOptions({ name: 'V2DeviceList' })
const message = useMessage()
const loading = ref(false)
const devices = ref<V2DeviceInfo[]>([])
const pendingIncomingTransfers = ref<V2DeviceTransferResponse[]>([])
const showBind = ref(false)
const bindSn = ref('')
const bindCode = ref('')
const bindLoading = ref(false)
const transferLoading = ref(false)
const pendingIncomingTransferCount = computed(() => pendingIncomingTransfers.value.length)
const pendingIncomingTransferBadgeText = computed(() => String(pendingIncomingTransferCount.value))

onMounted(() => { loadPageData() })

async function loadPageData() {
  await Promise.all([loadDevices(), loadPendingIncomingTransfers()])
}

async function loadDevices() {
  loading.value = true
  try { const res = await v2GetDevices(); devices.value = res.rows || [] }
  catch (e) { console.error(e) }
  finally { loading.value = false }
}

async function loadPendingIncomingTransfers() {
  try {
    pendingIncomingTransfers.value = await v2ListPendingIncomingDeviceTransfers()
    updateM6PendingTabBarBadge('transfer', pendingIncomingTransfers.value.length)
  }
  catch (e) {
    console.error(e)
    pendingIncomingTransfers.value = []
    updateM6PendingTabBarBadge('transfer', 0)
  }
}

async function handleBind() {
  if (!bindSn.value.trim() || !bindCode.value.trim()) return
  bindLoading.value = true
  try {
    await v2BindDevice(bindSn.value.trim(), bindCode.value.trim())
    showBind.value = false
    showSubmitToast(t('v2.deviceList.confirm'))
    await loadDevices()
  } catch (e: any) { message.alert(e?.message || '绑定失败') }
  finally { bindLoading.value = false }
}

async function handleAcceptIncomingTransfer(transferId: string) {
  transferLoading.value = true
  try {
    await v2AcceptDeviceTransfer(transferId)
    showSubmitToast('Transfer accepted')
    await loadPageData()
  } catch (e: any) { message.alert(e?.message || 'Accept transfer failed') }
  finally { transferLoading.value = false }
}

function showSubmitToast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

function openDevice(deviceId: string) {
  uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${deviceId}` })
}
</script>

<template>
  <view class="nebula-page">
    <wd-config-provider theme-color="#3b82f6" />
    <wd-navbar :title="t('v2.deviceList.title')" fixed placeholder safe-area-inset-top
      custom-class="!bg-[#07070f]"
      title-class="!text-[#f0f4f8]"
    />

    <wd-status-tip v-if="loading" image="loading" tip="" />

    <wd-cell-group v-if="pendingIncomingTransfers.length" border custom-class="!mt-[20rpx] !bg-[#0a0a14]">
      <wd-cell :title="t('v2.deviceList.pendingTransfers')" :label="t('v2.deviceList.transfersWaiting')"
        custom-class="!bg-[#0a0a14]"
      >
        <template #value>
          <wd-tag type="warning" size="mini">
            {{ pendingIncomingTransferBadgeText }}
          </wd-tag>
        </template>
      </wd-cell>
      <wd-cell
        v-for="transfer in pendingIncomingTransfers"
        :key="transfer.transferId"
        :title="transfer.deviceId"
        :label="`#${transfer.transferId} from account ${transfer.sourceAccountId}`"
        custom-class="!bg-[#0a0a14]"
      >
        <template #value>
          <wd-button
            type="success"
            round
            size="small"
            :loading="transferLoading"
            @click="handleAcceptIncomingTransfer(transfer.transferId)"
          >
            {{ t('v2.deviceList.accept') }}
          </wd-button>
        </template>
      </wd-cell>
    </wd-cell-group>

    <view v-if="!loading && !devices.length" class="flex flex-col items-center justify-center py-[120rpx]">
      <wd-icon name="notification" custom-class="text-[120rpx] text-[#3a4252] mb-[32rpx]" />
      <text class="mb-[16rpx] text-[32rpx] text-[#8b95a8] font-medium">
        {{ t('v2.deviceList.empty') }}
      </text>
      <wd-button type="primary" round custom-class="!mt-[24rpx]" @click="showBind = true; bindSn = ''; bindCode = ''">
        {{ t('v2.deviceList.addDevice') }}
      </wd-button>
    </view>

    <wd-cell-group v-if="!loading && devices.length" border custom-class="!mt-[20rpx] !bg-[#0a0a14]">
      <wd-cell
        v-for="d in devices" :key="d.deviceId" :title="d.model || d.deviceId"
        :label="d.deviceId" is-link clickable
        custom-class="!bg-[#0a0a14]"
        @click="openDevice(d.deviceId)"
      >
        <template #value>
          <wd-tag :type="d.status === 'online' ? 'success' : 'default'" size="mini">
            {{ d.status === 'online' ? '在线' : '离线' }}
          </wd-tag>
        </template>
      </wd-cell>
    </wd-cell-group>

    <wd-button type="primary" block round custom-class="!mt-[40rpx] !mx-[20rpx]" @click="showBind = true; bindSn = ''; bindCode = ''">
      {{ t('v2.deviceList.addDevice') }}
    </wd-button>

    <wd-popup v-model="showBind" position="bottom" custom-style="border-radius:32rpx 32rpx 0 0;padding:40rpx;background:#0a0a14">
      <wd-text :text="t('v2.deviceList.addDevice')" size="32rpx" bold custom-class="!text-center !mb-[24rpx] !text-[#f0f4f8]" />
      <wd-input v-model="bindSn" :placeholder="t('v2.deviceList.enterSn')" clearable custom-cell-class="!mb-[20rpx]" />
      <wd-input v-model="bindCode" :placeholder="t('v2.deviceList.enterCode')" clearable />
      <view class="flex gap-[20rpx] mt-[40rpx]">
        <wd-button type="default" block round @click="showBind = false">{{ t('v2.deviceList.cancel') }}</wd-button>
        <wd-button type="primary" block round :loading="bindLoading" @click="handleBind">{{ t('v2.deviceList.confirm') }}</wd-button>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
.nebula-page {
  min-height: 100vh;
  background: #07070f;
  padding-top: env(safe-area-inset-top);
}
</style>
