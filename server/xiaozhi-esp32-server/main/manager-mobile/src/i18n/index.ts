import type { Language } from '@/store/lang'
import { ref } from 'vue'
import { useLangStore } from '@/store/lang'
import zh_CN from './zh_CN'

const loaders: Record<Language, () => Promise<Record<string, string>>> = {
  zh_CN: () => Promise.resolve(zh_CN),
  en: () => import('./en').then(m => m.default),
  zh_TW: () => import('./zh_TW').then(m => m.default),
  de: () => import('./de').then(m => m.default),
  vi: () => import('./vi').then(m => m.default),
  pt_BR: () => import('./pt_BR').then(m => m.default),
}

const messages: Partial<Record<Language, Record<string, string>>> = {
  zh_CN,
}

const currentLang = ref<Language>('zh_CN')
let loadingLang: Language | null = null

async function loadLanguage(lang: Language) {
  if (messages[lang] || loadingLang === lang)
    return
  loadingLang = lang
  try {
    messages[lang] = await loaders[lang]()
  }
  finally {
    loadingLang = null
  }
}

export async function initI18n() {
  const langStore = useLangStore()
  currentLang.value = langStore.currentLang
  if (currentLang.value !== 'zh_CN')
    await loadLanguage(currentLang.value)
}

export async function changeLanguage(lang: Language) {
  await loadLanguage(lang)
  currentLang.value = lang
  const langStore = useLangStore()
  langStore.changeLang(lang)
}

export function t(key: string, params?: Record<string, string | number>): string {
  const langMessages = messages[currentLang.value] || messages.zh_CN

  if (langMessages && typeof langMessages === 'object' && key in langMessages) {
    const value = langMessages[key]
    if (typeof value === 'string') {
      if (params) {
        let result = value
        Object.entries(params).forEach(([paramKey, paramValue]) => {
          const regex = new RegExp(`\\{${paramKey}\\}`, 'g')
          result = result.replace(regex, String(paramValue))
        })
        return result
      }
      return value
    }
  }

  return key
}

export function getCurrentLanguage(): Language {
  return currentLang.value
}

export function getSupportedLanguages(): { code: Language, name: string }[] {
  return [
    { code: 'zh_CN', name: '简体中文' },
    { code: 'en', name: 'English' },
    { code: 'zh_TW', name: '繁體中文' },
    { code: 'de', name: 'Deutsch' },
    { code: 'vi', name: 'Tiếng Việt' },
    { code: 'pt_BR', name: 'Português (Brasil)' },
  ]
}
