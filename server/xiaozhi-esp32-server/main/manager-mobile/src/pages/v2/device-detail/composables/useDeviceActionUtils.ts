import type { V2SubmitTaskResponse } from '@/api/v2/types'
import { t } from '@/i18n'

export const defaultWriteTextFontId = 'kai_basic_v1'
export const healthCheckPath = [
  { cmd: 'M', x: 5, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 5, z: 0 },
  { cmd: 'L', x: 25, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 25, z: 0 },
  { cmd: 'L', x: 5, y: 5, z: 0 },
]

export function taskSubmitErrorMessage(error: any) {
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

export function showSubmitToast(key: string) {
  uni.showToast({ title: t(key), icon: 'none' })
}

export function noteTaskDeliveryHonesty(result: V2SubmitTaskResponse) {
  if (result.deliveryAvailable !== false || !result.message)
    return
  uni.showModal({
    title: t('v2.detail.deliveryUnavailableTitle'),
    content: result.message,
    showCancel: false,
  })
}