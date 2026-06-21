<script setup lang="ts">
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app'
import { watch, onMounted, ref } from 'vue'
import { usePageAuth } from '@/hooks/usePageAuth'
import { useConfigStore } from '@/store'
import { t } from '@/i18n'
import { useLangStore } from '@/store/lang'
import { applyM6PendingTabBarBadge } from '@/utils'

const isOnline = ref(true)

usePageAuth()

const configStore = useConfigStore()
const langStore = useLangStore()

onLaunch(() => {
  console.log('App Launch')
  // 设置全局深色背景
  uni.setBackgroundColor({
    backgroundColor: '#07070f',
    backgroundColorTop: '#07070f',
    backgroundColorBottom: '#07070f',
  })
  // 获取公共配置
  configStore.fetchPublicConfig().catch((error) => {
    console.error('获取公共配置失败:', error)
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
  console.log('App Show')
  // 使用setTimeout延迟执行，确保tabBar已经初始化
  setTimeout(() => {
    updateTabBarText()
    applyM6PendingTabBarBadge()
  }, 100)
})

// 动态更新tabBar文本
function updateTabBarText() {
  try {
    // index 0: 星云
    uni.setTabBarItem({
      index: 0,
      text: '星云',
      success: () => {},
      fail: (err) => { console.log('设置星云tabBar文本失败:', err) }
    })

    // index 1: 对话
    uni.setTabBarItem({
      index: 1,
      text: '对话',
      success: () => {},
      fail: (err) => { console.log('设置对话tabBar文本失败:', err) }
    })

    // index 2: 创作
    uni.setTabBarItem({
      index: 2,
      text: '创作',
      success: () => {},
      fail: (err) => { console.log('设置创作tabBar文本失败:', err) }
    })

    // index 3: 我的
    uni.setTabBarItem({
      index: 3,
      text: '我的',
      success: () => {},
      fail: (err) => { console.log('设置我的tabBar文本失败:', err) }
    })
  } catch (error) {
    console.log('更新tabBar文本时出错:', error)
  }
}
// 监听语言切换事件
onMounted(() => {
  // 监听语言变化，当语言改变时自动更新tabBar文本
  watch(() => langStore.currentLang, () => {
    console.log('语言已切换，更新tabBar文本')
    // 语言切换后立即更新tabBar文本
    updateTabBarText()
  })
})

onHide(() => {
  console.log('App Hide')
})
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
