import type { Language } from '@/store/lang'

const langMap: Record<Language, string> = {
  zh_CN: 'zh-CN',
  en: 'en-US',
  zh_TW: 'zh-TW',
  de: 'de',
  vi: 'vi',
  pt_BR: 'pt-BR',
}

function parseToken(raw: string | null): { token?: string } | null {
  if (!raw)
    return null
  try {
    return JSON.parse(raw) as { token?: string }
  }
  catch {
    return { token: raw }
  }
}

let cachedTokenRaw: string | null = uni.getStorageSync('token') || null
let cachedAuthInfo: { token?: string } | null = parseToken(cachedTokenRaw)
let cachedLanguage: string = langMap[uni.getStorageSync('app_language') as Language || 'zh_CN']

export function getCachedAuthInfo(): { token?: string } | null {
  return cachedAuthInfo
}

export function setCachedToken(raw: string | null) {
  cachedTokenRaw = raw
  cachedAuthInfo = parseToken(raw)
}

export function clearCachedToken() {
  cachedTokenRaw = null
  cachedAuthInfo = null
}

export function getCachedLanguage(): string {
  return cachedLanguage
}

export function setCachedLanguage(lang: Language) {
  cachedLanguage = langMap[lang] || langMap.zh_CN
}
