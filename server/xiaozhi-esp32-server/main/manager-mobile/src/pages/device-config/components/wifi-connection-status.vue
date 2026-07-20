<script setup lang="ts">
import { t } from '@/i18n'

interface Props {
  autoConnect?: boolean
  // MP-6:父组件以 :is-connected-to-esp32 传参(camelize → isConnectedToEsp32),
  // 此前声明为 isConnectedToESP32 导致 prop 永远 undefined、状态恒显"未连接"(TS2345)
  isConnectedToEsp32: boolean
  checkingConnection: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  recheck: []
}>()
</script>

<template>
  <view v-if="autoConnect" class="connection-status">
    <view v-if="!isConnectedToEsp32" class="status-warning">
      <view class="status-content">
        <text class="warning-text">
          {{ t('deviceConfig.connectDlcHotspot') }} (DLC-XXXXXX)
        </text>
        <wd-button
          size="small"
          type="primary"
          :loading="checkingConnection"
          @click="emit('recheck')"
        >
          {{ checkingConnection ? t('deviceConfig.checking') : t('deviceConfig.reCheck') }}
        </wd-button>
      </view>
    </view>
    <view v-else class="status-success">
      <view class="status-content">
        <text class="success-text">
          {{ t('deviceConfig.connectedDlcHotspot') }}
        </text>
        <wd-button
          size="small"
          :loading="checkingConnection"
          @click="emit('recheck')"
        >
          {{ checkingConnection ? t('deviceConfig.checking') : t('deviceConfig.refreshStatus') }}
        </wd-button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.connection-status {
  margin-bottom: 24rpx;
}

.status-warning {
  padding: 24rpx;
  background-color: #fff3cd;
  border: 1rpx solid #ffeaa7;
  border-radius: 16rpx;
}

.status-success {
  padding: 24rpx;
  background-color: #d4edda;
  border: 1rpx solid #c3e6cb;
  border-radius: 16rpx;
}

.status-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.warning-text {
  color: #856404;
  font-size: 28rpx;
}

.success-text {
  color: #155724;
  font-size: 28rpx;
}
</style>