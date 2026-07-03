import type { uniappRequestAdapter } from '@alova/adapter-uniapp'
import type { IResponse } from './types'
import type { Language } from '@/store/lang'
import AdapterUniapp from '@alova/adapter-uniapp'
import { createAlova } from 'alova'
import { createServerTokenAuthentication } from 'alova/client'
import VueHook from 'alova/vue'
import { v2RefreshToken } from '@/api/v2'
import { API_DEFAULT_TIMEOUT_MS, REFRESH_COOLDOWN_MS } from '@/config/timeouts'
import { getEnvBaseUrl } from '@/utils'
import { toast } from '@/utils/toast'
import { ContentTypeEnum, ResultEnum, ShowMessage } from './enum'

// 语言映射, 用于设置 Accept-language 头（P2-19 裁剪：仅保留 zh_CN + en）
const langMap: Record<Language, string> = {
  zh_CN: 'zh-CN',
  en: 'en-US',
}

/**
 * 最近一次刷新成功的毫秒时间戳。
 * 用于防止无限刷新循环：alova 内置无循环保护，若刷新后重试仍返回 401
 * （账号被禁用/服务端异常），会再次触发刷新 → 死循环。
 * 此处要求两次刷新间隔 ≥ REFRESH_COOLDOWN_MS，否则视为刷新无效，回退登录页。
 */
let lastRefreshAt = 0

/**
 * 创建请求实例
 *
 * 鉴权刷新策略说明（重要）：
 * LiMa 后端在 token 过期/无效时返回 HTTP 401（状态码，非业务 code）。
 * uni-app 请求适配器对任何 HTTP 状态码（含 401）都走 success 回调，
 * 仅网络层失败才 reject。因此 HTTP 401 必然进入 responded.onSuccess 路径，
 * 而非 onError。故必须使用 refreshTokenOnSuccess（在 onSuccess 拦截器内判定），
 * 而非 refreshTokenOnError（仅在适配器 reject 时触发，对 LiMa 的 401 永远不触发）。
 *
 * 刷新流程（alova 内置）：
 * isExpired 返回 true → 调用 handler 静默刷新 → handler 成功（无抛出）后
 * alova 自动重发原始请求并返回重试结果，对业务层完全透明。
 * handler 抛出则刷新失败，回退到登录页。
 */
const { onAuthRequired, onResponseRefreshToken } = createServerTokenAuthentication<
  typeof VueHook,
  typeof uniappRequestAdapter
>({
  refreshTokenOnSuccess: {
    // 响应到达 onSuccess 时判定：HTTP 401 表示服务端拒绝当前 token
    isExpired: (response) => {
      const statusCode = (response as UniNamespace.RequestSuccessCallbackResult)?.statusCode
      return statusCode === ResultEnum.Unauthorized
    },
    // 静默刷新：微信 code → v2Login → 更新本地 token。
    // 成功后 alova 自动重发原始请求；失败则清除 token 回退登录页并抛出（中止重试）。
    // 冷却期内重复触发视为刷新无效（重试仍 401），直接回退登录页以打破潜在死循环。
    handler: async () => {
      const now = Date.now()
      if (now - lastRefreshAt < REFRESH_COOLDOWN_MS) {
        uni.removeStorageSync('token')
        await uni.reLaunch({ url: '/pages/v2/login/index' })
        throw new Error('token refresh ineffective, fallback to login')
      }
      try {
        await v2RefreshToken()
        lastRefreshAt = Date.now()
      }
      catch (error) {
        uni.removeStorageSync('token')
        await uni.reLaunch({ url: '/pages/v2/login/index' })
        throw error
      }
    },
  },
})

/**
 * alova 请求实例
 */
const alovaInstance = createAlova({
  baseURL: getEnvBaseUrl(),
  ...AdapterUniapp(),
  timeout: API_DEFAULT_TIMEOUT_MS,
  statesHook: VueHook,

  beforeRequest: onAuthRequired((method) => {
    // h5动态获取最新的 baseURL，确保使用用户设置的服务器地址
    const currentBaseUrl = getEnvBaseUrl()
    if (currentBaseUrl !== method.baseURL) {
      method.baseURL = currentBaseUrl
    }

    // 检查混合内容错误（HTTPS页面请求HTTP接口）
    const currentProtocol = typeof window !== 'undefined' && window.location.protocol
    const requestProtocol = method.baseURL?.split(':')[0]
    const currentLang = langMap[uni.getStorageSync('app_language') as Language || 'zh_CN']
    if (currentProtocol === 'https:' && requestProtocol === 'http') {
      const errorMessage = '无法配置http协议地址,请检查接口地址'
      throw new Error(errorMessage)
    }

    // 设置默认 Content-Type
    method.config.headers = {
      'Content-Type': ContentTypeEnum.JSON,
      'Accept': 'application/json, text/plain, */*',
      'Accept-language': currentLang,
      ...method.config.headers,
    }

    const { config } = method
    const ignoreAuth = config.meta?.ignoreAuth

    // 处理认证信息
    if (!ignoreAuth) {
      const rawToken = uni.getStorageSync('token') || ''
      let authInfo: { token?: string } = {}
      try {
        authInfo = JSON.parse(rawToken || '{}')
      }
      catch {
        authInfo = { token: rawToken }
      }
      if (!authInfo.token) {
        // 跳转到登录页
        uni.reLaunch({ url: '/pages/v2/login/index' })
        throw new Error('[请求错误]：未登录')
      }
      // 添加 Authorization 头
      method.config.headers.Authorization = `Bearer ${authInfo.token}`
    }

    // 处理动态域名
    if (config.meta?.domain) {
      method.baseURL = config.meta.domain
    }
  }),

  responded: onResponseRefreshToken((response, method) => {
    const { config } = method
    const { requestType } = config
    const {
      statusCode,
      data: rawData,
      errMsg,
    } = response as UniNamespace.RequestSuccessCallbackResult

    // 处理特殊请求类型（上传/下载）
    if (requestType === 'upload' || requestType === 'download') {
      return response
    }

    // 处理 HTTP 状态码错误
    if (statusCode !== 200) {
      const errorMessage = ShowMessage(statusCode) || `HTTP请求错误[${statusCode}]`
      console.error('errorMessage===>', errorMessage)
      toast.error(errorMessage)
      throw new Error(`${errorMessage}：${errMsg}`)
    }

    // 处理业务逻辑错误
    const { code, msg, message: messageText, data } = rawData as IResponse
    // LiMa 后端错误 envelope 使用 `message` 字段，兼容 `msg`
    const errorMsg = msg || messageText || ''
    if (code === undefined) {
      return rawData
    }
    if (code !== ResultEnum.Success) {
      // 检查是否为token失效
      if (code === ResultEnum.Unauthorized) {
        // 清除token并跳转到登录页
        uni.removeStorageSync('token')
        uni.reLaunch({ url: '/pages/v2/login/index' })
        throw new Error(`请求错误[${code}]：${errorMsg}`)
      }

      if (config.meta?.isExposeError) {
        return Promise.reject(new Error(errorMsg))
      }

      if (config.meta?.toast !== false) {
        toast.warning(errorMsg)
      }
      throw new Error(`请求错误[${code}]：${errorMsg}`)
    }
    // 处理成功响应，返回业务数据
    return data
  }),
})

export const http = alovaInstance
