import type { ChatMessage } from '@/api/chat/chat'
import { ref } from 'vue'
import { t } from '@/i18n'
import { genMsgId, formatTime } from './useChatHelpers'

export interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string
  time: string
  streaming?: boolean
  error?: boolean
  id: string
}

const CHAT_HISTORY_KEY = 'lima_chat_history'
const MAX_HISTORY = 100

function createWelcomeMessage(): DisplayMessage {
  return {
    id: genMsgId(),
    role: 'assistant',
    content: t('chat.welcome'),
    time: formatTime(new Date()),
  }
}

/**
 * 聊天消息历史与状态（从 chat.vue 提取）。
 */
export function useChatMessages() {
  const messages = ref<DisplayMessage[]>([])

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

  function ensureWelcome() {
    if (!messages.value.length)
      messages.value.push(createWelcomeMessage())
  }

  function clearHistory() {
    messages.value = [createWelcomeMessage()]
    saveHistory()
  }

  function pushUserMessage(text: string) {
    messages.value.push({
      id: genMsgId(),
      role: 'user',
      content: text,
      time: formatTime(new Date()),
    })
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
      .map(m => ({ role: m.role, content: m.content }))
  }

  function toRegenerateMessages(aiIndex: number): ChatMessage[] {
    return messages.value
      .filter((m, i) => i < aiIndex && m.content)
      .map(m => ({ role: m.role, content: m.content }))
  }

  return {
    messages,
    MAX_HISTORY,
    loadHistory,
    saveHistory,
    ensureWelcome,
    clearHistory,
    pushUserMessage,
    pushAssistantPlaceholder,
    toChatMessages,
    toRegenerateMessages,
  }
}
