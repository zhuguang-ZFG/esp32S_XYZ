<script setup lang="ts">
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app'
import { onMounted, ref, watch } from 'vue'
import { usePageAuth } from '@/hooks/usePageAuth'
import { t } from '@/i18n'
import { tabBarI18nKeys } from '@/layouts/fg-tabbar/tabbarList'
import { useConfigStore } from '@/store'
import { useLangStore } from '@/store/lang'
import { applyM6PendingTabBarBadge } from '@/utils'
import 'abortcontroller-polyfill/dist/abortcontroller-polyfill-only'

const isOnline = ref(true)

usePageAuth()

const configStore = useConfigStore()
const langStore = useLangStore()

onLaunch(() => {
  // 设置全局深色背景
  uni.setBackgroundColor({
    backgroundColor: '#07070f',
    backgroundColorTop: '#07070f',
    backgroundColorBottom: '#07070f',
  })
  // 获取公共配置
  configStore.fetchPublicConfig().catch(() => {
    // ignore; config falls back to env defaults
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
