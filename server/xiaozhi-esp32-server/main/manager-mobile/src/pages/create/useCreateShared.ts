import type { V2DeviceInfo, V2TaskInfo } from '@/api/v2/types'
import { onShow } from '@dcloudio/uni-app'
import { onUnmounted, ref } from 'vue'
import { v2GetDevices, v2GetTask } from '@/api/v2'
import { t } from '@/i18n'

/**
 * AI 绘画两页共享：设备列表加载、任务轮询、SVG 预览弹窗、任务删除/清空。
 * ai-draw.vue 与 image-draw.vue 各自实例化一份（任务列表互不共享）。
 */
export function useCreateShared() {
  const safeAreaTop = ref(0)
  safeAreaTop.value = uni.getSystemInfoSync().statusBarHeight || 0

  const devices = ref<V2DeviceInfo[]>([])
  const selectedDeviceId = ref('')
  const submitting = ref(false)
  const tasks = ref<V2TaskInfo[]>([])
  const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

  const imageErrorMap = ref<Record<string, boolean>>({})
  function onImageError(taskId: string) {
    imageErrorMap.value[taskId] = true
  }

  const previewSvgContent = ref('')
  const showPreview = ref(false)
  function openPreview(svg: string) {
    previewSvgContent.value = svg
    showPreview.value = true
  }

  function previewImage(url: string) {
    uni.previewImage({ urls: [url], current: url })
  }

  function navigateBack() {
    uni.navigateBack()
  }

  async function loadDevices() {
    try {
      const res = await v2GetDevices()
      devices.value = res.rows || []
      const online = devices.value.find(d => d.status === 'online')
      selectedDeviceId.value = online?.deviceId || devices.value[0]?.deviceId || ''
    }
    catch (e) {
      console.error('loadDevices failed', e)
      uni.showToast({ title: t('create.loadDevicesFailed'), icon: 'none' })
    }
  }

  function startPolling(taskId: string) {
    if (pollTimer.value)
      clearInterval(pollTimer.value)
    let failCount = 0
    const MAX_POLL_FAILS = 5
    pollTimer.value = setInterval(async () => {
      try {
        const task = await v2GetTask(taskId)
        failCount = 0
        const idx = tasks.value.findIndex(t => t.taskId === taskId)
        if (idx < 0) {
          if (pollTimer.value)
            clearInterval(pollTimer.value)
          pollTimer.value = null
          return
        }
        tasks.value[idx] = { ...tasks.value[idx], ...task }
        if (['done', 'failed', 'cancelled', 'dead_letter', 'completed', 'error'].includes(task.status)) {
          if (pollTimer.value)
            clearInterval(pollTimer.value)
          pollTimer.value = null
        }
      }
      catch (e) {
        failCount++
        console.error('poll failed', e)
        if (failCount >= MAX_POLL_FAILS) {
          if (pollTimer.value)
            clearInterval(pollTimer.value)
          pollTimer.value = null
          uni.showToast({ title: t('create.pollFailed'), icon: 'none' })
        }
      }
    }, 3000)
  }

  function deleteTask(taskId: string) {
    uni.showModal({
      title: t('create.deleteConfirmTitle'),
      content: t('create.deleteConfirmContent'),
      success: (res) => {
        if (res.confirm) {
          tasks.value = tasks.value.filter(t => t.taskId !== taskId)
          uni.showToast({ title: t('create.deleted'), icon: 'success' })
        }
      },
    })
  }

  function clearAllTasks() {
    if (!tasks.value.length)
      return
    uni.showModal({
      title: t('create.clearConfirmTitle'),
      content: t('create.clearConfirmContent'),
      success: (res) => {
        if (res.confirm) {
          tasks.value = []
          uni.showToast({ title: t('create.cleared'), icon: 'success' })
        }
      },
    })
  }

  onShow(() => {
    loadDevices()
  })
  onUnmounted(() => {
    if (pollTimer.value)
      clearInterval(pollTimer.value)
  })

  return {
    safeAreaTop,
    devices,
    selectedDeviceId,
    submitting,
    tasks,
    imageErrorMap,
    previewSvgContent,
    showPreview,
    onImageError,
    openPreview,
    previewImage,
    navigateBack,
    loadDevices,
    startPolling,
    deleteTask,
    clearAllTasks,
  }
}