import { ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { v2DeleteAccount } from '@/api/v2'
import { t } from '@/i18n'

/**
 * 账号注销管理 composable（P2-19 从 settings/index.vue 提取）
 */
export function useAccountDeletion(onDeleted: () => void) {
  const toast = useToast()
  const accountDeleteLoading = ref(false)

  function handleAccountDeletion() {
    if (accountDeleteLoading.value)
      return
    uni.showModal({
      title: t('settings.deleteConfirmTitle'),
      content: t('settings.deleteConfirmContent'),
      confirmText: t('settings.deleteConfirmContinue'),
      cancelText: t('common.cancel'),
      success: (first) => {
        if (!first.confirm)
          return
        uni.showModal({
          title: t('settings.deleteSecondConfirmTitle'),
          content: t('settings.deleteSecondConfirmContent'),
          confirmText: t('settings.deleteSecondConfirmAction'),
          cancelText: t('common.cancel'),
          success: async (second) => {
            if (!second.confirm)
              return
            await submitAccountDeletion()
          },
        })
      },
    })
  }

  async function submitAccountDeletion() {
    accountDeleteLoading.value = true
    try {
      const response = await v2DeleteAccount()
      onDeleted()
      toast.success(t('settings.accountDeleted', { days: response.auditRetentionDays }))
      setTimeout(() => {
        uni.reLaunch({ url: '/pages/v2/login/index' })
      }, 800)
    }
    catch (error) {
      console.error('delete account failed:', error)
      toast.error(t('settings.deleteFailed'))
    }
    finally {
      accountDeleteLoading.value = false
    }
  }

  return {
    accountDeleteLoading,
    handleAccountDeletion,
  }
}
