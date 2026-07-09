<script setup lang="ts">
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app'
import { onMounted, ref, watch } from 'vue'
import { usePageAuth } from '@/hooks/usePageAuth'
import { t } from '@/i18n'
import { tabBarI18nKeys } from '@/layouts/fg-tabbar/tabbarList'
import { useUserStore } from '@/store'
import { useLangStore } from '@/store/lang'
import { applyM6PendingTabBarBadge, getBearerToken } from '@/utils'
import 'abortcontroller-polyfill/dist/abortcontroller-polyfill-only'

const isOnline = ref(true)

usePageAuth()

const langStore = useLangStore()

onLaunch(() => {
  if (getBearerToken()) {
    const userStore = useUserStore()
    userStore.getUserInfo().catch((error) => {
      console.warn('App launch: sync user profile failed', error)
    })
  }
  // 设置全局深色背景
  uni.setBackgroundColor({
    backgroundColor: '#0b0e13',
    backgroundColorTop: '#0b0e13',
    backgroundColorBottom: '#0b0e13',
  })
  // 监听网络状态
  uni.onNetworkStatusChange((res) => {
    isOnline.value = res.isConnected
    if (!res.isConnected) {
      uni.showToast({ title: t('common.networkOffline'), icon: 'none', duration: 3000 })
    }
  })
  uni.getNetworkType({
    success: (res) => {
      isOnline.value = res.networkType !== 'none'
    },
  })
})
onShow(() => {
  // 使用setTimeout延迟执行，确保tabBar已经初始化
  setTimeout(() => {
    updateTabBarText()
    applyM6PendingTabBarBadge()
  }, 100)
})

// 动态更新tabBar文本（跟随当前语言）
function updateTabBarText() {
  try {
    tabBarI18nKeys.forEach((key, index) => {
      uni.setTabBarItem({ index, text: t(key) })
    })
  }
  catch {
    // ignore tabBar update errors on platforms without tabBar
  }
}
// 监听语言切换事件
onMounted(() => {
  // 监听语言变化，当语言改变时自动更新tabBar文本
  watch(() => langStore.currentLang, () => {
    // 语言切换后立即更新tabBar文本
    updateTabBarText()
  })
})

onHide(() => {})
</script>

<style lang="scss">
swiper,
scroll-view {
  flex: 1;
  height: 100%;
  overflow: hidden;
}

image {
  width: 100%;
  height: 100%;
  vertical-align: middle;
}
</style>
