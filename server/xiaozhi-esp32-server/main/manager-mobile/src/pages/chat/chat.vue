<route lang="jsonc" type="page">
{
  "style": {
    "navigationStyle": "custom",
    "navigationBarTitleText": "AI 对话"
  }
}
</route>

<script lang="ts" setup>
import type { ChatMessage } from '@/api/chat/chat'
import { onLoad } from '@dcloudio/uni-app'
import { nextTick, ref } from 'vue'
import { chatCompletionStream } from '@/api/chat/chat'
import { t } from '@/i18n'
import { hasMarkdown, markdownToHtml, stripMarkdown } from '@/utils/markdown'

interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string
  time: string
  streaming?: boolean
  error?: boolean
  id: string
}

function genMsgId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

const CHAT_HISTORY_KEY = 'lima_chat_history'
const MAX_HISTORY = 100

const messages = ref<DisplayMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const scrollToView = ref('')
const statusBarHeight = ref(uni.getSystemInfoSync().statusBarHeight || 0)

let abortController: { abort: () => void } | null = null

onLoad(() => {
  loadHistory()
  if (!messages.value.length) {
    messages.value.push({
      id: genMsgId(),
      role: 'assistant',
      content: t('chat.welcome'),
      time: formatTime(new Date()),
    })
  }
  scrollToBottom()
})

function loadHistory() {
  try {
    const raw = uni.getStorageSync(CHAT_HISTORY_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as DisplayMessage[]
      messages.value = parsed.filter(m => m.role && m.content).slice(-MAX_HISTORY)
    }
  }
  catch (e) { /* ignore corrupted history */ }
}

function saveHistory() {
  try {
    const toSave = messages.value
      .filter(m => !m.streaming && m.content.trim())
      .slice(-MAX_HISTORY)
    uni.setStorageSync(CHAT_HISTORY_KEY, JSON.stringify(toSave))
  }
  catch (e) { console.error('save chat history failed', e) }
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value)
    return

  messages.value.push({ id: genMsgId(), role: 'user', content: text, time: formatTime(new Date()) })
  inputText.value = ''
  scrollToBottom()
  saveHistory()

  sending.value = true

  const aiIndex = messages.value.length
  messages.value.push({ id: genMsgId(), role: 'assistant', content: '', time: formatTime(new Date()), streaming: true })
  scrollToBottom()

  const chatMessages: ChatMessage[] = messages.value
    .filter(m => !m.streaming && m.content)
    .map(m => ({ role: m.role, content: m.content }))

  abortController = chatCompletionStream(chatMessages, (chunk, done) => {
    const msg = messages.value[aiIndex]
    if (msg) {
      msg.content += chunk
      msg.streaming = !done
      if (done)
        msg.time = formatTime(new Date())
    }
    scrollToBottom()
    if (done) {
      sending.value = false
      abortController = null
      saveHistory()
    }
  }, (errMsg) => {
    // 流式请求失败：标记错误，不保存到历史，提示用户
    const msg = messages.value[aiIndex]
    if (msg) {
      msg.streaming = false
      msg.error = true
      msg.content = t('chat.requestFailed')
      msg.time = formatTime(new Date())
    }
    sending.value = false
    abortController = null
    uni.showToast({ title: errMsg, icon: 'none' })
  })
}

function handleStop() {
  if (abortController) {
    uni.vibrateShort({ type: 'light' })
    abortController.abort()
    abortController = null
    sending.value = false
    const last = messages.value[messages.value.length - 1]
    if (last && last.streaming) {
      last.streaming = false
      last.time = formatTime(new Date())
    }
    saveHistory()
  }
}

function scrollToBottom() {
  nextTick(() => {
    const id = `msg-${messages.value.length - 1}`
    scrollToView.value = id
  })
}

function formatTime(d: Date) {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function navigateBack() {
  uni.navigateBack()
}

// 获取 AI 消息渲染 HTML
function getAiHtml(content: string): string {
  if (!content)
    return ''
  if (hasMarkdown(content))
    return markdownToHtml(content)
  return content.replace(/\n/g, '<br>')
}

// 长按消息菜单
function onLongPressMessage(msg: DisplayMessage, index: number) {
  const actions = [t('chat.copy')]
  if (msg.role === 'assistant' && index > 0)
    actions.push(t('chat.regenerate'))

  uni.showActionSheet({
    itemList: actions,
    success: (res) => {
      if (res.tapIndex === 0) {
        const text = stripMarkdown(msg.content)
        uni.setClipboardData({ data: text, success: () => uni.showToast({ title: t('chat.copied'), icon: 'success' }) })
      }
      else if (res.tapIndex === 1) {
        regenerate(index)
      }
    },
  })
}

// 重新生成
function regenerate(aiIndex: number) {
  if (sending.value)
    return
  const userIndex = aiIndex - 1
  if (userIndex < 0 || messages.value[userIndex]?.role !== 'user')
    return

  messages.value.splice(aiIndex)
  sending.value = true
  messages.value.push({ id: genMsgId(), role: 'assistant', content: '', time: formatTime(new Date()), streaming: true })
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
      if (done)
        msg.time = formatTime(new Date())
    }
    scrollToBottom()
    if (done) {
      sending.value = false
      abortController = null
      saveHistory()
    }
  }, (errMsg) => {
    const msg = messages.value[newAiIndex]
    if (msg) {
      msg.streaming = false
      msg.error = true
      msg.content = t('chat.requestFailed')
      msg.time = formatTime(new Date())
    }
    sending.value = false
    abortController = null
    uni.showToast({ title: errMsg, icon: 'none' })
  })
}

// 复制回复
function copyReply(content: string) {
  const text = stripMarkdown(content)
  uni.setClipboardData({ data: text, success: () => uni.showToast({ title: t('chat.copied'), icon: 'success' }) })
}

// 清空历史
function clearHistory() {
  uni.showModal({
    title: t('chat.clearTitle'),
    content: t('chat.clearConfirm'),
    success: (res) => {
      if (res.confirm) {
            messages.value = [{
              id: genMsgId(),
              role: 'assistant',
              content: t('chat.welcome'),
              time: formatTime(new Date()),
            }]
        saveHistory()
      }
    },
  })
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

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
}

.chat-nav {
  background: var(--bg2);
  border-bottom: 1rpx solid var(--border);
  flex-shrink: 0;

  .nav-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 88rpx;
    padding: 0 24rpx;
  }

  .nav-back {
    width: 80rpx;
    display: flex;
    align-items: center;
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: 600;
    color: var(--text);
  }

  .nav-action {
    width: 80rpx;
    display: flex;
    align-items: center;
    justify-content: flex-end;
  }
}

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
      background: linear-gradient(135deg, var(--accent), var(--accent2));
      border-bottom-right-radius: 4rpx;
    }
    .msg-content {
      color: #fff;
    }
    .msg-time {
      color: rgba(255, 255, 255, 0.6);
    }
  }

  &.assistant {
    justify-content: flex-start;
    .msg-bubble {
      background: var(--surface);
      border: 1rpx solid var(--border);
      backdrop-filter: blur(24rpx);
      box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
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
}

.msg-content {
  font-size: 30rpx;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.msg-time {
  font-size: 20rpx;
  align-self: flex-end;
}

/* rich-text 渲染（暗色主题） */
.msg-rich-text {
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--text);

  :deep(pre) {
    background: #1e1e2e;
    border: 1rpx solid rgba(255, 255, 255, 0.06);
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
    background: rgba(0, 255, 170, 0.15);
    padding: 2rpx 10rpx;
    border-radius: 6rpx;
    color: var(--accent);
    font-size: 26rpx;
  }

  :deep(strong) {
    font-weight: 700;
    color: var(--text);
  }

  :deep(em) {
    font-style: italic;
    color: var(--muted);
  }

  :deep(blockquote) {
    border-left: 4rpx solid var(--accent);
    padding-left: 20rpx;
    margin: 12rpx 0;
    color: var(--muted);
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 32rpx;
    margin: 8rpx 0;
  }

  :deep(li) {
    margin: 4rpx 0;
  }

  :deep(a) {
    color: var(--accent);
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
    color: var(--dim);
  }

  .msg-actions {
    display: flex;
    gap: 16rpx;
  }

  .action-btn {
    font-size: 22rpx;
    color: var(--accent);
    padding: 12rpx 20rpx;
    background: rgba(0, 255, 170, 0.12);
    border-radius: 8rpx;

    &:active {
      opacity: 0.7;
    }
  }
}

/* 错误消息样式 */
.msg-error .msg-bubble {
  border-color: rgba(239, 68, 68, 0.3) !important;
  background: rgba(239, 68, 68, 0.06) !important;
  .msg-rich-text {
    color: #f87171 !important;
  }
}

.typing-cursor {
  position: absolute;
  right: 20rpx;
  bottom: 20rpx;
  width: 4rpx;
  height: 28rpx;
  background: var(--accent);
  animation: blink 1s infinite;
  border-radius: 2rpx;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: var(--bg2);
  border-top: 1rpx solid var(--border);
  flex-shrink: 0;
}

.input-box {
  flex: 1;
  background: var(--bg);
  border-radius: 40rpx;
  padding: 16rpx 28rpx;
}

.input-field {
  font-size: 30rpx;
  color: var(--text);
  height: 48rpx;
  line-height: 48rpx;
}
.input-placeholder {
  color: var(--dim);
}

.send-btn {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 16rpx rgba(0, 255, 170, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    opacity: 0.8;
    box-shadow: 0 0 8rpx rgba(0, 255, 170, 0.2);
  }
  &.disabled {
    opacity: 0.4;
    box-shadow: none;
  }
}

.stop-btn {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #ff4d6d;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    opacity: 0.8;
  }
}

.stop-square {
  width: 24rpx;
  height: 24rpx;
  background: #fff;
  border-radius: 4rpx;
}
</style>
