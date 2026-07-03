import { t } from '@/i18n'

/**
 * 首页导航（D2 从 pages/index/index.vue 提取）。
 *
 * 集中管理 8 个跳转入口。数字人尚未上线，仅弹 toast 提示。
 */
export function useHomeNavigation() {
  function goChat() {
    uni.navigateTo({ url: '/pages/chat/chat' })
  }
  function goDraw() {
    uni.navigateTo({ url: '/pages/create/ai-draw' })
  }
  function goImageDraw() {
    uni.navigateTo({ url: '/pages/create/image-draw' })
  }
  function goDigitalHuman() {
    uni.showToast({ title: t('nebula.digitalHumanComingSoon'), icon: 'none' })
  }
  function goDevices() {
    uni.switchTab({ url: '/pages/v2/device-list/index' })
  }
  function goDeviceDetail(id: string) {
    uni.navigateTo({ url: `/pages/v2/device-detail/index?deviceId=${id}` })
  }
  function goConfig() {
    uni.switchTab({ url: '/pages/device-config/index' })
  }
  function goSettings() {
    uni.switchTab({ url: '/pages/settings/index' })
  }

  return { goChat, goDraw, goImageDraw, goDigitalHuman, goDevices, goDeviceDetail, goConfig, goSettings }
}
