import { computed, onMounted, ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { SOFTAP_PROBE_TIMEOUT_MS, SOFTAP_SCAN_TIMEOUT_MS } from '@/config/timeouts'
import { t } from '@/i18n'
import { provisioningContract, softApUrl } from '../provisioning-contract'

// 类型定义
export interface WiFiNetwork {
  ssid: string
  rssi: number
  authmode: number
  channel: number
}

export function getSignalStrength(rssi: number): string {
  if (rssi >= -50)
    return t('deviceConfig.signalStrong')
  if (rssi >= -60)
    return t('deviceConfig.signalGood')
  if (rssi >= -70)
    return t('deviceConfig.signalFair')
  return t('deviceConfig.signalWeak')
}

export function getSignalColor(rssi: number): string {
  if (rssi >= -50)
    return '#52c41a'
  if (rssi >= -60)
    return '#73d13d'
  if (rssi >= -70)
    return '#faad14'
  return '#ff4d4f'
}

export function useWifiSelector(autoConnect: boolean, emit: any) {
  const toast = useToast()

  const isConnectedToESP32 = ref(false)
  const checkingConnection = ref(false)
  const scanning = ref(false)
  const wifiNetworks = ref<WiFiNetwork[]>([])
  const selectedNetwork = ref<WiFiNetwork | null>(null)
  const password = ref('')
  const selectorExpanded = ref(false)

  const networkDisplayText = computed(() => {
    if (!selectedNetwork.value)
      return t('deviceConfig.selectWifiNetwork')
    return selectedNetwork.value.ssid
  })

  async function checkESP32Connection() {
    checkingConnection.value = true
    try {
      const response = await uni.request({
        url: softApUrl(provisioningContract.softApScanPath),
        method: 'GET',
        timeout: SOFTAP_PROBE_TIMEOUT_MS,
      })
      isConnectedToESP32.value = response.statusCode === 200
      emit('connection-status', isConnectedToESP32.value)
    }
    catch {
      isConnectedToESP32.value = false
      emit('connection-status', false)
    }
    finally {
      checkingConnection.value = false
    }
  }

  async function scanWifi() {
    if (!isConnectedToESP32.value) {
      toast.error(t('deviceConfig.connectDlcHotspot'))
      return
    }

    scanning.value = true

    try {
      const response = await uni.request({
        url: softApUrl(provisioningContract.softApScanPath),
        method: 'GET',
        timeout: SOFTAP_SCAN_TIMEOUT_MS,
      })

      if (response.statusCode === 200 && response.data) {
        const data = response.data as any
        if (data.success && Array.isArray(data.networks)) {
          wifiNetworks.value = data.networks
        }
        else if (data.aps && Array.isArray(data.aps)) {
          wifiNetworks.value = data.aps.map((item: any) => ({
            ssid: item.ssid,
            rssi: item.rssi,
            authmode: item.authmode,
            channel: item.channel || 0,
          }))
        }
        else if (Array.isArray(response.data)) {
          wifiNetworks.value = response.data.map((item: any) => ({
            ssid: item.ssid,
            rssi: item.rssi,
            authmode: item.authmode,
            channel: item.channel || 0,
          }))
        }
        else {
          throw new TypeError('扫描接口返回格式异常')
        }
      }
      else {
        throw new Error(`HTTP ${response.statusCode}`)
      }
    }
    catch (error) {
      console.error(`${t('deviceConfig.wifiScanFailed')}:`, error)
      toast.error(t('deviceConfig.scanFailedCheckConnection'))
    }
    finally {
      scanning.value = false
    }
  }

  async function showNetworkSelector() {
    await checkESP32Connection()

    if (!isConnectedToESP32.value) {
      toast.error(t('deviceConfig.connectDlcHotspot'))
      return
    }

    selectorExpanded.value = true

    if (wifiNetworks.value.length === 0) {
      scanWifi()
    }
  }

  function selectNetwork(network: WiFiNetwork) {
    selectedNetwork.value = network
    password.value = ''
    selectorExpanded.value = false
    emit('network-selected', network, '')
  }

  function onPasswordChange() {
    emit('network-selected', selectedNetwork.value, password.value)
  }

  function getSelectedNetworkInfo() {
    return {
      network: selectedNetwork.value,
      password: password.value,
    }
  }

  function reset() {
    selectedNetwork.value = null
    password.value = ''
    wifiNetworks.value = []
    selectorExpanded.value = false
    emit('network-selected', null, '')
  }

  function getSignalStrength(rssi: number): string {
    if (rssi >= -50)
      return t('deviceConfig.signalStrong')
    if (rssi >= -60)
      return t('deviceConfig.signalGood')
    if (rssi >= -70)
      return t('deviceConfig.signalFair')
    return t('deviceConfig.signalWeak')
  }

  function getSignalColor(rssi: number): string {
    if (rssi >= -50)
      return '#52c41a'
    if (rssi >= -60)
      return '#73d13d'
    if (rssi >= -70)
      return '#faad14'
    return '#ff4d4f'
  }

  onMounted(() => {
    if (autoConnect) {
      checkESP32Connection()
    }
  })

  return {
    isConnectedToESP32,
    checkingConnection,
    scanning,
    wifiNetworks,
    selectedNetwork,
    password,
    selectorExpanded,
    networkDisplayText,
    checkESP32Connection,
    scanWifi,
    showNetworkSelector,
    selectNetwork,
    onPasswordChange,
    getSelectedNetworkInfo,
    reset,
  }
}