<script lang="ts" setup>
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { v2Login } from '@/api/v2'
import { t } from '@/i18n'

defineOptions({ name: 'V2Login' })
const message = useMessage()
const loading = ref(false)

onLoad(() => {
  if (uni.getStorageSync('token'))
    uni.switchTab({ url: '/pages/v2/device-list/index' })
})

async function handleLogin() {
  loading.value = true
  try {
    // #ifdef MP-WEIXIN
    const res = await uni.login({ provider: 'weixin' })
    if (!res.code) {
      message.alert(`${t('v2.login.wxLogin')}失败`)
      return
    }
    const data = await v2Login(res.code)
    uni.setStorageSync('token', JSON.stringify({ token: data.token, expire: data.expiresIn }))
    uni.switchTab({ url: '/pages/v2/device-list/index' })
    // #endif
    // #ifndef MP-WEIXIN
    message.alert('请在微信小程序中打开')
    // #endif
  }
  catch (e: any) {
    message.alert(e?.message || '登录失败')
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <wd-config-provider theme-color="#3b82f6" />
  <wd-navbar
    :title="t('v2.login.title')" placeholder safe-area-inset-top fixed
    custom-class="!bg-[#07070f]"
    title-class="!text-[#f0f4f8]"
  />
  <view class="min-h-screen flex flex-col items-center justify-center px-[40rpx]" style="background: #07070f;">
    <wd-status-tip image="content" tip="" />
    <view class="mb-[16rpx] text-[48rpx] text-[#f0f4f8] font-bold">
      {{ t('v2.login.title') }}
    </view>
    <wd-text :text="t('v2.login.subtitle')" size="28rpx" color="#5a6372" />
    <wd-button type="primary" round block size="large" :loading="loading" custom-class="!mt-[80rpx] !h-[96rpx] !text-[32rpx]" @click="handleLogin">
      {{ loading ? t('v2.login.loggingIn') : t('v2.login.wxLogin') }}
    </wd-button>
    <wd-text :text="t('v2.login.privacy')" size="24rpx" color="#5a6372" custom-class="!mt-[40rpx]" />
  </view>
</template>
