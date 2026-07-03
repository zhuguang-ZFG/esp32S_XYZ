<route lang="jsonc" type="page">
{
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "AI 对话"
  }
}
</route>

<script lang="ts" setup>
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { t } from '@/i18n'
import { useChatMessages } from './composables/useChatMessages'
import { useChatHelpers } from './composables/useChatHelpers'
import { useChatStream, getAiHtml, onLongPressMessage as _onLongPressMessage, copyReply } from './composables/useChatStream'

const inputText = ref('')
const sending = ref(false)
const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 0)
const { messages, loadHistory, saveHistory, ensureWelcome, clearHistory: _clearHistory } = useChatMessages()
const { scrollToView, scrollToBottom, navigateBack } = useChatHelpers(messages)
const { handleSend, handleStop, regenerate } = useChatStream(messages, inputText, sending, saveHistory, scrollToBottom)

onLoad(() => {
  loadHistory()
  ensureWelcome()
  scrollToBottom()
})

function clearHistory() {
  uni.showModal({
    title: t('chat.clearTitle'),
    content: t('chat.clearConfirm'),
    success: (res) => {
      if (res.confirm) {
        _clearHistory()
        scrollToBottom()
      }
    },
  })
}

function onLongPressMessage(msg: any, index: number) {
  _onLongPressMessage(msg, index, regenerate)
}
</script>

<template>
  <view class="chat-page page-enter">
    <!-- 导航栏 -->
    <view class="chat-nav" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-content">
        <view class="nav-back" @click="navigateBack">
          <wd-icon name="arrow-left" size="20" color="var(--text)" />
        </view>
        <text class="nav-title">
          {{ t('chat.title') }}
        </text>
        <view class="nav-action" @click="clearHistory">
          <wd-icon name="delete" size="18" color="var(--dim)" />
        </view>
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view class="chat-scroll" scroll-y :scroll-into-view="scrollToView" scroll-with-animation :scroll-animation-duration="200">
      <view class="chat-list">
        <view
          v-for="(msg, index) in messages"
          :id="`msg-${msg.id}`"
          :key="msg.id"
          class="msg-row"
          :class="[msg.role, { 'msg-error': msg.error }]"
          @longpress="onLongPressMessage(msg, index)"
        >
          <!-- 用户消息 -->
          <view v-if="msg.role === 'user'" class="user msg-bubble">
            <text class="msg-content">
              {{ msg.content }}
            </text>
            <text class="msg-time">
              {{ msg.time }}
            </text>
          </view>

          <!-- AI 消息 -->
          <view v-else class="msg-bubble assistant">
            <rich-text class="msg-rich-text" :nodes="getAiHtml(msg.content)" />
            <view class="msg-footer">
              <text class="msg-time">
                {{ msg.time }}
              </text>
              <view v-if="!msg.streaming" class="msg-actions">
                <text class="action-btn" @click="copyReply(msg.content)">
                  {{ t('chat.copy') }}
                </text>
              </view>
            </view>
            <view v-if="msg.streaming" class="typing-cursor" />
          </view>
        </view>
      </view>
      <view style="height: 40rpx;" />
    </scroll-view>

    <!-- 输入区 -->
    <view class="chat-input-bar">
      <view class="input-box">
        <input
          v-model="inputText"
          class="input-field"
          :placeholder="t('chat.inputPlaceholder')"
          placeholder-class="input-placeholder"
          confirm-type="send"
          :disabled="sending"
          @confirm="handleSend"
        >
      </view>
      <view v-if="!sending" class="send-btn" :class="{ disabled: !inputText.trim() }" @click="handleSend">
        <wd-icon name="arrow-right" size="20" color="#fff" />
      </view>
      <view v-else class="stop-btn" @click="handleStop">
        <view class="stop-square" />
      </view>
    </view>
  </view>
</template>

<style src="./chat.scss" lang="scss" scoped></style>
