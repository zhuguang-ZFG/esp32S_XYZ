<!-- 智能体管理 tab 页面 -->
<route lang="jsonc" type="page">
{
  "layout": "tabbar",
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "智能体"
  }
}
</route>

<script lang="ts" setup>
import type { Agent } from '@/api/agent/types'
import { onMounted, ref } from 'vue'
// 在组件挂载后设置导航栏标题
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import useZPaging from 'z-paging/components/z-paging/js/hooks/useZPaging.js'
import { createAgent, deleteAgent, getAgentList } from '@/api/agent/agent'
import { t } from '@/i18n'
import { toast } from '@/utils/toast'

defineOptions({
  name: 'Home',
})

// 获取屏幕边界到安全区域距离
let safeAreaInsets: any
let systemInfo: any

// #ifdef MP-WEIXIN
// 微信小程序使用新的API
systemInfo = uni.getWindowInfo()
safeAreaInsets = systemInfo.safeArea
  ? {
      top: systemInfo.safeArea.top,
      right: systemInfo.windowWidth - systemInfo.safeArea.right,
      bottom: systemInfo.windowHeight - systemInfo.safeArea.bottom,
      left: systemInfo.safeArea.left,
    }
  : null
// #endif

// #ifndef MP-WEIXIN
// 其他平台继续使用uni API
systemInfo = uni.getSystemInfoSync()
safeAreaInsets = systemInfo.safeAreaInsets
// #endif

// 智能体数据
const agentList = ref<Agent[]>([])
const pagingRef = ref()
useZPaging(pagingRef)
// 消息组件
const message = useMessage()

// z-paging查询列表数据
async function queryList(pageNo: number, pageSize: number) {
  try {
    console.log('z-paging获取智能体列表')

    const response = await getAgentList()

    // 更新本地列表
    agentList.value = response

    // 直接返回全部数据，不需要分页处理
    pagingRef.value.complete(response)
  }
  catch (error) {
    console.error('获取智能体列表失败:', error)
    // 告知z-paging数据加载失败
    pagingRef.value.complete(false)
  }
}

// 创建智能体
async function handleCreateAgent(agentName: string) {
  try {
    await createAgent({ agentName: agentName.trim() })
    // 创建成功后刷新列表
    pagingRef.value.reload()
    toast.success(`${t('home.agentName')}"${agentName}"${t('message.saveSuccess')}`)
  }
  catch (error: any) {
    console.error('创建智能体失败:', error)
    const errorMessage = error?.message || t('message.saveFail')
    toast.error(errorMessage)
  }
}

// 删除智能体
async function handleDeleteAgent(agent: Agent) {
  try {
    await deleteAgent(agent.id)
    // 删除成功后刷新列表
    pagingRef.value.reload()
    toast.success(`${t('home.agentName')}${t('message.deleteSuccess')}`)
  }
  catch (error: any) {
    console.error('删除智能体失败:', error)
    const errorMessage = error?.message || t('message.deleteFail')
    toast.error(errorMessage)
  }
}

// 进入编辑页面
function goToEditAgent(agent: Agent) {
  // 传递智能体ID到编辑页面
  uni.navigateTo({
    url: `/pages/agent/index?agentId=${agent.id}`,
  })
}

// 点击卡片进入编辑
function handleCardClick(agent: Agent) {
  goToEditAgent(agent)
}

// 打开创建对话框
function openCreateDialog() {
  message
    .prompt({
      title: t('home.dialogTitle'),
      msg: '',
      inputPlaceholder: t('home.inputPlaceholder'),
      inputValue: '',
      inputPattern: /^.{1,64}$/i,
      inputError: t('home.createError'),
      confirmButtonText: t('home.createNow'),
      cancelButtonText: t('common.cancel'),
    })
    .then(async (result: any) => {
      if (result.value && String(result.value).trim()) {
        await handleCreateAgent(String(result.value).trim())
      }
    })
    .catch(() => {
      // 用户取消操作
    })
}

// 格式化时间
function formatTime(timeStr: string) {
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000)
    return t('home.justNow')
  if (diff < 3600000)
    return `${Math.floor(diff / 60000)}${t('home.minutesAgo')}`
  if (diff < 86400000)
    return `${Math.floor(diff / 3600000)}${t('home.hoursAgo')}`
  return `${Math.floor(diff / 86400000)}${t('home.daysAgo')}`
}

// 页面显示时刷新列表
onShow(() => {
  console.log('首页 onShow，刷新智能体列表')
  if (pagingRef.value) {
    pagingRef.value.refresh()
  }
})

onMounted(() => {
  uni.setNavigationBarTitle({
    title: t('home.pageTitle'),
  })
})
</script>

<template>
  <z-paging
    ref="pagingRef" v-model="agentList" :refresher-enabled="true" :auto-show-back-to-top="true"
    :loading-more-enabled="false" :show-loading-more="false" :hide-empty-view="false" :empty-view-text="t('home.emptyState')"
    empty-view-img="" :refresher-threshold="80" :back-to-top-style="{
      backgroundColor: '#0a0a14',
      borderRadius: '50%',
      border: '1rpx solid rgba(255,255,255,0.06)',
      width: '56px',
      height: '56px',
    }" @query="queryList"
  >
    <!-- 固定在顶部的星云横幅区域 -->
    <template #top>
      <view class="nebula-banner" :style="{ paddingTop: `${safeAreaInsets?.top + 80}rpx` }">
        <view class="banner-content">
          <view class="welcome-info">
            <text class="greeting">
              {{ t('home.greeting') }}
            </text>
            <text class="subtitle">
              {{ t('home.subtitle') }} <text class="highlight">
                {{ t('home.wonderfulDay') }}
              </text>
            </text>
          </view>
          <view class="star-decoration">
            <view class="star" />
            <view class="star star-2" />
            <view class="star star-3" />
          </view>
        </view>
      </view>

      <!-- 内容区域开始标识 -->
      <view class="content-section-header" />
    </template>

    <!-- 智能体卡片列表 -->
    <view class="agent-list">
      <view v-for="agent in agentList" :key="agent.id" class="agent-item">
        <wd-swipe-action>
          <view class="nebula-card agent-card" @click="handleCardClick(agent)">
            <view class="card-content">
              <view class="card-main">
                <view class="agent-title">
                  <text class="agent-name">
                    {{ agent.agentName }}
                  </text>
                </view>

                <view class="model-info">
                  <text class="model-text">
                    {{ t('home.languageModel') }}： {{ agent.llmModelName }}
                  </text>
                  <text class="model-text">
                    {{ t('home.voiceModel') }}： {{ agent.ttsModelName }} ({{ agent.ttsVoiceName }})
                  </text>
                </view>

                <view class="stats-row">
                  <view class="stat-chip">
                    <wd-icon name="phone" custom-class="chip-icon" />
                    <text class="chip-text">
                      {{ t('home.deviceManagement') }}({{ agent.deviceCount }})
                    </text>
                  </view>
                  <view v-if="agent.lastConnectedAt" class="stat-chip">
                    <wd-icon name="time" custom-class="chip-icon" />
                    <text class="chip-text">
                      {{ t('home.lastConversation') }}{{ formatTime(agent.lastConnectedAt) }}
                    </text>
                  </view>
                  <text v-if="agent.tags" class="flex-1 truncate text-right text-[22rpx] text-[#5a6372]">
                    {{ agent.tags.map(tag => tag.tagName).join(',') }}
                  </text>
                </view>
              </view>

              <wd-icon name="arrow-right" custom-class="arrow-icon" />
            </view>
          </view>

          <template #right>
            <view class="swipe-actions">
              <view class="action-btn delete-btn" @click.stop="handleDeleteAgent(agent)">
                <wd-icon name="delete" />
                <text>{{ t('home.delete') }}</text>
              </view>
            </view>
          </template>
        </wd-swipe-action>
      </view>
    </view>

    <!-- 自定义空状态 -->
    <template #empty>
      <view class="empty-state">
        <wd-icon name="robot" custom-class="empty-icon" />
        <text class="empty-text">
          {{ t('home.emptyState') }}
        </text>
        <text class="empty-desc">
          {{ t('home.createFirstAgent') }}
        </text>
      </view>
    </template>

    <!-- FAB 新增按钮 -->
    <wd-fab type="primary" icon="add" :draggable="true" :expandable="false" @click="openCreateDialog" />

    <!-- MessageBox 组件 -->
    <wd-message-box />
  </z-paging>
</template>

<style lang="scss" scoped>
.nebula-banner {
  background: linear-gradient(145deg, #0a0a14, #07070f, #0d0d1a, #07070f);
  position: relative;
  padding: 40rpx 40rpx 80rpx 40rpx;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background:
      radial-gradient(ellipse 300rpx 200rpx at 80% 20%, rgba(59, 130, 246, 0.08) 0%, transparent 70%),
      radial-gradient(ellipse 200rpx 150rpx at 20% 60%, rgba(139, 92, 246, 0.06) 0%, transparent 70%),
      radial-gradient(ellipse 150rpx 100rpx at 60% 80%, rgba(6, 182, 212, 0.04) 0%, transparent 70%);
    pointer-events: none;
  }

  .banner-content {
    position: relative;
    z-index: 2;
  }

  .welcome-info {
    .greeting {
      display: block;
      font-size: 48rpx;
      font-weight: 700;
      color: #f0f4f8;
      margin-bottom: 16rpx;
      text-shadow: 0 2rpx 8rpx rgba(59, 130, 246, 0.2);
    }

    .subtitle {
      display: block;
      font-size: 32rpx;
      color: rgba(240, 244, 248, 0.8);
      margin-bottom: 12rpx;
      font-weight: 500;

      .highlight {
        color: #60a5fa;
        font-weight: 600;
      }
    }
  }

  .star-decoration {
    position: absolute;
    top: 0;
    right: -60rpx;
    width: 300rpx;
    height: 100%;
    opacity: 0.6;
    pointer-events: none;

    .star {
      position: absolute;
      width: 6rpx;
      height: 6rpx;
      background: #60a5fa;
      border-radius: 50%;
      box-shadow: 0 0 20rpx 4rpx rgba(96, 165, 250, 0.4);
      animation: twinkle 3s ease-in-out infinite;

      &.star-2 {
        top: 30%;
        right: 40%;
        animation-delay: -1s;
        background: #8b5cf6;
        box-shadow: 0 0 20rpx 4rpx rgba(139, 92, 246, 0.4);
      }

      &.star-3 {
        top: 60%;
        right: 20%;
        animation-delay: -2s;
        background: #06b6d4;
        box-shadow: 0 0 20rpx 4rpx rgba(6, 182, 212, 0.4);
      }
    }
  }
}

@keyframes twinkle {
  0%, 100% {
    opacity: 0.4;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.5);
  }
}

// 内容区域开始标识，创建深色背景过渡
.content-section-header {
  background: #07070f;
  border-radius: 32rpx 32rpx 0 0;
  margin-top: -32rpx;
  height: 32rpx;
  position: relative;
  z-index: 1;
}

// z-paging内容区域样式
:deep(.z-paging-content) {
  background: #07070f;
  padding: 0 0 40rpx 0;
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 0 20rpx;
}

.agent-item {
  :deep(.wd-swipe-action) {
    border-radius: 16rpx;
    overflow: hidden;
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.2);
    border: 1rpx solid rgba(255, 255, 255, 0.04);
  }
}

.agent-card {
  background: rgba(255, 255, 255, 0.03);
  padding: 24rpx;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: 16rpx;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.06) 0%, transparent 50%, rgba(139, 92, 246, 0.04) 100%);
    pointer-events: none;
  }

  &:active {
    background: rgba(255, 255, 255, 0.06);
  }

  .card-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    position: relative;
    z-index: 1;
  }

  .card-main {
    flex: 1;
    width: 100%;
  }

  .agent-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12rpx;

    .agent-name {
      font-size: 32rpx;
      font-weight: 600;
      color: #f0f4f8;
    }
  }

  .model-info {
    margin-bottom: 16rpx;

    .model-text {
      display: block;
      font-size: 24rpx;
      color: #8b95a8;
      line-height: 1.5;
      margin-bottom: 4rpx;

      &:last-child {
        margin-bottom: 0;
      }
    }
  }

  .stats-row {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12rpx;
    flex-wrap: wrap;

    .stat-chip {
      display: flex;
      align-items: center;
      padding: 6rpx 12rpx;
      background: rgba(255, 255, 255, 0.04);
      border-radius: 20rpx;
      border: 1rpx solid rgba(255, 255, 255, 0.04);

      :deep(.chip-icon) {
        font-size: 20rpx;
        color: #8b95a8;
        margin-right: 6rpx;
      }

      .chip-text {
        font-size: 22rpx;
        color: #8b95a8;
      }
    }
  }

  :deep(.arrow-icon) {
    font-size: 24rpx;
    color: #5a6372;
    margin-left: 16rpx;
  }
}

.swipe-actions {
  display: flex;
  height: 100%;

  .action-btn {
    width: 120rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8rpx;
    color: #ffffff;
    font-size: 24rpx;
    font-weight: 500;
    transition: all 0.3s ease;

    &.delete-btn {
      background: #ff4d4f;

      &:active {
        background: #d9363e;
      }
    }

    :deep(.wd-icon) {
      font-size: 32rpx;
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100rpx 40rpx;
  text-align: center;

  :deep(.empty-icon) {
    font-size: 120rpx;
    color: #3a4252;
    margin-bottom: 32rpx;
  }

  .empty-text {
    font-size: 32rpx;
    color: #8b95a8;
    margin-bottom: 16rpx;
    font-weight: 500;
  }

  .empty-desc {
    font-size: 26rpx;
    color: #5a6372;
    line-height: 1.5;
  }
}

@keyframes pulse {
  0% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }

  100% {
    opacity: 1;
  }
}

.filter-actions {
  padding: 32rpx;
  text-align: center;
  border-top: 1rpx solid rgba(255, 255, 255, 0.04);
}
</style>
