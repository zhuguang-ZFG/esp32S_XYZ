import type { Language } from '@/store/lang'
import { ref } from 'vue'
import { useLangStore } from '@/store/lang'

// 导入各个语言的翻译文件（P2-19 裁剪：仅保留 zh_CN + en）
import en from './en'
import zh_CN from './zh_CN'

// 语言包映射
const messages = {
  zh_CN,
  en,
}

// 当前使用的语言
const currentLang = ref<Language>('zh_CN')

// 初始化语言
export function initI18n() {
  const langStore = useLangStore()
  currentLang.value = langStore.currentLang
}

// 切换语言
export function changeLanguage(lang: Language) {
  currentLang.value = lang
  const langStore = useLangStore()
  langStore.changeLang(lang)
}

// 获取翻译文本
export function t(key: string, params?: Record<string, string | number>): string {
  const langMessages = messages[currentLang.value]

  // 直接查找扁平键名
  if (langMessages && typeof langMessages === 'object' && key in langMessages) {
    const value = langMessages[key]
    if (typeof value === 'string') {
      // 处理参数替换
      if (params) {
        let result = value
        Object.entries(params).forEach(([paramKey, paramValue]) => {
          const regex = new RegExp(`\{${paramKey}\}`, 'g')
          result = result.replace(regex, String(paramValue))
        })
        return result
      }
      return value
    }
    return key
  }

  return key // 如果找不到对应的翻译，返回key本身
}

// 获取当前语言
export function getCurrentLanguage(): Language {
  return currentLang.value
}

// 获取支持的语言列表
export function getSupportedLanguages(): { code: Language, name: string }[] {
  return [
    { code: 'zh_CN', name: '简体中文' },
    { code: 'en', name: 'English' },
  ]
}
