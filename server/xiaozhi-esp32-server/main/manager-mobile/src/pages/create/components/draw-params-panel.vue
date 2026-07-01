<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue'
import { t } from '@/i18n'

defineOptions({ name: 'DrawParamsPanel' })

const emit = defineEmits<{
  (e: 'update:params', params: Record<string, unknown>): void
}>()

// 速度预设（mm/min）
const FEED_PRESETS = [
  { label: '慢', value: 300 },
  { label: '中', value: 900 },
  { label: '快', value: 1500 },
]

const feed = ref(900)

onMounted(() => emitParams())
watch(feed, () => emitParams())

function emitParams() {
  emit('update:params', { feed: feed.value })
}

function onFeedPreset(value: number) {
  feed.value = value
}
</script>

<template>
  <view class="params-panel">
    <view class="param-row">
      <view class="param-header">
        <text class="param-label">
          {{ t('create.params.speed') }}
        </text>
        <text class="param-value">
          {{ feed }} mm/min
        </text>
      </view>
      <view class="feed-presets">
        <view
          v-for="preset in FEED_PRESETS"
          :key="preset.value"
          class="feed-chip"
          :class="{ active: feed === preset.value }"
          @click="onFeedPreset(preset.value)"
        >
          {{ preset.label }}
        </view>
      </view>
      <slider
        :value="feed"
        :min="100"
        :max="2000"
        :step="100"
        active-color="var(--accent)"
        block-color="var(--accent)"
        @change="(e: any) => feed = e.detail.value"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.params-panel {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 24rpx 0;
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.param-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.param-label {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text);
}

.param-value {
  font-size: 24rpx;
  color: var(--accent);
  font-weight: 500;
}

.feed-presets {
  display: flex;
  gap: 12rpx;
}

.feed-chip {
  flex: 1;
  text-align: center;
  padding: 12rpx 0;
  border-radius: 12rpx;
  background: var(--bg);
  border: 1rpx solid var(--border);
  font-size: 24rpx;
  color: var(--muted);
  transition: all 0.15s;

  &.active {
    background: var(--accent-g);
    border-color: var(--accent);
    color: var(--accent);
    font-weight: 600;
  }
}

slider {
  width: 100%;
}
</style>
