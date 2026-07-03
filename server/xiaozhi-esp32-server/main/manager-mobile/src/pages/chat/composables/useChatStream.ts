import { chatCompletionStream } from '@/api/chat/chat'
import type { ChatMessage } from '@/api/chat/chat'
import { t } from '@/i18n'
import { hasMarkdown, markdownToHtml, stripMarkdown } from '@/utils/markdown'
import { ref } from 'vue'
import { genMsgId, formatTime } from './useChatHelpers'
import type { DisplayMessage } from './useChatMessages'

/**
 * 聊天流式调用与重新生成（从 chat.vue 提取）。
 */
export function useChatStream(
  messages: { value: DisplayMessage[] },
  inputText: { value: string },
  sending: { value: boolean },
  saveHistory: () => void,
  scrollToBottom: () => void,
) {
  let abortController: { abort: () => void } | null = null

  function appendChunk(aiIndex: number, chunk: string, done: boolean) {
    const msg = messages.value[aiIndex]
    if (msg) {
      msg.content += chunk
      msg.streaming = !done
      if (done)
        msg.time = formatTime(new Date())
    }
    scrollToBottom()
  }

  function markStreamError(aiIndex: number, errMsg: string) {
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
  }

  function finishStream(aiIndex: number) {
    sending.value = false
    abortController = null
    saveHistory()
  }

  function pushAssistantPlaceholder(): number {
    messages.value.push({
      id: genMsgId(),
      role: 'assistant',
      content: '',
      time: formatTime(new Date()),
      streaming: true,
    })
    return messages.value.length - 1
  }

  function toChatMessages(): ChatMessage[] {
    return messages.value
      .filter(m => !m.streaming && m.content)
      .map(m => ({ role: m.role, content: m.content })) as ChatMessage[]
  }

  function toRegenerateMessages(aiIndex: number): ChatMessage[] {
    return messages.value
      .filter((m, i) => i < aiIndex && m.content)
      .map(m => ({ role: m.role, content: m.content })) as ChatMessage[]
  }

  function startStream(chatMessages: ChatMessage[], aiIndex: number) {
    sending.value = true
    abortController = chatCompletionStream(
      chatMessages,
      (chunk, done) => {
        appendChunk(aiIndex, chunk, done)
        if (done)
          finishStream(aiIndex)
      },
      (errMsg) => markStreamError(aiIndex, errMsg),
    )
  }

  function handleSend() {
    const text = inputText.value.trim()
    if (!text || sending.value)
      return

    messages.value.push({ id: genMsgId(), role: 'user', content: text, time: formatTime(new Date()) })
    inputText.value = ''
    scrollToBottom()
    saveHistory()

    const aiIndex = pushAssistantPlaceholder()
    startStream(toChatMessages(), aiIndex)
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

  function regenerate(aiIndex: number) {
    if (sending.value)
      return
    const userIndex = aiIndex - 1
    if (userIndex < 0 || messages.value[userIndex]?.role !== 'user')
      return

    messages.value.splice(aiIndex)
    const newAiIndex = pushAssistantPlaceholder()
    scrollToBottom()

    startStream(toRegenerateMessages(aiIndex), newAiIndex)
  }

  return {
    handleSend,
    handleStop,
    regenerate,
  }
}

export function getAiHtml(content: string): string {
  if (!content)
    return ''
  if (hasMarkdown(content))
    return markdownToHtml(content)
  return content.replace(/\n/g, '<br>')
}

export function copyReply(content: string) {
  const text = stripMarkdown(content)
  uni.setClipboardData({ data: text, success: () => uni.showToast({ title: t('chat.copied'), icon: 'success' }) })
}

export function onLongPressMessage(
  msg: DisplayMessage,
  index: number,
  regenerate: (aiIndex: number) => void,
) {
  const actions = [t('chat.copy')]
  if (msg.role === 'assistant' && index > 0)
    actions.push(t('chat.regenerate'))

  uni.showActionSheet({
    itemList: actions,
    success: (res) => {
      if (res.tapIndex === 0) {
        copyReply(msg.content)
      }
      else if (res.tapIndex === 1) {
        regenerate(index)
      }
    },
  })
}
