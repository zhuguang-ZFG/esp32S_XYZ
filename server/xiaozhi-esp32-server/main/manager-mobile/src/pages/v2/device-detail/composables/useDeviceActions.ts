import type { ComputedRef } from 'vue'
import type { V2ShareResponse } from '@/api/v2'
import type { V2DeviceSupplyResponse, V2DeviceTransferResponse } from '@/api/v2/types'
import { computed, ref } from 'vue'
import type { GalleryImage } from '@/api/gallery'
import {
  v2AcceptDeviceTransfer,
  v2CancelDeviceTransfer,
  v2CreateShare,
  v2ListShares,
  v2RenderAsset,
  v2RequestDeviceTransfer,
  v2RevokeShare,
  v2SubmitTask,
  v2UnbindDevice,
  v2UpdateDeviceSupplies,
} from '@/api/v2'
import { t } from '@/i18n'

const defaultWriteTextFontId = 'kai_basic_v1'
const healthCheckPath = [
  { cmd: 'M', x: 5, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 5, z: 0 },
]

function taskSubmitErrorMessage(error: any) {
  const text = String(error?.message || error || '')
  if (text.includes('E_RUNTIME_STALE'))
    return t('v2.detail.errorRuntimeStale')
  if (text.includes('E_CONTENT_BLOCKED'))
    return t('v2.detail.errorContentBlocked')
  if (text.includes('E_INVALID_DRAWING'))
    return t('v2.detail.errorInvalidDrawing')
  if (text.includes('E_NOT_ENTITLED'))
    return t('v2.detail.errorNotEntitled')
  if (text.includes('E_NO_PAPER'))
    return t('v2.detail.errorNoPaper')
  return text || t('common.fail')
}

function showSubmitToast(key: string) {
  uni.showToast({ title: t(key), icon: 'none' })
}

/**
 * 设备详情页的任务派发 / 耗材 / 转移 / 分享 / 解绑动作（P3.1 从 index.vue 提取）。
 *
 * 依赖 useDeviceEvents 暴露的进度 setter（setPhase/resetProgress/startInfoLoadingTimer 等），
 * 从而与事件流共享同一份 latestPhase/infoLoading 状态，而非各存一份。
 */
export function useDeviceActions(opts: {
  deviceId: () => string
  message: any
  appendLog: (msg: string) => void
  setPhase: (phase: string) => void
  resetProgress: () => void
  infoLoading: { value: boolean }
  healthCheckLoading: { value: boolean }
  isDeviceBusy: ComputedRef<boolean>
  startInfoLoadingTimer: () => void
  clearInfoLoadingTimer: () => void
  applyRuntimeStatus: (deviceId: string) => Promise<void>
}) {
  const { deviceId, message, appendLog, setPhase, resetProgress, infoLoading, healthCheckLoading, isDeviceBusy, startInfoLoadingTimer, clearInfoLoadingTimer, applyRuntimeStatus } = opts

  const homeLoading = ref(false)
  const writeTextInput = ref('你好')
  const writeTextLoading = ref(false)
  const drawGeneratedLoading = ref(false)
  const drawFromImageLoading = ref(false)
  const suppliesLoading = ref(false)
  const transferLoading = ref(false)
  const drawPromptInput = ref('星星')
  const deviceSupplies = ref<V2DeviceSupplyResponse | null>(null)
  const deviceTransfer = ref<V2DeviceTransferResponse | null>(null)
  const transferTargetPhone = ref('')
  const transferAcceptId = ref('')
  const shares = ref<V2ShareResponse[]>([])
  const shareLoading = ref(false)
  const sharePermission = ref('view')
  const shareExpiry = ref('7d')
  const unbindLoading = ref(false)

  const starterAssets = [
    { id: 'starter_star', label: '星星' },
    { id: 'starter_house', label: '小房子' },
    { id: 'starter_tree', label: '树' },
    { id: 'starter_fish', label: '鱼' },
    { id: 'starter_flower', label: '花' },
  ]

  const paperSlotStateLabel = computed(() => {
    const s = deviceSupplies.value?.paperSlotState || 'unknown'
    return s === 'loaded' ? t('v2.detail.paperLoaded') : s === 'empty' ? t('v2.detail.paperEmpty') : t('v2.detail.unknown')
  })
  const penStateLabel = computed(() => {
    if (!deviceSupplies.value?.penInstalledAt)
      return t('v2.detail.noPenRecord')
    return `${t('v2.detail.inkEstimate')} ${deviceSupplies.value.penInkPercentEst ?? 100}%`
  })
  const transferStateLabel = computed(() => {
    if (!deviceTransfer.value)
      return t('v2.detail.noTransferPending')
    return `#${deviceTransfer.value.transferId} ${deviceTransfer.value.status}`
  })

  async function handleHome() {
    if (isDeviceBusy.value) {
      showSubmitToast('v2.detail.deviceBusy')
      return
    }
    homeLoading.value = true
    try {
      const r = await v2SubmitTask(deviceId(), 'home')
      showSubmitToast('v2.detail.homeSubmitted')
      appendLog(`home: ${r.taskId}`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      homeLoading.value = false
    }
  }

  async function handleWriteText() {
    if (isDeviceBusy.value) {
      showSubmitToast('v2.detail.deviceBusy')
      return
    }
    const text = writeTextInput.value.trim()
    if (!text) {
      message.alert(t('v2.detail.enterWriteText'))
      return
    }
    writeTextLoading.value = true
    try {
      const r = await v2SubmitTask(deviceId(), 'write_text', { text, font_id: defaultWriteTextFontId })
      setPhase(r.status)
      resetProgress()
      showSubmitToast('v2.detail.writeSubmitted')
      appendLog(`write_text: ${r.taskId}`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      writeTextLoading.value = false
    }
  }

  async function submitDraw(params: Record<string, unknown>, label: string) {
    if (isDeviceBusy.value) {
      showSubmitToast('v2.detail.deviceBusy')
      return
    }
    drawGeneratedLoading.value = true
    try {
      const r = await v2SubmitTask(deviceId(), 'draw_generated', params)
      setPhase(r.status)
      resetProgress()
      showSubmitToast('v2.detail.drawSubmitted')
      appendLog(`${label}: ${r.taskId}`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      drawGeneratedLoading.value = false
    }
  }
  async function handleDrawPrompt() {
    const p = drawPromptInput.value.trim()
    if (!p) {
      message.alert(t('v2.detail.enterDrawPrompt'))
      return
    }
    await submitDraw({ prompt: p }, 'draw_generated')
  }

  async function handleDrawFromImage(image: GalleryImage) {
    if (isDeviceBusy.value) {
      showSubmitToast('v2.detail.deviceBusy')
      return
    }
    drawFromImageLoading.value = true
    try {
      const r = await v2SubmitTask(deviceId(), 'draw_generated', {
        gallery_image_id: image.id,
        prompt: '',
      })
      setPhase(r.status)
      resetProgress()
      showSubmitToast('v2.detail.galleryDrawSubmitted')
      appendLog(`draw_generated(gallery): ${r.taskId}`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      drawFromImageLoading.value = false
    }
  }

  async function handleDrawStarter(id: string) {
    if (isDeviceBusy.value) {
      showSubmitToast('v2.detail.deviceBusy')
      return
    }
    drawGeneratedLoading.value = true
    try {
      const r = await v2RenderAsset(id, deviceId())
      setPhase(r.status)
      resetProgress()
      showSubmitToast('v2.detail.drawSubmitted')
      appendLog(`draw_starter ${id}: ${r.taskId}`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      drawGeneratedLoading.value = false
    }
  }

  async function handleRefreshInfo() {
    infoLoading.value = true
    clearInfoLoadingTimer()
    try {
      await applyRuntimeStatus(deviceId())
      showSubmitToast('v2.detail.infoSubmitted')
      appendLog('refresh device status')
    }
    catch (e: any) {
      infoLoading.value = false
      message.alert(taskSubmitErrorMessage(e))
    }
  }

  async function handleHealthCheck() {
    if (isDeviceBusy.value) {
      showSubmitToast('v2.detail.deviceBusy')
      return
    }
    healthCheckLoading.value = true
    try {
      const r = await v2SubmitTask(deviceId(), 'run_path', { path: healthCheckPath, feed: 900 })
      setPhase(r.status)
      resetProgress()
      showSubmitToast('v2.detail.healthCheckSubmitted')
      appendLog(`health_check: ${r.taskId}`)
    }
    catch (e: any) {
      healthCheckLoading.value = false
      message.alert(taskSubmitErrorMessage(e))
    }
  }

  async function updatePaper(state: 'empty' | 'loaded' | 'unknown') {
    suppliesLoading.value = true
    try {
      deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId(), { paperSlotState: state })
      showSubmitToast(state === 'loaded' ? 'v2.detail.paperMarkedLoaded' : state === 'empty' ? 'v2.detail.paperMarkedEmpty' : 'v2.detail.paperMarkedUnknown')
      appendLog(`supplies paper=${state}`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      suppliesLoading.value = false
    }
  }

  async function markNewPen() {
    suppliesLoading.value = true
    try {
      deviceSupplies.value = await v2UpdateDeviceSupplies(deviceId(), { penInstalledAt: new Date().toISOString(), penInkPercentEst: 100, resetPenMileage: true })
      showSubmitToast('v2.detail.penRecorded')
      appendLog('supplies pen installed')
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      suppliesLoading.value = false
    }
  }

  async function handleRequestTransfer() {
    const target = transferTargetPhone.value.trim()
    if (!target) {
      message.alert(t('v2.detail.enterTargetPhone'))
      return
    }
    transferLoading.value = true
    try {
      deviceTransfer.value = await v2RequestDeviceTransfer(deviceId(), { targetPhone: target })
      transferAcceptId.value = String(deviceTransfer.value.transferId)
      showSubmitToast('v2.detail.transferCreated')
      appendLog(`transfer id=${deviceTransfer.value.transferId}`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      transferLoading.value = false
    }
  }

  async function handleCancelTransfer() {
    const tid = transferAcceptId.value.trim() || String(deviceTransfer.value?.transferId || '') || null
    if (!tid) {
      message.alert(t('v2.detail.enterTransferId'))
      return
    }
    transferLoading.value = true
    try {
      deviceTransfer.value = await v2CancelDeviceTransfer(tid)
      showSubmitToast('v2.detail.transferCancelled')
      appendLog(`transfer cancelled`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      transferLoading.value = false
    }
  }

  async function handleAcceptTransfer() {
    const tid = transferAcceptId.value.trim() || String(deviceTransfer.value?.transferId || '') || null
    if (!tid) {
      message.alert(t('v2.detail.enterTransferId'))
      return
    }
    transferLoading.value = true
    try {
      deviceTransfer.value = await v2AcceptDeviceTransfer(tid)
      showSubmitToast('v2.detail.transferAccepted')
      appendLog(`transfer accepted`)
    }
    catch (e: any) {
      message.alert(taskSubmitErrorMessage(e))
    }
    finally {
      transferLoading.value = false
    }
  }

  async function loadShares() {
    if (!deviceId())
      return
    try {
      shares.value = await v2ListShares(deviceId())
    }
    catch (e: any) {
      console.warn('load shares failed:', e?.message || e)
    }
  }

  async function handleCreateShare() {
    if (!deviceId())
      return
    shareLoading.value = true
    try {
      const days = Number.parseInt(shareExpiry.value.replace('d', ''), 10) || 7
      const expiresAt = new Date(Date.now() + days * 86400000).toISOString()
      await v2CreateShare(deviceId(), sharePermission.value, expiresAt)
      uni.showToast({ title: t('v2.detail.shareCreated'), icon: 'success' })
      await loadShares()
    }
    catch (e: any) {
      message.alert(e?.message || t('v2.detail.shareFailed'))
    }
    finally {
      shareLoading.value = false
    }
  }

  async function handleRevokeShare(shareToken: string) {
    if (!deviceId())
      return
    shareLoading.value = true
    try {
      await v2RevokeShare(deviceId(), shareToken)
      uni.showToast({ title: t('v2.detail.shareRevoked'), icon: 'success' })
      await loadShares()
    }
    catch (e: any) {
      message.alert(e?.message || t('v2.detail.revokeFailed'))
    }
    finally {
      shareLoading.value = false
    }
  }

  async function handleUnbind() {
    if (!deviceId())
      return
    uni.vibrateShort({ type: 'medium' })
    try {
      const confirmed = await message.confirm(t('v2.detail.unbindConfirm'))
      if (!confirmed)
        return
    }
    catch {
      return
    }
    unbindLoading.value = true
    try {
      await v2UnbindDevice(deviceId())
      uni.showToast({ title: t('v2.detail.unbindSuccess'), icon: 'success' })
      setTimeout(() => uni.navigateBack(), 1000)
    }
    catch (e: any) {
      message.alert(e?.message || t('v2.detail.unbindFailed'))
    }
    finally {
      unbindLoading.value = false
    }
  }

  return {
    homeLoading,
    writeTextInput,
    writeTextLoading,
    drawGeneratedLoading,
    drawFromImageLoading,
    suppliesLoading,
    transferLoading,
    drawPromptInput,
    deviceSupplies,
    deviceTransfer,
    transferTargetPhone,
    transferAcceptId,
    shares,
    shareLoading,
    sharePermission,
    shareExpiry,
    unbindLoading,
    starterAssets,
    defaultWriteTextFontId,
    paperSlotStateLabel,
    penStateLabel,
    transferStateLabel,
    taskSubmitErrorMessage,
    handleHome,
    handleWriteText,
    handleDrawPrompt,
    handleDrawStarter,
    handleDrawFromImage,
    handleRefreshInfo,
    handleHealthCheck,
    updatePaper,
    markNewPen,
    handleRequestTransfer,
    handleCancelTransfer,
    handleAcceptTransfer,
    loadShares,
    handleCreateShare,
    handleRevokeShare,
    handleUnbind,
  }
}
