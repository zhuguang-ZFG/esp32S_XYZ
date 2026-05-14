<script lang="ts" setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { t } from '@/i18n'
import { toast } from '@/utils/toast'
import { v2Login } from '@/api/v2'

defineOptions({ name: 'V2Login' })

const loading = ref(false)

onLoad(() => {
  // 如果已有 token 则直接跳转设备列表
  const token = uni.getStorageSync('token')
  if (token) {
    uni.redirectTo({ url: '/pages/v2/device-list/index' })
  }
})

async function handleWxLogin() {
  loading.value = true
  try {
    // #ifdef MP-WEIXIN
    const loginRes = await uni.login({ provider: 'weixin' })
    const code = loginRes.code
    if (!code) {
      toast.error(t('v2.deviceDetail.toastLoginFailed'))
      return
    }
    const res = await v2Login(code)
    uni.setStorageSync('token', res.token)
    uni.redirectTo({ url: '/pages/v2/device-list/index' })
    // #endif
    // #ifndef MP-WEIXIN
    toast.error('请在微信小程序中打开')
    // #endif
  }
  catch (e: any) {
    console.error('v2 login failed', e)
    toast.error(e?.message || t('v2.deviceDetail.toastLoginFailed'))
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="page-container min-h-screen bg-[#fbfbfb] flex flex-col items-center justify-center px-[40rpx]">
    <!-- #ifdef MP-WEIXIN -->
    <view class="safe-area-top" />
    <!-- #endif -->

    <view class="mb-[80rpx] text-center">
      <text class="text-[48rpx] font-bold text-[#333]">
        {{ t('v2.login.title') }}
      </text>
      <view class="mt-[16rpx]">
        <text class="text-[28rpx] text-[#999]">AI 语音写字绘图机</text>
      </view>
    </view>

    <wd-button
      type="primary"
      block
      round
      size="large"
      :loading="loading"
      custom-class="!h-[96rpx] !text-[32rpx]"
      @click="handleWxLogin"
    >
      {{ loading ? t('v2.login.loggingIn') : t('v2.login.wxLogin') }}
    </wd-button>

    <view class="mt-[40rpx]">
      <text class="text-[24rpx] text-[#999]">
        {{ t('v2.login.privacy') }}
      </text>
    </view>

    <!-- #ifdef MP-WEIXIN -->
    <view class="safe-area-bottom" />
    <!-- #endif -->
  </view>
</template>

<style scoped>
.safe-area-top {
  height: constant(safe-area-inset-top);
  height: env(safe-area-inset-top);
}
.safe-area-bottom {
  height: constant(safe-area-inset-bottom);
  height: env(safe-area-inset-bottom);
}
</style>
