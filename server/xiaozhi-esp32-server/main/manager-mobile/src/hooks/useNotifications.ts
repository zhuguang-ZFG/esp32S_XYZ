import type { V2NotificationSubscription } from '@/api/v2'
import { ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { v2ListNotificationSubscriptions, v2SubscribeNotifications, v2UnsubscribeNotification } from '@/api/v2'
import { t } from '@/i18n'

// 微信订阅消息模板
const NOTIFICATION_TEMPLATES = [
  'task_completed',
  'task_failed',
  'device_offline',
  'firmware_update',
]

/**
 * 通知订阅管理 composable（P2-19 从 settings/index.vue 提取）
 */
export function useNotifications() {
  const toast = useToast()
  const notificationSubs = ref<V2NotificationSubscription[]>([])
  const notificationLoading = ref(false)
  const notificationEnabled = ref(false)

  async function loadNotificationSubs() {
    try {
      notificationSubs.value = await v2ListNotificationSubscriptions()
      notificationEnabled.value = notificationSubs.value.some(s => s.status === 'active')
    }
    catch {
      notificationSubs.value = []
    }
  }

  async function handleToggleNotifications() {
    if (notificationLoading.value)
      return
    notificationLoading.value = true
    try {
      if (notificationEnabled.value) {
        // 关闭：取消所有活跃订阅
        for (const sub of notificationSubs.value) {
          if (sub.status === 'active')
            await v2UnsubscribeNotification(sub.subscriptionId)
        }
        notificationEnabled.value = false
        toast.success(t('settings.notificationsOff'))
      }
      else {
        // 开启：请求微信订阅授权 → 后端订阅
        // #ifdef MP-WEIXIN
        const reqTemplateIds = NOTIFICATION_TEMPLATES.map(tid => `tmpl_${tid}`)
        const wxRes = await uni.requestSubscribeMessage({ tmplIds: reqTemplateIds })
        const accepted = reqTemplateIds.filter(tid => (wxRes as any)[tid] === 'accept')
        if (!accepted.length) {
          toast.warning(t('settings.notificationsRejected'))
          return
        }
        const openid = uni.getStorageSync('openid') || ''
        if (!openid) {
          toast.warning(t('settings.notificationsNeedLogin'))
          return
        }
        const devices = uni.getStorageSync('device_ids') || []
        if (!devices.length) {
          toast.warning(t('settings.notificationsNoDevices'))
          return
        }
        await v2SubscribeNotifications(openid, accepted, devices)
        notificationEnabled.value = true
        toast.success(t('settings.notificationsOn'))
        // #endif
        // #ifndef MP-WEIXIN
        toast.info(t('settings.notificationsMpOnly'))
        // #endif
        await loadNotificationSubs()
      }
    }
    catch (e: any) {
      console.error('toggle notifications failed:', e)
      toast.error(e?.message || t('settings.notificationsFailed'))
    }
    finally {
      notificationLoading.value = false
    }
  }

  return {
    notificationEnabled,
    notificationLoading,
    loadNotificationSubs,
    handleToggleNotifications,
  }
}
