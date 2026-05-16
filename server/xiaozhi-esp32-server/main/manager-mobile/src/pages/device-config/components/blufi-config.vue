<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { toast } from '@/utils/toast'
import {
  createDesignTimeBlufiCredentialPayload,
  matchesBleUuid,
  provisioningContract,
} from '../provisioning-contract'

interface WiFiNetwork {
  ssid: string
  rssi: number
  authmode: number
  channel: number
}

interface Props {
  selectedNetwork?: WiFiNetwork | null
  password?: string
}

interface BlufiDevice {
  deviceId: string
  name: string
  RSSI?: number
}

const props = defineProps<Props>()
const bluetooth = uni as any

const scanning = ref(false)
const connecting = ref(false)
const submitting = ref(false)
const devices = ref<BlufiDevice[]>([])
const connectedDeviceId = ref('')
const connectedDeviceName = ref('')
const serviceId = ref('')
const writeCharacteristicId = ref('')
const notifyCharacteristicId = ref('')
const manualSsid = ref('')
const manualPassword = ref('')

const selectedSsid = computed(() => props.selectedNetwork?.ssid || manualSsid.value.trim())
const selectedPassword = computed(() => {
  if (props.selectedNetwork && props.selectedNetwork.authmode > 0)
    return props.password || manualPassword.value
  return manualPassword.value
})
const canSubmit = computed(() =>
  Boolean(connectedDeviceId.value && serviceId.value && writeCharacteristicId.value && selectedSsid.value),
)

function deviceName(device: any) {
  return device?.name || device?.localName || ''
}

function isBlufiDevice(device: any) {
  const name = deviceName(device)
  return name.includes(provisioningContract.blufiDeviceName)
    || name.includes(provisioningContract.legacyBlufiDeviceName)
}

function upsertDevice(device: any) {
  if (!device?.deviceId || !isBlufiDevice(device))
    return
  if (devices.value.some(item => item.deviceId === device.deviceId))
    return
  devices.value.push({
    deviceId: device.deviceId,
    name: deviceName(device),
    RSSI: device.RSSI,
  })
}

function handleBluetoothDeviceFound(result: any) {
  const found = Array.isArray(result?.devices) ? result.devices : [result]
  found.forEach(upsertDevice)
}

async function ensureBluetoothAdapter() {
  await bluetooth.openBluetoothAdapter()
}

async function startBlufiScan() {
  scanning.value = true
  devices.value = []
  try {
    await ensureBluetoothAdapter()
    bluetooth.offBluetoothDeviceFound?.(handleBluetoothDeviceFound)
    bluetooth.onBluetoothDeviceFound(handleBluetoothDeviceFound)
    await bluetooth.startBluetoothDevicesDiscovery({
      allowDuplicatesKey: false,
    })
  }
  catch (error) {
    console.error('BLE scan failed:', error)
    toast.error('BLE scan failed')
  }
  finally {
    scanning.value = false
  }
}

async function stopBlufiScan() {
  try {
    await bluetooth.stopBluetoothDevicesDiscovery()
  }
  catch (error) {
    console.log('BLE discovery already stopped:', error)
  }
}

function findWritableCharacteristic(characteristics: any[]) {
  return characteristics.find(item =>
    matchesBleUuid(item.uuid, provisioningContract.blufiWriteCharacteristicUuidCandidates),
  ) || characteristics.find(item => item.properties?.write || item.properties?.writeNoResponse)
}

function findNotifyCharacteristic(characteristics: any[]) {
  return characteristics.find(item =>
    matchesBleUuid(item.uuid, provisioningContract.blufiNotifyCharacteristicUuidCandidates),
  ) || characteristics.find(item => item.properties?.notify || item.properties?.indicate)
}

async function resolveBlufiGatt(deviceId: string) {
  const serviceResult = await bluetooth.getBLEDeviceServices({ deviceId })
  const services = serviceResult?.services || []
  const service = services.find((item: any) =>
    matchesBleUuid(item.uuid, provisioningContract.blufiServiceUuidCandidates),
  ) || services.find((item: any) => item.isPrimary) || services[0]
  if (!service)
    throw new Error('No BLE service found')

  const characteristicResult = await bluetooth.getBLEDeviceCharacteristics({
    deviceId,
    serviceId: service.uuid,
  })
  const characteristics = characteristicResult?.characteristics || []
  const writeCharacteristic = findWritableCharacteristic(characteristics)
  const notifyCharacteristic = findNotifyCharacteristic(characteristics)
  if (!writeCharacteristic)
    throw new Error('No writable BLE characteristic found')

  serviceId.value = service.uuid
  writeCharacteristicId.value = writeCharacteristic.uuid
  notifyCharacteristicId.value = notifyCharacteristic?.uuid || ''

  if (notifyCharacteristicId.value) {
    await bluetooth.notifyBLECharacteristicValueChange({
      deviceId,
      serviceId: serviceId.value,
      characteristicId: notifyCharacteristicId.value,
      state: true,
    })
  }
}

async function connectBlufiDevice(device: BlufiDevice) {
  connecting.value = true
  try {
    await stopBlufiScan()
    await bluetooth.createBLEConnection({
      deviceId: device.deviceId,
      timeout: 10000,
    })
    connectedDeviceId.value = device.deviceId
    connectedDeviceName.value = device.name
    await resolveBlufiGatt(device.deviceId)
    toast.success('BLE connected')
  }
  catch (error) {
    console.error('BLE connect failed:', error)
    toast.error('BLE connect failed')
  }
  finally {
    connecting.value = false
  }
}

async function submitBlufiConfig() {
  if (!canSubmit.value)
    return

  submitting.value = true
  try {
    await bluetooth.writeBLECharacteristicValue({
      deviceId: connectedDeviceId.value,
      serviceId: serviceId.value,
      characteristicId: writeCharacteristicId.value,
      value: createDesignTimeBlufiCredentialPayload({
        ssid: selectedSsid.value,
        password: selectedPassword.value,
      }),
    })
    toast.success('BLE provisioning payload sent')
  }
  catch (error) {
    console.error('BLE provisioning write failed:', error)
    toast.error('BLE provisioning failed')
  }
  finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => {
  bluetooth.offBluetoothDeviceFound?.(handleBluetoothDeviceFound)
  stopBlufiScan()
})
</script>

<template>
  <view class="blufi-config">
    <view class="manual-network">
      <wd-input
        v-model="manualSsid"
        label="SSID"
        placeholder="Wi-Fi SSID"
        clearable
      />
      <wd-input
        v-model="manualPassword"
        label="Password"
        placeholder="Wi-Fi password"
        show-password
        clearable
      />
    </view>

    <view class="ble-actions">
      <wd-button
        type="primary"
        :loading="scanning"
        @click="startBlufiScan"
      >
        Scan BLE
      </wd-button>
      <text v-if="connectedDeviceName" class="connected-name">
        {{ connectedDeviceName }}
      </text>
    </view>

    <view class="device-list">
      <view
        v-for="device in devices"
        :key="device.deviceId"
        class="device-item"
        @click="connectBlufiDevice(device)"
      >
        <text class="device-name">
          {{ device.name }}
        </text>
        <text class="device-rssi">
          {{ device.RSSI || 0 }} dBm
        </text>
      </view>
    </view>

    <wd-button
      type="primary"
      size="large"
      block
      :loading="submitting || connecting"
      :disabled="!canSubmit"
      @click="submitBlufiConfig"
    >
      Send BLE Config
    </wd-button>
  </view>
</template>

<style scoped>
.blufi-config {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 20rpx 0;
}

.manual-network {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.ble-actions {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.connected-name {
  min-width: 0;
  flex: 1;
  font-size: 24rpx;
  color: #2f7d32;
  word-break: break-all;
}

.device-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.device-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx;
  border: 1rpx solid #eeeeee;
  border-radius: 12rpx;
  background: #fbfbfb;
}

.device-item:active {
  border-color: #336cff;
  background: #eef3ff;
}

.device-name {
  min-width: 0;
  flex: 1;
  font-size: 28rpx;
  color: #232338;
  word-break: break-all;
}

.device-rssi {
  margin-left: 16rpx;
  font-size: 24rpx;
  color: #65686f;
}
</style>
