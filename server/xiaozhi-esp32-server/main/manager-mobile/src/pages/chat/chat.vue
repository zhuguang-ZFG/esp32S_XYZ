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
import { markdownToHtml, stripMarkdown, hasMarkdown } from '@/utils/markdown'

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
    content: '你好！我是 LiMa 星云 AI 助手。你可以问我任何问题，我会实时为你解答。',
    time: formatTime(new Date()),
  })
  scrollToBottom()
})

function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  messages.value.push({
    role: 'user',
    content: text,
    time: formatTime(new Date()),
  })
  inputText.value = ''
  scrollToBottom()

  sending.value = true

  const aiIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    time: formatTime(new Date()),
    streaming: true,
  })
  scrollToBottom()

  const chatMessages: ChatMessage[] = messages.value
    .filter(m => !m.streaming && m.content)
    .map(m => ({ role: m.role, content: m.content }))

  abortController = chatCompletionStream(chatMessages, (chunk, done) => {
    const msg = messages.value[aiIndex]
    if (msg) {
      msg.content += chunk
      msg.streaming = !done
      if (done) {
        msg.time = formatTime(new Date())
      }
    }
    scrollToBottom()

    if (done) {
      sending.value = false
      abortController = null
    }
  })
}

function handleStop() {
  if (abortController) {
    abortController.abort()
    abortController = null
    sending.value = false
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.streaming) {
      lastMsg.streaming = false
      lastMsg.time = formatTime(new Date())
    }
  }
}

function scrollToBottom() {
  nextTick(() => {
    const id = `msg-${messages.value.length - 1}`
    scrollToView.value = id
    setTimeout(() => {
      scrollToView.value = ''
      nextTick(() => {
        scrollToView.value = id
      })
    }, 50)
  })
}

function formatTime(d: Date) {
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

function navigateBack() { uni.navigateBack() }

// 长按消息：复制 / 重新生成
function onLongPressMessage(msg: DisplayMessage, index: number) {
  const actions = ['复制内容']
  if (msg.role === 'assistant' && index > 0) {
    actions.push('重新生成')
  }

  uni.showActionSheet({
    itemList: actions,
    success: (res) => {
      if (res.tapIndex === 0) {
        const text = stripMarkdown(msg.content)
        uni.setClipboardData({
          data: text,
          success: () => uni.showToast({ title: '已复制', icon: 'success' }),
        })
      } else if (res.tapIndex === 1 && msg.role === 'assistant') {
        regenerate(index)
      }
    },
  })
}

// 重新生成：删除当前 AI 回复，重新发送上一条用户消息
function regenerate(aiIndex: number) {
  if (sending.value) return
  // 找到上一条用户消息
  const userIndex = aiIndex - 1
  if (userIndex < 0 || messages.value[userIndex]?.role !== 'user') return

  const userMsg = messages.value[userIndex]

  // 删除当前 AI 回复及后续消息
  messages.value.splice(aiIndex)

  // 重新发送
  sending.value = true
  messages.value.push({
    role: 'assistant',
    content: '',
    time: formatTime(new Date()),
    streaming: true,
  })
  const newAiIndex = messages.value.length - 1
  scrollToBottom()

  const chatMessages: ChatMessage[] = messages.value
    .filter((m, i) => i < aiIndex && m.content)
    .map(m => ({ role: m.role, content: m.content }))

  abortController = chatCompletionStream(chatMessages, (chunk, done) => {
    const msg = messages.value[newAiIndex]
    if (msg) {
      msg.content += chunk
      msg.streaming = !done
      if (done) msg.time = formatTime(new Date())
    }
    scrollToBottom()
    if (done) {
      sending.value = false
      abortController = null
    }
  })
}

// 复制 AI 回复
function copyReply(content: string) {
  const text = stripMarkdown(content)
  uni.setClipboardData({
    data: text,
    success: () => uni.showToast({ title: '已复制', icon: 'success' }),
  })
}

// 获取 AI 消息渲染 HTML
function getAiHtml(content: string): string {
  if (!content) return ''
  if (hasMarkdown(content)) {
    return markdownToHtml(content)
  }
  // 纯文本：保留换行
  return content.replace(/\n/g, '<br>')
}
</script>

<template>
  <view class="chat-page">
    <!-- 导航栏 -->
    <view class="chat-nav" :style="{ paddingTop: (uni.getSystemInfoSync().statusBarHeight || 0) + 'px' }">
      <view class="nav-content">
        <view class="nav-back" @click="navigateBack">
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
      :scroll-animation-duration="200"
    >
      <view class="chat-list">
        <view
          v-for="(msg, index) in messages"
          :id="`msg-${index}`"
          :key="index"
          class="msg-row"
          :class="msg.role"
          @longpress="onLongPressMessage(msg, index)"
        >
          <!-- 用户消息 -->
          <view v-if="msg.role === 'user'" class="msg-bubble user">
            <text class="msg-content">{{ msg.content }}</text>
            <text class="msg-time">{{ msg.time }}</text>
          </view>

          <!-- AI 消息 -->
          <view v-else class="msg-bubble assistant">
            <rich-text
              class="msg-rich-text"
              :nodes="getAiHtml(msg.content)"
            />
            <view class="msg-footer">
              <text class="msg-time">{{ msg.time }}</text>
              <view v-if="!msg.streaming" class="msg-actions">
                <text class="action-btn" @click="copyReply(msg.content)">复制</text>
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
          placeholder="输入消息..."
          placeholder-class="input-placeholder"
          confirm-type="send"
          :disabled="sending"
          @confirm="handleSend"
        />
      </view>
      <view
        v-if="!sending"
        class="send-btn"
        :class="{ disabled: !inputText.trim() }"
        @click="handleSend"
      >
        <text class="send-icon">➤</text>
      </view>
      <view
        v-else
        class="stop-btn"
        @click="handleStop"
      >
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
  max-width: 75%;
  padding: 20rpx 28rpx;
  border-radius: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  position: relative;

  .msg-content {
    font-size: 30rpx;
    color: #f0f4f8;
    line-height: 1.6;
    word-break: break-word;
    white-space: pre-wrap;
  }

  .msg-time {
    font-size: 20rpx;
    color: rgba(240, 244, 248, 0.4);
    align-self: flex-end;
  }

  &.streaming .msg-content {
    padding-right: 8rpx;
  }
}

/* rich-text 渲染 */
.msg-rich-text {
  font-size: 28rpx;
  line-height: 1.7;
  color: #f0f4f8;

  :deep(pre) {
    background: rgba(0, 0, 0, 0.4);
    border: 1rpx solid rgba(255, 255, 255, 0.08);
    border-radius: 12rpx;
    padding: 20rpx;
    margin: 12rpx 0;
    overflow-x: auto;
    font-family: monospace;
    font-size: 26rpx;
    color: #e0e6ed;
    white-space: pre-wrap;
    word-break: break-word;
  }

  :deep(code) {
    font-family: monospace;
    font-size: 26rpx;
  }

  :deep(.inline-code) {
    background: rgba(59, 130, 246, 0.15);
    padding: 2rpx 10rpx;
    border-radius: 6rpx;
    color: #60a5fa;
  }

  :deep(strong) {
    font-weight: 700;
    color: #fff;
  }

  :deep(em) {
    font-style: italic;
    color: #c0c8d8;
  }

  :deep(blockquote) {
    border-left: 4rpx solid #3b82f6;
    padding-left: 20rpx;
    margin: 12rpx 0;
    color: #8b95a8;
  }

  :deep(ul), :deep(ol) {
    padding-left: 32rpx;
    margin: 8rpx 0;
  }

  :deep(li) {
    margin: 4rpx 0;
  }

  :deep(a) {
    color: #3b82f6;
    text-decoration: underline;
  }

  :deep(br) {
    display: block;
    content: '';
    margin-bottom: 4rpx;
  }
}

/* 消息底部操作 */
.msg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;

  .msg-time {
    font-size: 20rpx;
    color: rgba(240, 244, 248, 0.4);
  }

  .msg-actions {
    display: flex;
    gap: 16rpx;
  }

  .action-btn {
    font-size: 22rpx;
    color: #3b82f6;
    padding: 4rpx 12rpx;
    background: rgba(59, 130, 246, 0.08);
    border-radius: 8rpx;

    &:active {
      opacity: 0.7;
    }
  }
}

/* 打字光标 */
.typing-cursor {
  position: absolute;
  right: 20rpx;
  bottom: 20rpx;
  width: 4rpx;
  height: 28rpx;
  background: #3b82f6;
  animation: blink 1s infinite;
  border-radius: 2rpx;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
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

.stop-btn {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: rgba(255, 80, 80, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.2s;

  &:active {
    opacity: 0.8;
    transform: scale(0.95);
  }

  .stop-square {
    width: 24rpx;
    height: 24rpx;
    background: #fff;
    border-radius: 4rpx;
  }
}
</style>
