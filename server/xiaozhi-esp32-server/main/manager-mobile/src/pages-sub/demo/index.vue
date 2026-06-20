<route lang="jsonc" type="page">
{
  "layout": "default",
  "style": {
    "navigationStyle": "custom"
  }
}
</route>

<script lang="ts" setup>
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { ref } from 'vue'

const webUrl = ref('')
const pageTitle = ref('数字人')
const loading = ref(true)

onLoad((options: any) => {
  const url = options?.url || ''
  if (url) {
    webUrl.value = decodeURIComponent(url)
  }
  if (options?.title) {
    pageTitle.value = decodeURIComponent(options.title)
  }
})

onUnload(() => {
  loading.value = false
})

function onMessage(e: any) {
  console.log('web-view message:', e.detail?.data)
}

function onLoadComplete() {
  loading.value = false
}

function onError(e: any) {
  loading.value = false
  console.error('web-view error:', e)
}

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="webview-page">
    <!-- 自定义导航栏 -->
    <view class="webview-nav" :style="{ paddingTop: (uni.getSystemInfoSync().statusBarHeight || 0) + 'px' }">
      <view class="nav-content">
        <view class="nav-back" @click="goBack">
          <text class="back-icon">‹</text>
        </view>
        <text class="nav-title">{{ pageTitle }}</text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-overlay">
      <view class="loading-spinner" />
      <text class="loading-text">加载中...</text>
    </view>

    <!-- WebView -->
    <web-view
      v-if="webUrl"
      :src="webUrl"
      @message="onMessage"
      @load="onLoadComplete"
      @error="onError"
    />

    <!-- 无 URL 提示 -->
    <view v-if="!webUrl && !loading" class="empty-tip">
      <text class="empty-icon">🔗</text>
      <text class="empty-text">页面地址无效</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.webview-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #07070f;
}

/* 导航栏 */
.webview-nav {
  background: rgba(7, 7, 15, 0.95);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);
  flex-shrink: 0;
  z-index: 100;

  .nav-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 88rpx;
    padding: 0 24rpx;
  }

  .nav-back {
    width: 60rpx;
    display: flex;
    align-items: center;

    .back-icon {
      font-size: 48rpx;
      color: #f0f4f8;
      line-height: 1;
    }
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: #f0f4f8;
  }

  .nav-placeholder {
    width: 60rpx;
  }
}

/* 加载中 */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20rpx;
  z-index: 50;
  background: #07070f;
}

.loading-spinner {
  width: 60rpx;
  height: 60rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.1);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  font-size: 28rpx;
  color: #8b95a8;
}

/* 空提示 */
.empty-tip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;

  .empty-icon {
    font-size: 80rpx;
  }

  .empty-text {
    font-size: 28rpx;
    color: #5a6372;
  }
}
</style>
