import { reactive } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { getServerBaseUrlOverride, setServerBaseUrlOverride } from '@/utils'
import { t } from '@/i18n'

/**
 * 缓存管理 composable（P2-19 从 settings/index.vue 提取）
 */
export function useCacheManager() {
  const toast = useToast()
  const cacheInfo = reactive({ storageSize: '0MB' })

  function getCacheInfo() {
    try {
      const info = uni.getStorageInfoSync()
      const totalSize = (info.currentSize || 0) / 1024 // KB to MB
      cacheInfo.storageSize = `${totalSize.toFixed(2)}MB`
    }
    catch (error) {
      console.error('获取缓存信息失败:', error)
    }
  }

  /** 切换地址或清除缓存时：完全清空存储，保留 server URL override */
  function clearAllCacheAfterUrlChange() {
    try {
      const preservedOverride = getServerBaseUrlOverride()
      uni.clearStorageSync()
      // #ifdef H5
      if (typeof localStorage !== 'undefined')
        localStorage.clear()
      // #endif
      if (preservedOverride)
        setServerBaseUrlOverride(preservedOverride)
      getCacheInfo()
    }
    catch (error) {
      console.error('清除缓存失败:', error)
    }
  }

  function clearCache() {
    uni.showModal({
      title: t('settings.confirmClear'),
      content: t('settings.confirmClearMessage'),
      confirmText: t('common.confirm'),
      cancelText: t('common.cancel'),
      success: (res) => {
        if (res.confirm) {
          clearAllCacheAfterUrlChange()
          toast.success(t('settings.cacheCleared'))
          setTimeout(() => {
            uni.reLaunch({ url: '/pages/v2/login/index' })
          }, 1500)
        }
      },
    })
  }

  return {
    cacheInfo,
    getCacheInfo,
    clearAllCacheAfterUrlChange,
    clearCache,
  }
}
