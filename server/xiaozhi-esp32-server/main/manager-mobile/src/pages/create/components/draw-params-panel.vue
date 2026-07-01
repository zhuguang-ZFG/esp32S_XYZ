<script lang="ts" setup>
import { ref, watch, onMounted } from 'vue'
import { v2GetHandwritingOptions } from '@/api/v2'
import { t } from '@/i18n'

defineOptions({ name: 'DrawParamsPanel' })

const props = defineProps<{
  mode: string // 'draw' | 'write' | 'handwrite' | 'image'
}>()

const emit = defineEmits<{
  (e: 'update:params', params: Record<string, unknown>): void
}>()

// 速度预设（mm/min）
const FEED_PRESETS = [
  { label: '慢', value: 300 },
  { label: '中', value: 900 },
  { label: '快', value: 1500 },
]

// 仿手写字体列表
const fonts = ref<Record<string, string>>({})
const fontsLoaded = ref(false)
const selectedFont = ref('0')

// 参数
const feed = ref(900)
const mistakeRate = ref(3)
const messyRatio = ref(0)

onMounted(async () => {
  try {
    const res = await v2GetHandwritingOptions()
    fonts.value = res.fonts || {}
    fontsLoaded.value = true
    selectedFont.value = res.defaults?.font_type || '0'
    emitParams()
  } catch (e) {
    console.error('Failed to load handwriting options', e)
  }
})

function emitParams() {
  const params: Record<string, unknown> = { feed: feed.value }
  if (props.mode === 'handwrite') {
    params.font_type = selectedFont.value
    params.mistake_rate = mistakeRate.value
    params.messy_ratio = messyRatio.value
  }
  emit('update:params', params)
}

watch([feed, selectedFont, mistakeRate, messyRatio], () => emitParams())

function onFeedPreset(value: number) {
  feed.value = value
}
</script>

<template>
  <view v-if="mode !== 'image'" class="params-panel">
    <!-- 仿手写：字体选择 -->
    <view v-if="mode === 'handwrite'" class="param-row">
      <text class="param-label">{{ t('create.params.font') }}</text>
      <view class="font-picker">
        <picker
          mode="selector"
          :range="Object.values(fonts)"
          :value="Object.keys(fonts).indexOf(selectedFont)"
          @change="(e: any) => { const idx = Number(e.detail.value); selectedFont = Object.keys(fonts)[idx] || '0' }"
        >
          <view class="picker-display">
            <text class="picker-text">{{ fonts[selectedFont] || t('create.params.selectFont') }}</text>
            <text class="picker-arrow">▾</text>
          </view>
        </picker>
      </view>
    </view>

    <!-- 速度滑块（写字/手写/绘图通用） -->
    <view class="param-row">
      <view class="param-header">
        <text class="param-label">{{ t('create.params.speed') }}</text>
        <text class="param-value">{{ feed }} mm/min</text>
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

    <!-- 仿手写：失误率 -->
    <view v-if="mode === 'handwrite'" class="param-row">
      <view class="param-header">
        <text class="param-label">{{ t('create.params.mistakeRate') }}</text>
        <text class="param-value">{{ mistakeRate }}%</text>
      </view>
      <slider
        :value="mistakeRate"
        :min="0"
        :max="100"
        :step="1"
        active-color="var(--amber)"
        block-color="var(--amber)"
        @change="(e: any) => mistakeRate = e.detail.value"
      />
    </view>

    <!-- 仿手写：潦草度 -->
    <view v-if="mode === 'handwrite'" class="param-row">
      <view class="param-header">
        <text class="param-label">{{ t('create.params.messyRatio') }}</text>
        <text class="param-value">{{ messyRatio }}%</text>
      </view>
      <slider
        :value="messyRatio"
        :min="0"
        :max="100"
        :step="1"
        active-color="var(--violet)"
        block-color="var(--violet)"
        @change="(e: any) => messyRatio = e.detail.value"
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

.font-picker {
  picker {
    width: 100%;
  }
}

.picker-display {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 20rpx;
  background: var(--bg);
  border: 1rpx solid var(--border);
  border-radius: 12rpx;
}

.picker-text {
  font-size: 26rpx;
  color: var(--text);
}

.picker-arrow {
  font-size: 24rpx;
  color: var(--dim);
}

slider {
  width: 100%;
}
</style>
