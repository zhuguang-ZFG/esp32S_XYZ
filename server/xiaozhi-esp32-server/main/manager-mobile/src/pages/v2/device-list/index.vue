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
import type { V2DeviceInfo, V2DeviceTransferResponse } from '@/api/v2/types'
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { v2AcceptDeviceTransfer, v2BindDevice, v2GetDevices, v2ListPendingIncomingDeviceTransfers, v2SubmitTask } from '@/api/v2'
import { t } from '@/i18n'
import { updateM6PendingTabBarBadge } from '@/utils'

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
const quickLoading = ref<Record<string, boolean>>({})

onShow(() => {
  loadPageData()
})

async function loadPageData() {
  await Promise.all([loadDevices(), loadPendingIncomingTransfers()])
}

async function loadDevices() {
  loading.value = true
  try {
    const res = await v2GetDevices()
    devices.value = res.rows || []
  }
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
  if (!bindSn.value.trim() || !bindCode.value.trim())
    return
  bindLoading.value = true
  try {
    await v2BindDevice(bindSn.value.trim(), bindCode.value.trim())
    showBind.value = false
    uni.showToast({ title: t('v2.deviceList.confirm'), icon: 'none' })
    await loadDevices()
  }
  catch (e: any) { message.alert(e?.message || t('v2.deviceList.bindFailed')) }
  finally { bindLoading.value = false }
}

async function handleAcceptIncomingTransfer(transferId: string) {
  transferLoading.value = true
  try {
    await v2AcceptDeviceTransfer(transferId)
    uni.showToast({ title: t('v2.deviceList.transferAccepted'), icon: 'success' })
    await loadPageData()
  }
  catch (e: any) { message.alert(e?.message || t('v2.deviceList.transferFailed')) }
  finally { transferLoading.value = false }
}

function openDevice(deviceId: string) {
  uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${deviceId}` })
}

async function quickControl(deviceId: string, action: string) {
  const key = `${deviceId}-${action}`
  quickLoading.value[key] = true
  const labelMap: Record<string, string> = {
    draw: t('v2.deviceList.quickDraw'),
    write: t('v2.deviceList.quickWrite'),
    home: t('v2.deviceList.quickHome'),
    pause: t('v2.deviceList.quickPause'),
  }
  const label = labelMap[action] || action
  try {
    const capabilityMap: Record<string, string> = { draw: 'draw_generated', write: 'write_text', home: 'home', pause: 'pause', resume: 'resume' }
    const capability = capabilityMap[action] || action
    const params = action === 'draw' ? { prompt: t('v2.deviceList.quickDrawPrompt') } : action === 'write' ? { text: t('v2.deviceList.quickWriteText') } : {}
    await v2SubmitTask(deviceId, capability, params)
    uni.showToast({ title: `${label} ${t('v2.deviceList.taskSubmitted')}`, icon: 'success' })
  }
  catch (e: any) { uni.showToast({ title: `${label} ${t('v2.deviceList.taskFailed')}`, icon: 'none' }) }
  finally { quickLoading.value[key] = false }
}

function deviceIconName(model?: string) {
  if (model?.includes('draw'))
    return 'photo'
  if (model?.includes('write'))
    return 'edit-2'
  return 'phone'
}
</script>

<template>
  <view class="device-list-page page-enter">
    <wd-navbar :title="t('v2.deviceList.title')" placeholder safe-area-inset-top fixed />

    <wd-status-tip v-if="loading" image="loading" tip="" />

    <!-- Pending Transfers -->
    <view v-if="pendingIncomingTransfers.length" class="bento-card transfer-card">
      <view class="transfer-header">
        <text class="bento-title">
          {{ t('v2.deviceList.pendingTransfers') }}
        </text>
        <wd-tag type="warning" size="small" round>
          {{ pendingIncomingTransferBadgeText }}
        </wd-tag>
      </view>
      <view v-for="transfer in pendingIncomingTransfers" :key="transfer.transferId" class="transfer-item">
        <view class="transfer-info">
          <text class="transfer-device">
            {{ transfer.deviceId }}
          </text>
          <text class="transfer-label">
            {{ t('v2.deviceList.fromAccount') }} {{ transfer.sourceAccountId }}
          </text>
        </view>
        <wd-button type="success" round size="small" :loading="transferLoading" @click="handleAcceptIncomingTransfer(transfer.transferId)">
          {{ t('v2.deviceList.accept') }}
        </wd-button>
      </view>
    </view>

    <!-- Empty State -->
    <view v-if="!loading && !devices.length" class="empty-state">
      <wd-icon name="notification" size="80" color="#c7c7cc" />
      <text class="empty-title">
        {{ t('v2.deviceList.empty') }}
      </text>
      <wd-button type="primary" round custom-class="!mt-[24rpx]" @click="showBind = true; bindSn = ''; bindCode = ''">
        {{ t('v2.deviceList.addDevice') }}
      </wd-button>
    </view>

    <!-- Device Cards -->
    <view v-if="!loading && devices.length" class="device-grid">
      <view v-for="d in devices" :key="d.deviceId" class="bento-card device-card" @click="openDevice(d.deviceId)">
        <view class="device-card-header">
          <view class="device-icon-wrap">
            <wd-icon :name="deviceIconName(d.model)" size="24" color="#3b82f6" />
          </view>
          <wd-tag :type="d.status === 'online' ? 'success' : 'default'" size="small" round>
            {{ d.status === 'online' ? t('v2.deviceList.online') : t('v2.deviceList.offline') }}
          </wd-tag>
        </view>
        <view class="device-card-body">
          <text class="device-model">
            {{ d.model || t('v2.deviceList.device') }}
          </text>
          <text class="device-id">
            {{ d.deviceId }}
          </text>
        </view>
        <!-- Quick Controls -->
        <view class="device-controls" @click.stop>
          <view class="control-btn" :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-draw`] }" @click="quickControl(d.deviceId, 'draw')">
            <wd-icon name="photo" size="20" color="#3b82f6" />
            <text class="control-label">
              {{ t('v2.deviceList.quickDraw') }}
            </text>
          </view>
          <view class="control-btn" :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-write`] }" @click="quickControl(d.deviceId, 'write')">
            <wd-icon name="edit-2" size="20" color="#3b82f6" />
            <text class="control-label">
              {{ t('v2.deviceList.quickWrite') }}
            </text>
          </view>
          <view class="control-btn" :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-home`] }" @click="quickControl(d.deviceId, 'home')">
            <wd-icon name="home" size="20" color="#3b82f6" />
            <text class="control-label">
              {{ t('v2.deviceList.quickHome') }}
            </text>
          </view>
          <view class="control-btn" :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-pause`] }" @click="quickControl(d.deviceId, 'pause')">
            <wd-icon name="pause-circle" size="20" color="#3b82f6" />
            <text class="control-label">
              {{ t('v2.deviceList.quickPause') }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <!-- Add Device -->
    <view v-if="!loading && devices.length" class="add-device-bar">
      <wd-button type="primary" round block @click="showBind = true; bindSn = ''; bindCode = ''">
        {{ t('v2.deviceList.addDevice') }}
      </wd-button>
    </view>

    <!-- Bind Popup -->
    <wd-popup v-model="showBind" position="bottom" custom-style="border-radius:32rpx 32rpx 0 0;padding:40rpx">
      <text class="bind-title">
        {{ t('v2.deviceList.addDevice') }}
      </text>
      <wd-input v-model="bindSn" :placeholder="t('v2.deviceList.enterSn')" clearable custom-cell-class="!mb-[20rpx]" />
      <wd-input v-model="bindCode" :placeholder="t('v2.deviceList.enterCode')" clearable />
      <view class="mt-[40rpx] flex gap-[20rpx]">
        <wd-button type="default" round block @click="showBind = false">
          {{ t('v2.deviceList.cancel') }}
        </wd-button>
        <wd-button type="primary" round block :loading="bindLoading" @click="handleBind">
          {{ t('v2.deviceList.confirm') }}
        </wd-button>
      </view>
    </wd-popup>

    <view style="height: env(safe-area-inset-bottom);" />
  </view>
</template>

<style lang="scss" scoped>
.device-list-page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 24rpx;
}

.bento-card {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: var(--r);
  padding: 28rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(24rpx);
}

.bento-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text);
}

/* Transfer */
.transfer-card {
  margin-top: 20rpx;
}
.transfer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.transfer-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--border);
  &:last-child {
    border-bottom: none;
  }
}
.transfer-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.transfer-device {
  font-size: 28rpx;
  color: var(--text);
  font-weight: 600;
}
.transfer-label {
  font-size: 22rpx;
  color: var(--dim);
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;
  .empty-title {
    margin-top: 24rpx;
    font-size: 30rpx;
    color: var(--dim);
    font-weight: 500;
  }
}

/* Device Grid */
.device-grid {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding-top: 20rpx;
}
.device-card {
  transition: all 0.15s;
  &:active {
    transform: scale(0.98);
  }
}
.device-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.device-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: rgba(59, 130, 246, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
}
.device-card-body {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  margin-bottom: 20rpx;
}
.device-model {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text);
}
.device-id {
  font-size: 22rpx;
  color: var(--dim);
  font-family: monospace;
}

/* Quick Controls */
.device-controls {
  display: flex;
  gap: 12rpx;
}
.control-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 0;
  background: var(--bg);
  border-radius: 16rpx;
  transition: all 0.15s;
  &:active:not(.disabled) {
    background: rgba(59, 130, 246, 0.15);
  }
  &.disabled {
    opacity: 0.3;
  }
  .control-label {
    font-size: 22rpx;
    color: var(--muted);
  }
}

.add-device-bar {
  padding: 24rpx;
}

.bind-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text);
  text-align: center;
  margin-bottom: 24rpx;
}
</style>
