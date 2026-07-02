<script setup lang="ts">
/**
 * 统计药丸：大数字 + 标签。深色主题。
 * 抽自 mine/mine.vue 与 index/index.vue 重复的统计卡。
 * tone=online/tasks 用于次级色彩区分。
 */
interface Props {
  value: number | string
  label: string
  tone?: 'default' | 'online' | 'tasks'
  skeleton?: boolean
  skeletonWidth?: string
}

withDefaults(defineProps<Props>(), {
  tone: 'default',
  skeleton: false,
  skeletonWidth: '60rpx',
})
</script>

<template>
  <view class="stat-card">
    <view v-if="skeleton" class="skeleton" :style="{ width: skeletonWidth, height: '48rpx' }" />
    <text
      v-else
      class="stat-num"
      :class="{
        online: tone === 'online',
        tasks: tone === 'tasks',
      }"
    >
      {{ value }}
    </text>
    <text class="stat-label">
      {{ label }}
    </text>
  </view>
</template>

<style lang="scss" scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.04);
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.03);
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
}

.stat-num {
  font-size: 52rpx;
  font-weight: 700;
  color: #f0f4f8;
  line-height: 1.2;

  &.online {
    color: #2dd4a7;
  }
  &.tasks {
    color: #ffc83d;
  }
}

.stat-label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #5a6372;
}

.skeleton {
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.04));
  background-size: 200% 100%;
  border-radius: 12rpx;
  animation: skeleton-shimmer 1.5s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>