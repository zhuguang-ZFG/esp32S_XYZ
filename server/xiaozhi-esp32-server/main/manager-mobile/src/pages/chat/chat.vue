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
import { chatCompletion, type ChatMessage } from '@/api/chat/chat'

interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string
  time: string
}

const messages = ref<DisplayMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const scrollToView = ref('')

onLoad(() => {
  // 欢迎消息
  messages.value.push({
    role: 'assistant',
    content: '你好！我是 LiMa 星云 AI 助手。你可以问我任何问题，我会尽力帮你解答。',
    time: formatTime(new Date()),
  })
  scrollToBottom()
})

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: text,
    time: formatTime(new Date()),
  })
  inputText.value = ''
  scrollToBottom()

  sending.value = true
  try {
    const chatMessages: ChatMessage[] = messages.value.map(m => ({
      role: m.role,
      content: m.content,
    }))
    const reply = await chatCompletion(chatMessages)
    messages.value.push({
      role: 'assistant',
      content: reply || '（无响应）',
      time: formatTime(new Date()),
    })
    scrollToBottom()
  } catch (e: any) {
    messages.value.push({
      role: 'assistant',
      content: `抱歉，请求出错了：${e.message || '未知错误'}`,
      time: formatTime(new Date()),
    })
    scrollToBottom()
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const id = `msg-${messages.value.length - 1}`
    scrollToView.value = id
  })
}

function formatTime(d: Date) {
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}
</script>

<template>
  <view class="chat-page">
    <!-- 导航栏 -->
    <view class="chat-nav" :style="{ paddingTop: (uni.getSystemInfoSync().statusBarHeight || 0) + 'px' }">
      <view class="nav-content">
        <view class="nav-back" @click="uni.navigateBack()">
          <text class="back-icon">‹</text>
        </view>
        <text class="nav-title">AI 对话</text>
        <view class="nav-placeholder" />
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="chat-scroll"
      scroll-y
      :scroll-into-view="scrollToView"
      scroll-with-animation
    >
      <view class="chat-list">
        <view
          v-for="(msg, index) in messages"
          :id="`msg-${index}`"
          :key="index"
          class="msg-row"
          :class="msg.role"
        >
          <view class="msg-bubble">
            <text class="msg-content">{{ msg.content }}</text>
            <text class="msg-time">{{ msg.time }}</text>
          </view>
        </view>
        <view v-if="sending" class="msg-row assistant">
          <view class="msg-bubble typing">
            <text class="typing-dot">●</text>
            <text class="typing-dot">●</text>
            <text class="typing-dot">●</text>
          </view>
        </view>
      </view>
      <view style="height: 20rpx;" />
    </scroll-view>

    <!-- 输入区 -->
    <view class="chat-input-bar">
      <view class="input-box">
        <input
          v-model="inputText"
          class="input-field"
          placeholder="输入消息..."
          placeholder-class="input-placeholder"
          confirm-type="send"
          :disabled="sending"
          @confirm="handleSend"
        />
      </view>
      <view
        class="send-btn"
        :class="{ disabled: !inputText.trim() || sending }"
        @click="handleSend"
      >
        <text class="send-icon">➤</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(180deg, #07070f 0%, #0a0a14 100%);
}

/* 导航栏 */
.chat-nav {
  background: rgba(7, 7, 15, 0.95);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);
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

    .back-icon {
      font-size: 48rpx;
      color: #f0f4f8;
      line-height: 1;
    }
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: #f0f4f8;
  }

  .nav-placeholder {
    width: 60rpx;
  }
}

/* 消息列表 */
.chat-scroll {
  flex: 1;
  overflow: hidden;
}

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

    .msg-bubble {
      background: linear-gradient(135deg, #3b82f6, #8b5cf6);
      border-bottom-right-radius: 4rpx;
    }
  }

  &.assistant {
    justify-content: flex-start;

    .msg-bubble {
      background: rgba(255, 255, 255, 0.06);
      border: 1rpx solid rgba(255, 255, 255, 0.06);
      border-bottom-left-radius: 4rpx;
    }
  }
}

.msg-bubble {
  max-width: 70%;
  padding: 20rpx 28rpx;
  border-radius: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;

  .msg-content {
    font-size: 30rpx;
    color: #f0f4f8;
    line-height: 1.6;
    word-break: break-word;
  }

  .msg-time {
    font-size: 20rpx;
    color: rgba(240, 244, 248, 0.4);
    align-self: flex-end;
  }

  &.typing {
    flex-direction: row;
    gap: 12rpx;
    padding: 24rpx 32rpx;

    .typing-dot {
      font-size: 20rpx;
      color: #8b95a8;
      animation: bounce 1.4s infinite ease-in-out both;

      &:nth-child(1) { animation-delay: -0.32s; }
      &:nth-child(2) { animation-delay: -0.16s; }
    }
  }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* 输入区 */
.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: rgba(10, 10, 20, 0.95);
  backdrop-filter: blur(20rpx);
  border-top: 1rpx solid rgba(255, 255, 255, 0.04);
  flex-shrink: 0;
}

.input-box {
  flex: 1;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
  border-radius: 40rpx;
  padding: 16rpx 28rpx;
}

.input-field {
  font-size: 30rpx;
  color: #f0f4f8;
  height: 48rpx;
  line-height: 48rpx;
}

.input-placeholder {
  color: #5a6372;
}

.send-btn {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.2s;

  &:active {
    opacity: 0.8;
    transform: scale(0.95);
  }

  &.disabled {
    opacity: 0.4;
  }

  .send-icon {
    font-size: 28rpx;
    color: #fff;
    margin-left: 4rpx;
  }
}
</style>
