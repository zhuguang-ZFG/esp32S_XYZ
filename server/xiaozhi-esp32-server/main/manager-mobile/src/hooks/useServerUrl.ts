import { ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import {
  clearServerBaseUrlOverride,
  getEnvBaseUrl,
  getServerBaseUrlOverride,
  isValidServerBaseUrl,
  setServerBaseUrlOverride,
} from '@/utils'
import { HEALTH_CHECK_TIMEOUT_MS } from '@/config/timeouts'
import { t } from '@/i18n'

/**
 * 服务端地址管理 composable（P2-19 从 settings/index.vue 提取）
 */
export function useServerUrl(onUrlChanged: () => void) {
  const toast = useToast()
  const baseUrlInput = ref('')
  const urlError = ref('')

  function loadServerBaseUrl() {
    const override = getServerBaseUrlOverride()
    baseUrlInput.value = override || getEnvBaseUrl()
  }

  function validateUrl() {
    urlError.value = ''
    if (!baseUrlInput.value)
      return
    if (!isValidServerBaseUrl(baseUrlInput.value)) {
      urlError.value = t('settings.validServerUrl')
    }
  }

  async function testServerBaseUrl(): Promise<boolean> {
    urlError.value = ''
    if (!baseUrlInput.value || !isValidServerBaseUrl(baseUrlInput.value))
      return false

    try {
      const response = await uni.request({
        url: `${baseUrlInput.value.replace(/\/$/, '')}/health`,
        method: 'GET',
        timeout: HEALTH_CHECK_TIMEOUT_MS,
      })
      if (response.statusCode === 200)
        return true
      toast.error({ msg: t('message.invalidAddress'), duration: 3000 })
      return false
    }
    catch (error) {
      console.error('测试服务端地址失败:', error)
      toast.error({ msg: t('message.invalidAddress'), duration: 3000 })
      return false
    }
  }

  async function saveServerBaseUrl() {
    if (!baseUrlInput.value || !isValidServerBaseUrl(baseUrlInput.value)) {
      toast.warning(t('settings.validServerUrl'))
      return
    }
    const isServerValid = await testServerBaseUrl()
    if (!isServerValid)
      return
    setServerBaseUrlOverride(baseUrlInput.value)
    onUrlChanged()
    uni.showModal({
      title: t('settings.restartApp'),
      content: t('settings.serverUrlSavedAndCacheCleared'),
      confirmText: t('settings.restartNow'),
      cancelText: t('settings.restartLater'),
      success: (res) => {
        if (res.confirm)
          restartApp()
        else
          toast.success(t('settings.restartSuccess'))
      },
    })
  }

  function resetServerBaseUrl() {
    clearServerBaseUrlOverride()
    baseUrlInput.value = getEnvBaseUrl()
    onUrlChanged()
    uni.showModal({
      title: t('settings.restartApp'),
      content: t('settings.resetToDefaultAndCacheCleared'),
      confirmText: t('settings.restartNow'),
      cancelText: t('settings.restartLater'),
      success: (res) => {
        if (res.confirm)
          restartApp()
        else
          toast.success(t('settings.resetSuccess'))
      },
    })
  }

  function restartApp() {
    // #ifdef APP-PLUS
    plus.runtime.restart()
    // #endif
    // #ifndef APP-PLUS
    uni.reLaunch({ url: '/pages/v2/device-list/index' })
    // #endif
  }

  return {
    baseUrlInput,
    urlError,
    loadServerBaseUrl,
    validateUrl,
    saveServerBaseUrl,
    resetServerBaseUrl,
  }
}
