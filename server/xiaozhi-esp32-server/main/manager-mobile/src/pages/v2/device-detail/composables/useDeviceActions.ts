import type { ComputedRef } from 'vue'
import type { GalleryImage } from '@/api/gallery'
import type { V2DeviceSupplyResponse } from '@/api/v2/types'
import {
  v2RenderAsset,
  v2SubmitTask,
  v2UpdateDeviceSupplies,
} from '@/api/v2'
import { computed, ref } from 'vue'
import { t } from '@/i18n'
import {
  defaultWriteTextFontId,
  healthCheckPath,
  noteTaskDeliveryHonesty,
  showSubmitToast,
  taskSubmitErrorMessage,
} from './useDeviceActionUtils'
import { useDeviceTransferAndShare } from './useDeviceTransferAndShare'

/**
 * 设备详情页的任务派发 / 耗材动作（P3.1 从 index.vue 提取）。
 * 转移 / 分享 / 解绑由 useDeviceTransferAndShare 管理。
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
  const drawPromptInput = ref('星星')
  const deviceSupplies = ref<V2DeviceSupplyResponse | null>(null)

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

  async function handleHome() {
    if (isDeviceBusy.value) {
      showSubmitToast('v2.detail.deviceBusy')
      return
    }
    homeLoading.value = true
    try {
      const r = await v2SubmitTask(deviceId(), 'home')
      noteTaskDeliveryHonesty(r)
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
      noteTaskDeliveryHonesty(r)
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
      noteTaskDeliveryHonesty(r)
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
      noteTaskDeliveryHonesty(r)
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

  const ts = useDeviceTransferAndShare({ deviceId, message, appendLog })

  return {
    homeLoading,
    writeTextInput,
    writeTextLoading,
    drawGeneratedLoading,
    drawFromImageLoading,
    suppliesLoading,
    drawPromptInput,
    deviceSupplies,
    starterAssets,
    defaultWriteTextFontId,
    paperSlotStateLabel,
    penStateLabel,
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
    ...ts,
  }
}

// Re-export for backward compat in index.vue
export type { V2DeviceSupplyResponse } from '@/api/v2/types'