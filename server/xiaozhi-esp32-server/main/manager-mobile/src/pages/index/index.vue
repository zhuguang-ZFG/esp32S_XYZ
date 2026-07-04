<route lang="jsonc" type="page">
{
  "layout": "default",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "LiMa"
  }
}
</route>

<script lang="ts" setup>
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { t } from '@/i18n'
import { useHomeData } from './composables/useHomeData'
import { useHomeNavigation } from './composables/useHomeNavigation'
import { useTaskFormatters } from './composables/useTaskFormatters'

defineOptions({ name: 'WorkshopHome' })

const safeAreaTop = ref(0)
const systemInfo = uni.getSystemInfoSync()
safeAreaTop.value = systemInfo.statusBarHeight || 0

// D2: 数据加载 + 派生状态（loadData / primaryDevice / onlineCount）
const { devices, recentTasks, loading, primaryDevice, onlineCount, loadData } = useHomeData()

// 瘦身后：对话走小智云，仅保留 draw/image-draw/devices/config/settings
const { goDraw, goImageDraw, goDevices, goDeviceDetail, goConfig, goSettings } = useHomeNavigation()

// D2: 任务状态格式化（label / color / progress）
const { taskStatusLabel, taskStatusColor, taskProgress } = useTaskFormatters()

onShow(() => {
  loadData()
})
</script>

<template>
  <view class="page-enter home" :style="{ paddingTop: `${safeAreaTop}px` }">
    <!-- 顶部标题（纯文字，无装饰） -->
    <view class="home-header">
      <text class="home-title">
        {{ t('workshop.title') }}
      </text>
      <text class="home-subtitle">
        {{ t('workshop.subtitle') }}
      </text>
    </view>

    <!-- ══ 设备状态卡（数据为核心）══ -->
    <view class="section">
      <view v-if="primaryDevice" class="device-hero workshop-panel" @click="goDeviceDetail(primaryDevice.deviceId)">
        <!-- 状态行：点灯 + 名称 -->
        <view class="hero-top">
          <view class="hero-status">
            <view class="pulse-dot" :class="primaryDevice.status === 'online' ? 'online' : 'offline'" />
            <text class="hero-status-text" :class="{ online: primaryDevice.status === 'online' }">
              {{ primaryDevice.status === 'online' ? t('workshop.online') : t('workshop.offline') }}
            </text>
          </view>
          <text class="hero-model">
            {{ primaryDevice.model || t('workshop.device') }}
          </text>
        </view>

        <!-- 大数字：工作幅面（唯一的 hero number） -->
        <view class="hero-metric">
          <text class="metric-value">
            {{ primaryDevice.workspaceMm?.x || 0 }}<text class="metric-x">
              ×
            </text>{{ primaryDevice.workspaceMm?.y || 0 }}
          </text>
          <text class="metric-unit">
            mm 工作幅面
          </text>
        </view>

        <!-- 次要数据行（中性色，不抢眼） -->
        <view class="hero-sub">
          <view class="sub-item">
            <text class="sub-label">
              固件
            </text>
            <text class="sub-value">
              v{{ primaryDevice.fwRev || '—' }}
            </text>
          </view>
          <view class="sub-divider" />
          <view class="sub-item">
            <text class="sub-label">
              设备
            </text>
            <text class="sub-value">
              {{ onlineCount }}/{{ devices.length }} 台
            </text>
          </view>
          <view class="sub-divider" />
          <view class="sub-item">
            <text class="sub-label">
              编号
            </text>
            <text class="sub-value mono">
              {{ primaryDevice.deviceId.slice(0, 8) }}
            </text>
          </view>
        </view>
      </view>

      <!-- 无设备状态 -->
      <view v-else-if="!loading" class="workshop-panel empty-hero" @click="goDevices">
        <text class="empty-icon">
          ＋
        </text>
        <text class="empty-text">
          {{ t('workshop.noDevices') }}
        </text>
        <text class="empty-hint">
          {{ t('workshop.addDeviceHint') }}
        </text>
      </view>
    </view>

    <!-- ══ AI 创作入口 ══ -->
    <view class="section">
      <text class="section-title">
        {{ t('workshop.aiCreate') }}
      </text>
      <view class="create-grid">
        <view class="create-card workshop-panel-interactive" @click="goDraw">
          <view class="create-icon draw">
            <text>✦</text>
          </view>
          <text class="create-name">
            {{ t('workshop.aiDraw') }}
          </text>
          <text class="create-desc">
            {{ t('workshop.aiDrawDesc') }}
          </text>
        </view>
        <view class="create-card workshop-panel-interactive" @click="goImageDraw">
          <view class="create-icon image">
            <text>🖼️</text>
          </view>
          <text class="create-name">
            {{ t('workshop.imageDraw') }}
          </text>
          <text class="create-desc">
            {{ t('workshop.imageDrawDesc') }}
          </text>
        </view>
      </view>
    </view>

    <!-- ══ 最近任务 ══ -->
    <view v-if="recentTasks.length" class="section">
      <view class="section-header">
        <text class="section-title">
          {{ t('workshop.recentTasks') }}
        </text>
        <text class="section-more" @click="goDeviceDetail(primaryDevice?.deviceId || '')">
          {{ t('workshop.viewAll') }}
        </text>
      </view>
      <view class="task-list">
        <view v-for="task in recentTasks" :key="task.taskId" class="workshop-panel task-item">
          <view class="task-info">
            <text class="task-cap">
              {{ task.params?.imageUrl && !task.params?.prompt ? '🖼️' : '✦' }} {{ task.params?.imageUrl && !task.params?.prompt ? t('workshop.imageDraw') : t('workshop.aiDraw') }}
            </text>
            <text class="task-status-text" :style="{ color: taskStatusColor(task.status) }">
              {{ taskStatusLabel(task.status) }}
            </text>
          </view>
          <view class="task-track">
            <view class="task-track-fill" :style="{ width: `${taskProgress(task.status)}%`, background: taskStatusColor(task.status) }" />
          </view>
        </view>
      </view>
    </view>

    <!-- ══ 快捷操作 ══ -->
    <view class="section">
      <view class="quick-row">
        <view class="workshop-panel-interactive quick-btn" @click="goDevices">
          <text class="quick-icon">
            ▣
          </text>
          <text class="quick-text">
            {{ t('workshop.myDevices') }}
          </text>
        </view>
        <view class="quick-btn workshop-panel-interactive" @click="goConfig">
          <text class="quick-icon">
            ⌗
          </text>
          <text class="quick-text">
            {{ t('workshop.config') }}
          </text>
        </view>
        <view class="quick-btn workshop-panel-interactive" @click="goSettings">
          <text class="quick-icon">
            ⚙
          </text>
          <text class="quick-text">
            {{ t('workshop.systemSettings') }}
          </text>
        </view>
      </view>
    </view>

    <view style="height: env(safe-area-inset-bottom);" />
  </view>
</template>

<style src="./index.scss" lang="scss" scoped></style>
