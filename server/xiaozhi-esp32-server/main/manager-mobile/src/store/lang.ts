import { ref } from 'vue'
import { defineStore } from 'pinia'
import { setCachedLanguage } from '@/utils/authCache'

// 支持的语言类型
export type Language = 'zh_CN' | 'en' | 'zh_TW' | 'de' | 'vi' | 'pt_BR'

export interface LangStore {
  currentLang: Language
  changeLang: (lang: Language) => void
}

export const useLangStore = defineStore(
  'lang',
  () => {
    // 从本地存储获取语言设置，如果没有则使用默认值
    const savedLang = uni.getStorageSync('app_language') as Language | null
    const currentLang = ref<Language>(savedLang || 'zh_CN')

    // 切换语言
    const changeLang = (lang: Language) => {
      currentLang.value = lang
      setCachedLanguage(lang)
    }

    return {
      currentLang,
      changeLang,
    }
  },
  {
    persist: {
      key: 'lang',
      serializer: {
        serialize: state => JSON.stringify(state.currentLang),
        deserialize: value => ({ currentLang: JSON.parse(value) }),
      },
    },
  },
)
