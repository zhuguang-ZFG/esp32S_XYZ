<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { t } from '@/i18n'
import { updateM6PendingTabBarBadge } from '@/utils'
import { v2AcceptDeviceTransfer, v2BindDevice, v2GetDevices, v2ListPendingIncomingDeviceTransfers, v2SubmitTask } from '@/api/v2'
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

// 一键控制 loading
const quickLoading = ref<Record<string, boolean>>({})

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

// 一键控制
async function quickControl(deviceId: string, action: string, label: string) {
  const key = `${deviceId}-${action}`
  quickLoading.value[key] = true
  try {
    const capabilityMap: Record<string, string> = {
      draw: 'draw_generated',
      write: 'write_text',
      home: 'home',
      pause: 'pause',
      resume: 'resume',
    }
    const capability = capabilityMap[action] || action
    const params = action === 'draw' ? { prompt: '快速绘图' } :
      action === 'write' ? { text: '快速写字' } : {}
    await v2SubmitTask(deviceId, capability, params)
    uni.showToast({ title: `${label} 已下发`, icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: `${label} 失败`, icon: 'none' })
  } finally {
    quickLoading.value[key] = false
  }
}

function getDeviceIcon(model?: string) {
  if (model?.includes('draw')) return '🎨'
  if (model?.includes('write')) return '✍️'
  return '🤖'
}

function getDeviceGradient(status: string) {
  return status === 'online'
    ? 'linear-gradient(135deg, rgba(59,130,246,0.12), rgba(139,92,246,0.08))'
    : 'linear-gradient(135deg, rgba(255,255,255,0.03), rgba(255,255,255,0.02))'
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

    <!-- 待处理转移 -->
    <view v-if="pendingIncomingTransfers.length" class="transfer-section">
      <view class="transfer-header">
        <text class="transfer-title">{{ t('v2.deviceList.pendingTransfers') }}</text>
        <wd-tag type="warning" size="mini">{{ pendingIncomingTransferBadgeText }}</wd-tag>
      </view>
      <view
        v-for="transfer in pendingIncomingTransfers"
        :key="transfer.transferId"
        class="transfer-card"
      >
        <view class="transfer-info">
          <text class="transfer-device">{{ transfer.deviceId }}</text>
          <text class="transfer-label">来自账户 {{ transfer.sourceAccountId }}</text>
        </view>
        <wd-button
          type="success"
          round
          size="small"
          :loading="transferLoading"
          @click="handleAcceptIncomingTransfer(transfer.transferId)"
        >
          {{ t('v2.deviceList.accept') }}
        </wd-button>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-if="!loading && !devices.length" class="empty-state">
      <wd-icon name="notification" custom-class="text-[120rpx] text-[#3a4252] mb-[32rpx]" />
      <text class="empty-title">{{ t('v2.deviceList.empty') }}</text>
      <wd-button type="primary" round custom-class="!mt-[24rpx]" @click="showBind = true; bindSn = ''; bindCode = ''">
        {{ t('v2.deviceList.addDevice') }}
      </wd-button>
    </view>

    <!-- 设备卡片列表 -->
    <view v-if="!loading && devices.length" class="device-grid">
      <view
        v-for="d in devices"
        :key="d.deviceId"
        class="device-card"
        :style="{ background: getDeviceGradient(d.status) }"
        @click="openDevice(d.deviceId)"
      >
        <view class="device-card-header">
          <view class="device-icon-wrap">
            <text class="device-icon">{{ getDeviceIcon(d.model) }}</text>
          </view>
          <view class="device-status-badge" :class="d.status">
            <text>{{ d.status === 'online' ? '在线' : '离线' }}</text>
          </view>
        </view>

        <view class="device-card-body">
          <text class="device-model">{{ d.model || '设备' }}</text>
          <text class="device-id">{{ d.deviceId }}</text>
        </view>

        <!-- 快捷控制 -->
        <view class="device-controls" @click.stop>
          <view
            class="control-btn"
            :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-draw`] }"
            @click="quickControl(d.deviceId, 'draw', '绘图')"
          >
            <text class="control-icon">🎨</text>
            <text class="control-label">绘图</text>
          </view>
          <view
            class="control-btn"
            :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-write`] }"
            @click="quickControl(d.deviceId, 'write', '写字')"
          >
            <text class="control-icon">✍️</text>
            <text class="control-label">写字</text>
          </view>
          <view
            class="control-btn"
            :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-home`] }"
            @click="quickControl(d.deviceId, 'home', '回家')"
          >
            <text class="control-icon">🏠</text>
            <text class="control-label">回家</text>
          </view>
          <view
            class="control-btn"
            :class="{ disabled: d.status !== 'online' || quickLoading[`${d.deviceId}-pause`] }"
            @click="quickControl(d.deviceId, 'pause', '暂停')"
          >
            <text class="control-icon">⏸️</text>
            <text class="control-label">暂停</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 添加设备按钮 -->
    <view v-if="!loading && devices.length" class="add-device-bar">
      <wd-button type="primary" block round @click="showBind = true; bindSn = ''; bindCode = ''">
        {{ t('v2.deviceList.addDevice') }}
      </wd-button>
    </view>

    <!-- 绑定弹窗 -->
    <wd-popup v-model="showBind" position="bottom" custom-style="border-radius:32rpx 32rpx 0 0;padding:40rpx;background:#0a0a14;border-top:1rpx solid rgba(255,255,255,0.08);box-shadow:0 -8rpx 40rpx rgba(0,0,0,0.5)">
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
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

/* 转移区 */
.transfer-section {
  margin: 24rpx;
  padding: 24rpx;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 24rpx;
}

.transfer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.transfer-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #f0f4f8;
}

.transfer-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);

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
  color: #f0f4f8;
  font-weight: 600;
}

.transfer-label {
  font-size: 22rpx;
  color: #5a6372;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;

  .empty-title {
    margin-bottom: 16rpx;
    font-size: 32rpx;
    color: #8b95a8;
    font-weight: 500;
  }
}

/* 设备卡片网格 */
.device-grid {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 24rpx;
}

.device-card {
  border: 1rpx solid rgba(255, 255, 255, 0.06);
  border-radius: 28rpx;
  padding: 28rpx;
  transition: all 0.3s ease;

  &:active {
    transform: scale(0.98);
    opacity: 0.9;
  }
}

.device-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.device-icon-wrap {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  background: rgba(59, 130, 246, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
}

.device-icon {
  font-size: 40rpx;
}

.device-status-badge {
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  font-weight: 600;

  &.online {
    background: rgba(52, 211, 153, 0.15);
    color: #34d399;
  }

  &.offline {
    background: rgba(255, 255, 255, 0.05);
    color: #5a6372;
  }
}

.device-card-body {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-bottom: 24rpx;
}

.device-model {
  font-size: 32rpx;
  font-weight: 700;
  color: #f0f4f8;
}

.device-id {
  font-size: 24rpx;
  color: #5a6372;
  font-family: monospace;
}

/* 快捷控制 */
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
  padding: 20rpx 0;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 16rpx;
  transition: all 0.2s ease;

  &:active:not(.disabled) {
    transform: scale(0.95);
    background: rgba(59, 130, 246, 0.1);
  }

  &.disabled {
    opacity: 0.3;
    pointer-events: none;
  }

  .control-icon {
    font-size: 36rpx;
  }

  .control-label {
    font-size: 22rpx;
    color: #c0c8d8;
  }
}

/* 添加设备 */
.add-device-bar {
  padding: 0 24rpx;
  margin-top: 8rpx;
  margin-bottom: 24rpx;
}
</style>
