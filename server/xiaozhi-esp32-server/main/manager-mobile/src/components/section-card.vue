<script setup lang="ts">
/**
 * 通用设置分区卡片壳：标题 + 圆角阴影内容区。
 * 抽自 settings/index.vue 原 7 处重复的 `-section` 样式。
 * variant=danger 用于账号注销等警示色分区。
 */
interface Props {
  title: string
  variant?: 'default' | 'danger'
}

withDefaults(defineProps<Props>(), {
  variant: 'default',
})
</script>

<template>
  <view class="mb-[32rpx]">
    <view class="mb-[24rpx] flex items-center">
      <text class="text-[32rpx] text-[#f0f4f8] font-bold">
        {{ title }}
      </text>
    </view>
    <view
      :class="variant === 'danger'
        ? 'border border-[rgba(255,107,107,0.15)] rounded-[24rpx] p-[32rpx]'
        : 'border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]'"
      :style="variant === 'danger'
        ? 'background: rgba(255,107,107,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);'
        : 'background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);'"
    >
      <slot />
    </view>
  </view>
</template>