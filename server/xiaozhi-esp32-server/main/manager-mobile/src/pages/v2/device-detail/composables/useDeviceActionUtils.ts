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

/**
 * 任务提交错误 → 用户可读文案。
 * 后端错误 envelope 统一为 {code, message}，HTTP 层（alova.ts）已透出后端 message，
 * 此处直接展示；历史 E_RUNTIME_STALE/E_CONTENT_BLOCKED/E_INVALID_DRAWING/
 * E_NOT_ENTITLED/E_NO_PAPER 码后端从未返回（2026-07-20 审查确认为死代码，已删除映射）。
 */
export function taskSubmitErrorMessage(error: any) {
  const text = String(error?.message || error || '')
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