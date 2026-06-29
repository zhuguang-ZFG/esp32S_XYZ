<script lang="ts" setup>
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { v2Login } from '@/api/v2'
import { t } from '@/i18n'
import { getEnvBaseUrl } from '@/utils'

defineOptions({ name: 'V2Login' })
const message = useMessage()
const loading = ref(false)
const errorInfo = ref('')
const isDev = import.meta.env.DEV

onLoad(() => {
  if (uni.getStorageSync('token'))
    uni.switchTab({ url: '/pages/v2/device-list/index' })
})

function stringifyError(e: any): string {
  if (e == null)
    return '错误对象为空'
  if (typeof e === 'string')
    return e
  if (e instanceof Error)
    return `${e.name}: ${e.message}\n${e.stack || ''}`
  try {
    return JSON.stringify(e, Object.getOwnPropertyNames(e), 2)
  }
  catch {
    return String(e)
  }
}

async function handleLogin() {
  loading.value = true
  errorInfo.value = ''
  try {
    // #ifdef MP-WEIXIN
    const loginRes = await uni.login({ provider: 'weixin' })
    if (!loginRes.code) {
      message.alert(t('v2.login.wxLoginFailed'))
      errorInfo.value = `uni.login 未返回 code\n${JSON.stringify(loginRes, null, 2)}`
      return
    }

    const data = await v2Login(loginRes.code)
    const expireAt = Math.floor(Date.now() / 1000) + (data.expiresIn || 86400)
    uni.setStorageSync('token', JSON.stringify({ token: data.token, expireAt }))
    uni.switchTab({ url: '/pages/v2/device-list/index' })
    // #endif
    // #ifndef MP-WEIXIN
    message.alert(t('v2.login.openInWechat'))
    // #endif
  }
  catch (e: any) {
    const detail = stringifyError(e)
    console.error('[wx login error]', e)
    if (isDev) {
      errorInfo.value = `[${new Date().toLocaleTimeString()}]\nbaseUrl: ${getEnvBaseUrl()}\n${detail}`
    }
    message.alert(detail || t('v2.login.loginFailed'))
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <wd-config-provider theme-color="#3b82f6" />
  <wd-navbar
    :title="t('v2.login.title')" safe-area-inset-top placeholder fixed
    custom-class="!bg-[#07070f]/90 !backdrop-blur-md"
    title-class="!text-[#f0f4f8]"
  />

  <view class="page-enter relative min-h-screen overflow-hidden bg-[#07070f]">
    <!-- 背景光晕 -->
    <view class="pointer-events-none absolute inset-0">
      <view class="absolute left-1/2 top-0 h-[70vh] w-[140vw] bg-[radial-gradient(circle_at_50%_0%,rgba(59,130,246,0.22),transparent_55%)] -translate-x-1/2" />
      <view class="absolute inset-0 bg-[radial-gradient(circle_at_50%_120%,rgba(29,78,216,0.10),transparent_50%)]" />
    </view>

    <!-- 内容 -->
    <view class="animate-enter relative z-10 min-h-screen flex flex-col items-center justify-center px-[48rpx] pb-[120rpx]">
      <!-- 品牌标识 -->
      <view class="mb-[64rpx] h-[180rpx] w-[180rpx] flex items-center justify-center border border-white/10 rounded-[48rpx] from-[#3b82f6]/30 via-[#2563eb]/15 to-transparent bg-gradient-to-br shadow-2xl shadow-blue-500/20">
        <text class="text-[64rpx] text-white font-black tracking-tighter">
          DLC
        </text>
      </view>

      <!-- 标题区 -->
      <view class="mb-[20rpx] text-[56rpx] text-white font-bold leading-none tracking-tight">
        {{ t('v2.login.title') }}
      </view>
      <view class="mb-[120rpx] text-center text-[28rpx] text-[#8b95a8] leading-relaxed">
        {{ t('v2.login.subtitle') }}
      </view>

      <!-- 登录按钮 -->
      <view class="max-w-[560rpx] w-full transition-transform duration-150 active:scale-[0.98]">
        <wd-button
          type="primary" round block size="large" :loading="loading"
          custom-class="!h-[100rpx] !rounded-full !bg-gradient-to-r !from-[#3b82f6] !to-[#2563eb] !shadow-lg !shadow-blue-500/30 !text-white !text-[32rpx] !font-medium"
          @click="handleLogin"
        >
          {{ loading ? t('v2.login.loggingIn') : t('v2.login.wxLogin') }}
        </wd-button>
      </view>

      <!-- 隐私提示 -->
      <view class="mt-[48rpx] px-[40rpx] text-center text-[22rpx] text-[#5a6372] leading-relaxed">
        {{ t('v2.login.privacy') }}
      </view>

      <!-- 调试信息（仅在开发环境显示） -->
      <view v-if="errorInfo && isDev" class="mt-[48rpx] max-w-[560rpx] w-full border border-white/5 rounded-2xl bg-[#0a0a14] p-[24rpx]">
        <view class="mb-[12rpx] text-[24rpx] text-[#ff6b6b] font-bold">
          调试信息
        </view>
        <text class="block text-[22rpx] text-[#8b95a8]" style="white-space: pre-wrap; word-break: break-all;">
          {{ errorInfo }}
        </text>
      </view>
    </view>
  </view>
</template>

<style scoped>
@keyframes fade-in-up {
  from {
    opacity: 0;
    transform: translateY(40rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.animate-enter {
  animation: fade-in-up 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@media (prefers-reduced-motion: reduce) {
  .animate-enter {
    animation: none;
  }
}
</style>
