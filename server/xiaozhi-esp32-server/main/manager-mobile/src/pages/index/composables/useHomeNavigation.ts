/**
 * 首页导航（D2 从 pages/index/index.vue 提取）。
 *
 * 瘦身后：对话走小智云，不再需要 goChat/goDigitalHuman。
 */
export function useHomeNavigation() {
  function goDraw() {
    uni.navigateTo({ url: '/pages/create/ai-draw' })
  }
  function goImageDraw() {
    uni.navigateTo({ url: '/pages/create/image-draw' })
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

  return { goDraw, goImageDraw, goDevices, goDeviceDetail, goConfig, goSettings }
}
