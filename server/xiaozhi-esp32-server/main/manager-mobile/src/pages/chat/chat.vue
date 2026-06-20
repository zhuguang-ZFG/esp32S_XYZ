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
import { nextTick, ref } from 'vue'
import { chatCompletionStream, type ChatMessage } from '@/api/chat/chat'
import { t } from '@/i18n'

interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string
  time: string
  streaming?: boolean
}

const messages = ref<DisplayMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const scrollToView = ref('')

let abortController: { abort: () => void } | null = null

onLoad(() => {
  messages.value.push({
    role: 'assistant',
    content: t('chat.welcome'),
    time: formatTime(new Date()),
  })
  scrollToBottom()
})

function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  messages.value.push({ role: 'user', content: text, time: formatTime(new Date()) })
  inputText.value = ''
  scrollToBottom()
  sending.value = true

  const aiIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', time: formatTime(new Date()), streaming: true })
  scrollToBottom()

  const chatMessages: ChatMessage[] = messages.value
    .filter(m => !m.streaming && m.content)
    .map(m => ({ role: m.role, content: m.content }))

  abortController = chatCompletionStream(chatMessages, (chunk, done) => {
    const msg = messages.value[aiIndex]
    if (msg) {
      msg.content += chunk
      msg.streaming = !done
      if (done) msg.time = formatTime(new Date())
    }
    scrollToBottom()
    if (done) { sending.value = false; abortController = null }
  })
}

function handleStop() {
  if (abortController) {
    abortController.abort()
    abortController = null
    sending.value = false
    const last = messages.value[messages.value.length - 1]
    if (last && last.streaming) { last.streaming = false; last.time = formatTime(new Date()) }
  }
}

function scrollToBottom() {
  nextTick(() => {
    const id = `msg-${messages.value.length - 1}`
    scrollToView.value = id
    setTimeout(() => { scrollToView.value = ''; nextTick(() => { scrollToView.value = id }) }, 50)
  })
}

function formatTime(d: Date) {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function navigateBack() { uni.navigateBack() }
</script>

<template>
  <view class="chat-page">
    <view class="chat-nav" :style="{ paddingTop: (uni.getSystemInfoSync().statusBarHeight || 0) + 'px' }">
      <view class="nav-content">
        <view class="nav-back" @click="navigateBack">
          <wd-icon name="arrow-left" size="20" color="#1d1d1f" />
        </view>
        <text class="nav-title">{{ t('chat.title') }}</text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <scroll-view class="chat-scroll" scroll-y :scroll-into-view="scrollToView" scroll-with-animation :scroll-animation-duration="200">
      <view class="chat-list">
        <view v-for="(msg, index) in messages" :id="`msg-${index}`" :key="index" class="msg-row" :class="msg.role">
          <view class="msg-bubble" :class="{ streaming: msg.streaming }">
            <text class="msg-content">{{ msg.content }}</text>
            <text class="msg-time">{{ msg.time }}</text>
            <view v-if="msg.streaming" class="typing-cursor" />
          </view>
        </view>
      </view>
      <view style="height: 40rpx;" />
    </scroll-view>

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
        />
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

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f5f7;
}

.chat-nav {
  background: #fff;
  border-bottom: 1rpx solid #eee;
  flex-shrink: 0;

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
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: #1d1d1f;
  }

  .nav-placeholder { width: 60rpx; }
}

.chat-scroll { flex: 1; overflow: hidden; }

.chat-list {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.msg-row {
  display: flex;

  &.user {
    justify-content: flex-end;
    .msg-bubble { background: #336cff; border-bottom-right-radius: 4rpx; }
    .msg-content { color: #fff; }
    .msg-time { color: rgba(255, 255, 255, 0.6); }
  }

  &.assistant {
    justify-content: flex-start;
    .msg-bubble { background: #fff; box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04); border-bottom-left-radius: 4rpx; }
    .msg-content { color: #1d1d1f; }
    .msg-time { color: #9d9ea3; }
  }
}

.msg-bubble {
  max-width: 70%;
  padding: 20rpx 28rpx;
  border-radius: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  position: relative;
}

.msg-content { font-size: 30rpx; line-height: 1.6; word-break: break-word; white-space: pre-wrap; }
.msg-time { font-size: 20rpx; align-self: flex-end; }

.typing-cursor {
  position: absolute;
  right: 20rpx;
  bottom: 20rpx;
  width: 4rpx;
  height: 28rpx;
  background: #336cff;
  animation: blink 1s infinite;
  border-radius: 2rpx;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
  flex-shrink: 0;
}

.input-box {
  flex: 1;
  background: #f5f5f7;
  border-radius: 40rpx;
  padding: 16rpx 28rpx;
}

.input-field { font-size: 30rpx; color: #1d1d1f; height: 48rpx; line-height: 48rpx; }
.input-placeholder { color: #9d9ea3; }

.send-btn {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #336cff;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active { opacity: 0.8; }
  &.disabled { opacity: 0.4; }
}

.stop-btn {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #ff4d4f;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active { opacity: 0.8; }
}

.stop-square { width: 24rpx; height: 24rpx; background: #fff; border-radius: 4rpx; }
</style>
