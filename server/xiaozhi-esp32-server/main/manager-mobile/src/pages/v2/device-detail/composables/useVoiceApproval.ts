import type { V2PendingVoiceTaskResponse } from '@/api/v2/types'
import { computed, ref } from 'vue'
import { v2ApproveVoiceTask, v2ListPendingVoiceTasks, v2RejectVoiceTask } from '@/api/v2'
import { t } from '@/i18n'
import { updateM6PendingTabBarBadge } from '@/utils'

interface VoiceprintConstraint {
  matched?: boolean
  reason?: string
  member_id?: number | string
  memberId?: number | string
  display_name?: string
  displayName?: string
  member_type?: string
  memberType?: string
  speaker_ref?: string
  speakerRef?: string
  reenroll_hint?: boolean
  reenrollHint?: boolean
}

export function useVoiceApproval(
  deviceId: () => string,
  appendLog: (msg: string) => void,
  message: any,
  taskSubmitErrorMessage: (e: any) => string,
) {
  const pendingVoiceTasks = ref<V2PendingVoiceTaskResponse[]>([])
  const voiceApprovalLoading = ref(false)
  // M16:per-task loading,单个任务审批不再让全列表按钮转圈
  const voiceTaskActionLoading = ref<Record<string, boolean>>({})
  const pendingVoiceApprovalCount = computed(() => pendingVoiceTasks.value.length)
  const pendingVoiceApprovalBadgeText = computed(() => String(pendingVoiceApprovalCount.value))

  function parseJsonObject(value: string | undefined) {
    if (!value)
      return null
    try {
      const p = JSON.parse(value)
      return p && typeof p === 'object' && !Array.isArray(p) ? p as Record<string, any> : null
    }
    catch { console.warn('parseJsonObject failed'); return null }
  }

  function voiceprintConstraintForTask(task: V2PendingVoiceTaskResponse): VoiceprintConstraint | null {
    const constraints = parseJsonObject(task.constraintsJson)
    const vp = constraints?.voiceprint
    return vp && typeof vp === 'object' && !Array.isArray(vp) ? vp as VoiceprintConstraint : null
  }

  function voiceprintApprovalLabel(task: V2PendingVoiceTaskResponse) {
    const vp = voiceprintConstraintForTask(task)
    if (!vp)
      return t('v2.detail.noVoiceprintMeta')
    const displayName = vp.display_name || vp.displayName || ''
    const memberType = vp.member_type || vp.memberType || ''
    if (voiceprintReenrollRequired(task))
      return `${t('v2.detail.voiceprintMatched')} ${displayName || 'child'}, ${t('v2.detail.reenrollNeeded')}`
    if (vp.matched)
      return `${t('v2.detail.voiceprintMatched')} ${displayName || t('v2.detail.registeredMember')}${memberType ? ` (${memberType})` : ''}`
    if (vp.reason === 'child_unknown_allowed')
      return t('v2.detail.unknownChildAllowed')
    if (vp.reason === 'unknown_allowed')
      return t('v2.detail.unknownAllowed')
    return `${t('v2.detail.voiceprint')}: ${vp.reason}`
  }

  function voiceprintReenrollRequired(task: V2PendingVoiceTaskResponse) {
    const vp = voiceprintConstraintForTask(task)
    return Boolean(vp?.reenroll_hint || vp?.reenrollHint || vp?.reason === 'child_reenroll_required')
  }

  function voiceprintHasUnknownSpeaker(task: V2PendingVoiceTaskResponse) {
    const reason = voiceprintConstraintForTask(task)?.reason
    return reason === 'child_unknown_allowed' || reason === 'unknown_allowed'
  }

  async function loadPendingVoiceTasks() {
    const did = deviceId()
    if (!did)
      return
    voiceApprovalLoading.value = true
    try {
      pendingVoiceTasks.value = await v2ListPendingVoiceTasks(did)
      updateM6PendingTabBarBadge('voiceApproval', pendingVoiceTasks.value.length)
      appendLog(`pending voice approvals: ${pendingVoiceTasks.value.length}`)
    }
    catch (e: any) {
      pendingVoiceTasks.value = []
      updateM6PendingTabBarBadge('voiceApproval', 0)
      appendLog(`pending voice approvals error: ${String(e?.message || e).slice(0, 80)}`)
    }
    finally { voiceApprovalLoading.value = false }
  }

  async function handleApproveVoiceTask(taskId: string) {
    if (voiceTaskActionLoading.value[taskId])
      return
    voiceTaskActionLoading.value[taskId] = true
    try {
      const r = await v2ApproveVoiceTask(taskId, 'approved from mobile')
      uni.showToast({ title: t('v2.detail.approved'), icon: 'none' })
      appendLog(`voice approve: ${r.taskId} ${r.status}`)
      await loadPendingVoiceTasks()
    }
    catch (e: any) { message.alert(taskSubmitErrorMessage(e)) }
    finally { voiceTaskActionLoading.value[taskId] = false }
  }

  async function handleRejectVoiceTask(taskId: string) {
    if (voiceTaskActionLoading.value[taskId])
      return
    // M16:拒绝是不可逆决定,加二次确认
    try {
      const confirmed = await message.confirm(t('v2.detail.rejectConfirm'))
      if (!confirmed)
        return
    }
    catch {
      return
    }
    voiceTaskActionLoading.value[taskId] = true
    try {
      const r = await v2RejectVoiceTask(taskId, 'rejected from mobile')
      uni.showToast({ title: t('v2.detail.rejected'), icon: 'none' })
      appendLog(`voice reject: ${r.taskId} ${r.status}`)
      await loadPendingVoiceTasks()
    }
    catch (e: any) { message.alert(taskSubmitErrorMessage(e)) }
    finally { voiceTaskActionLoading.value[taskId] = false }
  }

  return {
    pendingVoiceTasks,
    voiceApprovalLoading,
    voiceTaskActionLoading,
    pendingVoiceApprovalCount,
    pendingVoiceApprovalBadgeText,
    voiceprintApprovalLabel,
    voiceprintReenrollRequired,
    voiceprintHasUnknownSpeaker,
    loadPendingVoiceTasks,
    handleApproveVoiceTask,
    handleRejectVoiceTask,
  }
}
