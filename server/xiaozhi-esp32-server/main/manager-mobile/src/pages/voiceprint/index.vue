<route lang="jsonc" type="page">
{
  "needLogin": true,
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "声纹管理"
  }
}
</route>

<script lang="ts" setup>
import type { VoicePrint } from '@/api/voiceprint'
import { onLoad } from '@dcloudio/uni-app'
import { computed, onMounted, ref, watch } from 'vue'
import { useMessage } from 'wot-design-uni/components/wd-message-box'
import { t } from '@/i18n'
import { useAudioPlayer } from './composables/useAudioPlayer'
import { useVoicePrintCrud } from './composables/useVoicePrintCrud'

defineOptions({
  name: 'VoicePrintManage',
})

const emits = defineEmits(['update-refresher-enabled'])

// 获取屏幕边界到安全区域距离
let safeAreaInsets: any
let systemInfo: any

// #ifdef MP-WEIXIN
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
systemInfo = uni.getSystemInfoSync()
safeAreaInsets = systemInfo.safeAreaInsets
// #endif

const message = useMessage()

// 通过路由 query 参数接收 deviceId（页面导航用 onLoad，不是 props）
const routeDeviceId = ref('')
onLoad((opt: any) => {
  routeDeviceId.value = opt?.deviceId || ''
})
const currentDeviceId = computed(() => routeDeviceId.value)

// --- CRUD + 表单 + 成员/对话记录（P3.1 提取到 useVoicePrintCrud）---
const {
  voicePrintList,
  chatHistoryActions,
  memberList,
  swipeStates,
  loading,
  showAddDialog,
  showEditDialog,
  showChatHistoryDialog,
  addForm,
  editForm,
  memberPickerRange,
  selectedMemberName,
  loadVoicePrintList,
  refresh,
  loadMemberList,
  onMemberChange,
  loadChatHistory,
  openAddDialog,
  getSelectedAudioContent,
  handleItemClick,
  submitAdd,
  submitEdit,
  handleEdit,
  handleDelete,
} = useVoicePrintCrud(() => currentDeviceId.value, message)

// --- 音频试听（P3.1 提取到 useAudioPlayer）---
const { playingAudioId, playAudio, stopAudio } = useAudioPlayer()

watch(() => [showAddDialog.value, showEditDialog.value], (newValues) => {
  if (newValues.some((value: boolean) => value)) {
    emits('update-refresher-enabled', false)
  }
  else {
    emits('update-refresher-enabled', true)
  }
})

onMounted(async () => {
  loadVoicePrintList()
  loadMemberList()
  loadChatHistory()
})

// 暴露方法给父组件
defineExpose({
  showAddDialog,
  showEditDialog,
  refresh,
})
</script>

<template>
  <view class="voiceprint-container page-enter" style="background: var(--bg); min-height: 100%;">
    <!-- 加载状态 -->
    <view v-if="loading && voicePrintList.length === 0" class="loading-container">
      <wd-loading color="#2dd4a7" />
      <text class="loading-text">
        {{ t('voiceprint.loading') }}
      </text>
    </view>

    <!-- 声纹列表 -->
    <view v-else-if="voicePrintList.length > 0" class="voiceprint-list">
      <!-- 声纹卡片列表 -->
      <view class="box-border flex flex-col gap-[24rpx] p-[20rpx]">
        <view v-for="item in voicePrintList" :key="item.id">
          <wd-swipe-action
            :model-value="swipeStates[item.id] || 'close'"
            @update:model-value="swipeStates[item.id] = $event"
          >
            <view class="bg-[var(--surface)] border border-[1rpx] border-[var(--border)] p-[32rpx] shadow-[0_4rpx_20rpx_rgba(0,0,0,0.2)]" @click="handleEdit(item)">
              <view>
                <text class="mb-[12rpx] block text-[32rpx] text-[var(--text)] font-semibold">
                  {{ item.sourceName }}
                </text>
                <text class="mb-[12rpx] block text-[28rpx] text-[var(--muted)] leading-[1.4]">
                  {{ item.introduce || '暂无描述' }}
                </text>
                <text class="block text-[24rpx] text-[var(--dim)]">
                  {{ item.createDate }}
                </text>
              </view>
            </view>

            <template #right>
              <view class="h-full flex">
                <view
                  class="h-full min-w-[120rpx] flex items-center justify-center bg-[#ff4d4f] p-x-[32rpx] text-[28rpx] text-white font-medium"
                  @click="handleDelete(item.id)"
                >
                  <wd-icon name="delete" />
                  {{ t('voiceprint.delete') }}
                </view>
              </view>
            </template>
          </wd-swipe-action>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-else-if="!loading" class="empty-container">
      <view class="flex flex-col items-center justify-center p-[100rpx_40rpx] text-center">
        <wd-icon name="voice" custom-class="text-[120rpx] text-[var(--dim)] mb-[32rpx]" />
        <text class="mb-[32rpx] text-[32rpx] text-[var(--muted)] font-medium">
          {{ t('voiceprint.emptyTitle') }}
        </text>
        <text class="text-[26rpx] text-[var(--dim)] leading-[1.5]">
          {{ t('voiceprint.emptyDesc') }}
        </text>
      </view>
    </view>

    <!-- 浮动操作按钮 -->
    <wd-fab custom-style="z-index:10" type="primary" size="small" :draggable="true" :expandable="false" @click="openAddDialog">
      <wd-icon name="add" />
    </wd-fab>

    <!-- MessageBox 组件 -->
    <wd-message-box />
  </view>

  <!-- 添加说话人弹窗 -->
  <wd-popup
    v-model="showAddDialog"
    position="center"
    custom-style="width: 90%; max-width: 400px; border-radius: 16px;"
    safe-area-inset-bottom
  >
    <view>
      <view class="p-[32rpx]">
        <!-- 成员选择 -->
        <view class="mb-[32rpx]">
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            <text class="text-red">
              *
            </text>
            {{ t('voiceprint.member') }}
          </text>
          <picker mode="selector" :range="memberPickerRange" @change="onMemberChange">
            <view
              class="flex cursor-pointer items-center justify-between border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[20rpx] transition-all duration-300 active:bg-[#14181f]"
            >
              <text
                class="m-r-[16rpx] flex-1 text-left text-[26rpx] text-[var(--text)]"
                :class="{ 'text-[var(--dim)]': !addForm.memberId }"
              >
                {{ selectedMemberName || t('voiceprint.pleaseSelectMember') }}
              </text>
              <wd-icon name="arrow-down" custom-class="text-[20rpx] text-[var(--dim)]" />
            </view>
          </picker>
        </view>

        <!-- 声纹向量选择 -->
        <view class="mb-[32rpx]">
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            <text class="text-red">
              *
            </text>
            {{ t('voiceprint.voiceVector') }}
          </text>
          <view
            class="flex cursor-pointer items-center justify-between border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[20rpx] transition-all duration-300 active:bg-[#14181f]"
            @click="showChatHistoryDialog = true"
          >
            <text
              class="m-r-[16rpx] flex-1 text-left text-[26rpx] text-[var(--text)]"
              :class="{ 'text-[var(--dim)]': !addForm.audioId }"
            >
              {{ getSelectedAudioContent(addForm.audioId) }}
            </text>
            <wd-icon name="arrow-down" custom-class="text-[20rpx] text-[var(--dim)]" />
          </view>
        </view>

        <!-- 姓名 -->
        <view class="mb-[32rpx]">
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            <text class="text-red">
              *
            </text>
            {{ t('voiceprint.name') }}
          </text>
          <input
            v-model="addForm.sourceName"
            class="box-border h-[80rpx] w-full border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[16rpx_20rpx] text-[28rpx] text-[var(--text)] leading-[1.4] outline-none focus:border-[#2dd4a7] focus:bg-[var(--surface)] placeholder:text-[var(--dim)]"
            type="text" :placeholder="t('voiceprint.pleaseInputName')"
          >
        </view>

        <!-- 描述 -->
        <view>
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            <text class="text-red">
              *
            </text>
            {{ t('voiceprint.description') }}
          </text>
          <textarea
            v-model="addForm.introduce" :maxlength="100" :placeholder="t('voiceprint.pleaseInputDescription')"
            class="box-border h-[200rpx] w-full resize-none border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[20rpx] text-[26rpx] text-[var(--text)] leading-[1.6] outline-none focus:border-[#2dd4a7] focus:bg-[var(--surface)] placeholder:text-[var(--dim)]"
          />
          <view class="mt-[8rpx] text-right text-[22rpx] text-[var(--dim)]">
            {{ (addForm.introduce || '').length }}/100
          </view>
        </view>
      </view>

      <view class="flex gap-[16rpx] border-t-[2rpx] border-[var(--border)] p-[24rpx_32rpx_32rpx]">
        <wd-button type="info" custom-class="flex-1" @click="showAddDialog = false">
          {{ t('voiceprint.cancel') }}
        </wd-button>
        <wd-button type="primary" custom-class="flex-1" @click="submitAdd">
          {{ t('voiceprint.save') }}
        </wd-button>
      </view>
    </view>
  </wd-popup>

  <!-- 编辑说话人弹窗 -->
  <wd-popup
    v-model="showEditDialog" position="center" custom-style="width: 90%; max-width: 400px; border-radius: 16px;"
    safe-area-inset-bottom
  >
    <view>
      <view class="box-border w-full flex items-center justify-between border-b-[2rpx] border-[var(--border)] p-[32rpx_32rpx_24rpx]">
        <text class="w-full text-center text-[32rpx] text-[var(--text)] font-semibold">
          {{ t('voiceprint.editSpeaker') }}
        </text>
      </view>

      <view class="p-[32rpx]">
        <!-- 声纹向量选择 -->
        <view class="mb-[32rpx]">
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            <text class="text-red">
              *
            </text>
            {{ t('voiceprint.voiceVector') }}
          </text>
          <view
            class="flex cursor-pointer items-center justify-between border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[20rpx] transition-all duration-300 active:bg-[#14181f]"
            @click="showChatHistoryDialog = true"
          >
            <text
              class="m-r-[16rpx] flex-1 text-left text-[26rpx] text-[var(--text)]"
              :class="{ 'text-[var(--dim)]': !editForm.audioId }"
            >
              {{ getSelectedAudioContent(editForm.audioId) }}
            </text>
            <wd-icon name="arrow-down" custom-class="text-[20rpx] text-[var(--dim)]" />
          </view>
        </view>

        <!-- 姓名 -->
        <view class="mb-[32rpx]">
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            <text class="text-red">
              *
            </text>
            {{ t('voiceprint.name') }}
          </text>
          <input
            v-model="editForm.sourceName"
            class="box-border h-[80rpx] w-full border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[16rpx_20rpx] text-[28rpx] text-[var(--text)] leading-[1.4] outline-none focus:border-[#2dd4a7] focus:bg-[var(--surface)] placeholder:text-[var(--dim)]"
            type="text" :placeholder="t('voiceprint.pleaseInputName')"
          >
        </view>

        <!-- 描述 -->
        <view>
          <text class="mb-[16rpx] block text-[28rpx] text-[var(--text)] font-medium">
            <text class="text-red">
              *
            </text>
            {{ t('voiceprint.description') }}
          </text>
          <textarea
            v-model="editForm.introduce" :maxlength="100" :placeholder="t('voiceprint.pleaseInputDescription')"
            class="box-border h-[200rpx] w-full resize-none border-[1rpx] border-[var(--border)] rounded-[12rpx] bg-[#14181f] p-[20rpx] text-[26rpx] text-[var(--text)] leading-[1.6] outline-none focus:border-[#2dd4a7] focus:bg-[var(--surface)] placeholder:text-[var(--dim)]"
          />
          <view class="mt-[8rpx] text-right text-[22rpx] text-[var(--dim)]">
            {{ (editForm.introduce || '').length }}/100
          </view>
        </view>
      </view>

      <view class="flex gap-[16rpx] border-t-[2rpx] border-[var(--border)] p-[24rpx_32rpx_32rpx]">
        <wd-button type="info" custom-class="flex-1" @click="showEditDialog = false">
          {{ t('voiceprint.cancel') }}
        </wd-button>
        <wd-button type="primary" custom-class="flex-1" @click="submitEdit">
          {{ t('voiceprint.save') }}
        </wd-button>
      </view>
    </view>
  </wd-popup>

  <!-- 自定义语音对话记录选择弹出层 -->
  <wd-popup v-model="showChatHistoryDialog" class="custom-popup" position="bottom" @close="stopAudio">
    <view class="rounded-[20rpx] bg-[var(--surface)] pb-[20rpx] pt-[20rpx]">
      <view class="max-h-[600rpx] overflow-y-auto rounded-[20rpx]">
        <view
          v-for="item in chatHistoryActions"
          :key="item.audioId"
          class="flex items-center justify-between border-b border-[var(--border)] p-[32rpx] transition-all active:bg-[#14181f]"
          @click="handleItemClick(item)"
        >
          <text class="flex-1 text-[28rpx] text-[var(--text)]">
            {{ item.name }}
          </text>
          <view class="ml-[20rpx]" @click.stop="playAudio(item.audioId, $event)">
            <wd-icon
              :name="playingAudioId === item.audioId ? 'pause-circle' : 'play-circle'"
              size="24px"
              :custom-class="playingAudioId === item.audioId ? 'text-[#2dd4a7]' : 'text-[var(--dim)]'"
            />
          </view>
        </view>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.voiceprint-container {
  position: relative;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100rpx 40rpx;
}

.loading-text {
  margin-top: 20rpx;
  font-size: 28rpx;
  color: var(--muted);
}

::v-deep .custom-popup {
  .wd-popup {
    padding: 20rpx !important;
    background: var(--surface);
  }
}
</style>
