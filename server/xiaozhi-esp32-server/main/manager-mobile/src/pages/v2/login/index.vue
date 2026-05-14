<script lang="ts" setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useMessage } from 'wot-design-uni'
import { t } from '@/i18n'
import { v2Login } from '@/api/v2'

defineOptions({ name: 'V2Login' })
const message = useMessage()
const loading = ref(false)

onLoad(() => {
  if (uni.getStorageSync('token'))
    uni.redirectTo({ url: '/pages/v2/device-list/index' })
})

async function handleLogin() {
  loading.value = true
  try {
    // #ifdef MP-WEIXIN
    const res = await uni.login({ provider: 'weixin' })
    if (!res.code) { message.alert(t('v2.login.wxLogin') + '失败'); return }
    const data = await v2Login(res.code)
    uni.setStorageSync('token', data.token)
    uni.redirectTo({ url: '/pages/v2/device-list/index' })
    // #endif
    // #ifndef MP-WEIXIN
    message.alert('请在微信小程序中打开')
    // #endif
  } catch (e: any) {
    message.alert(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <wd-config-provider theme-color="#336cff" />
  <wd-navbar :title="t('v2.login.title')" fixed placeholder safe-area-inset-top />
  <view class="flex flex-col items-center justify-center min-h-screen px-[40rpx] bg-[#fbfbfb]">
    <wd-status-tip image="content" tip="" />
    <view class="text-[48rpx] font-bold text-[#333] mb-[16rpx]">
      {{ t('v2.login.title') }}
    </view>
    <wd-text :text="t('v2.login.subtitle')" size="28rpx" color="#999" />
    <wd-button type="primary" block round size="large" :loading="loading" custom-class="!mt-[80rpx] !h-[96rpx] !text-[32rpx]" @click="handleLogin">
      {{ loading ? t('v2.login.loggingIn') : t('v2.login.wxLogin') }}
    </wd-button>
    <wd-text :text="t('v2.login.privacy')" size="24rpx" color="#999" custom-class="!mt-[40rpx]" />
  </view>
</template>
