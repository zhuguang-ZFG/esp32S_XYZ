import { nextTick, ref } from 'vue'
import type { DisplayMessage } from './useChatMessages'

export function genMsgId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function formatTime(d: Date): string {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export function navigateBack() {
  uni.navigateBack()
}

/**
 * 聊天页通用 UI 工具（从 chat.vue 提取）。
 */
export function useChatHelpers(messages: { value: DisplayMessage[] }) {
  const scrollToView = ref('')

  function scrollToBottom() {
    nextTick(() => {
      const id = `msg-${messages.value.length - 1}`
      scrollToView.value = id
    })
  }

  return {
    scrollToView,
    scrollToBottom,
    genMsgId,
    formatTime,
    navigateBack,
  }
}
