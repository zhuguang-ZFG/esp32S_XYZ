<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useMessage } from 'wot-design-uni'
import { t } from '@/i18n'
import { v2BindDevice, v2GetDevices } from '@/api/v2'
import type { V2DeviceInfo } from '@/api/v2/types'

defineOptions({ name: 'V2DeviceList' })
const message = useMessage()
const loading = ref(false)
const devices = ref<V2DeviceInfo[]>([])
const showBind = ref(false)
const bindSn = ref('')
const bindCode = ref('')
const bindLoading = ref(false)

onMounted(() => { loadDevices() })

async function loadDevices() {
  loading.value = true
  try { const res = await v2GetDevices(); devices.value = res.rows || [] }
  catch (e) { console.error(e) }
  finally { loading.value = false }
}

async function handleBind() {
  if (!bindSn.value.trim() || !bindCode.value.trim()) return
  bindLoading.value = true
  try {
    await v2BindDevice(bindSn.value.trim(), bindCode.value.trim())
    showBind.value = false
    message.toast(t('v2.deviceList.confirm'))
    await loadDevices()
  } catch (e: any) { message.alert(e?.message || '绑定失败') }
  finally { bindLoading.value = false }
}
</script>

<template>
  <wd-config-provider theme-color="#336cff" />
  <wd-navbar :title="t('v2.deviceList.title')" fixed placeholder safe-area-inset-top />

  <wd-status-tip v-if="loading" image="loading" tip="" />

  <wd-status-tip v-else-if="!devices.length" image="content" :tip="t('v2.deviceList.empty')" />

  <wd-cell-group v-else border custom-class="!mt-[20rpx]">
    <wd-cell
      v-for="d in devices" :key="d.deviceId" :title="d.model || d.deviceId"
      :label="d.deviceId" is-link clickable
      @click="uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${d.deviceId}` })"
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

  <wd-popup v-model="showBind" position="bottom" custom-style="border-radius:32rpx 32rpx 0 0;padding:40rpx">
    <wd-text :text="t('v2.deviceList.addDevice')" size="32rpx" bold custom-class="!text-center !mb-[24rpx]" />
    <wd-input v-model="bindSn" :placeholder="t('v2.deviceList.enterSn')" clearable custom-cell-class="!mb-[20rpx]" />
    <wd-input v-model="bindCode" :placeholder="t('v2.deviceList.enterCode')" clearable />
    <view class="flex gap-[20rpx] mt-[40rpx]">
      <wd-button type="default" block round @click="showBind = false">{{ t('v2.deviceList.cancel') }}</wd-button>
      <wd-button type="primary" block round :loading="bindLoading" @click="handleBind">{{ t('v2.deviceList.confirm') }}</wd-button>
    </view>
  </wd-popup>
</template>
