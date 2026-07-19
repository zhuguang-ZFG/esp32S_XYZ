import type { ComputedRef } from 'vue'
import type { V2ShareResponse } from '@/api/v2'
import type { V2DeviceTransferResponse } from '@/api/v2/types'
import { computed, ref } from 'vue'
import {
  v2AcceptDeviceTransfer,
  v2CancelDeviceTransfer,
  v2CreateShare,
  v2ListShares,
  v2RequestDeviceTransfer,
  v2RevokeShare,
  v2UnbindDevice,
} from '@/api/v2'
import { t } from '@/i18n'
import { taskSubmitErrorMessage } from './useDeviceActionUtils'

export function useDeviceTransferAndShare(opts: {
  deviceId: () => string
  message: any
  appendLog: (msg: string) => void
}) {
  const { deviceId, message, appendLog } = opts

  const transferLoading = ref(false)
  const deviceTransfer = ref<V2DeviceTransferResponse | null>(null)
  const transferTargetPhone = ref('')
  const transferAcceptId = ref('')
  const shares = ref<V2ShareResponse[]>([])
  const shareLoading = ref(false)
  const sharePermission = ref('view')
  const shareExpiry = ref('7d')
  const unbindLoading = ref(false)

  const transferStateLabel = computed(() => {
    if (!deviceTransfer.value)
      return t('v2.detail.noTransferPending')
    return `#${deviceTransfer.value.transferId} ${deviceTransfer.value.status}`
  })

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
    transferLoading,
    deviceTransfer,
    transferTargetPhone,
    transferAcceptId,
    shares,
    shareLoading,
    sharePermission,
    shareExpiry,
    unbindLoading,
    transferStateLabel,
    handleRequestTransfer,
    handleCancelTransfer,
    handleAcceptTransfer,
    loadShares,
    handleCreateShare,
    handleRevokeShare,
    handleUnbind,
  }
}

function showSubmitToast(key: string) {
  uni.showToast({ title: t(key), icon: 'none' })
}