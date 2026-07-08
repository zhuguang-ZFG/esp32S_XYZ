import pagesJson from '@/pages.json'

import { isMpWeixin } from './platform'

const pages = pagesJson.pages
type SubPackage = { root: string; pages: { path: string }[] }
const safeSubPackages = (pagesJson as { subPackages?: SubPackage[] }).subPackages || []

/**
 * 运行时服务端地址覆盖存储键
 */
export const SERVER_BASE_URL_OVERRIDE_KEY = 'server_base_url_override'

/**
 * 设置/清除/获取 运行时覆盖的服务端地址
 */
export function setServerBaseUrlOverride(url: string) {
  uni.setStorageSync(SERVER_BASE_URL_OVERRIDE_KEY, url)
}

export function isValidServerBaseUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    return (parsed.protocol === 'http:' || parsed.protocol === 'https:') && Boolean(parsed.host)
  }
  catch {
    return false
  }
}

export function clearServerBaseUrlOverride() {
  uni.removeStorageSync(SERVER_BASE_URL_OVERRIDE_KEY)
}

export function getServerBaseUrlOverride(): string | null {
  const value = uni.getStorageSync(SERVER_BASE_URL_OVERRIDE_KEY)
  return value || null
}

/**
 * 从本地存储读取 Bearer token 字符串。
 *
 * token 存储格式为 `JSON.stringify({ token, expireAt })`，
 * 早期或异常情况下也可能是裸字符串。此函数统一解析为 token 值，
 * 解析失败（非 JSON）时回退为原始字符串，保证向后兼容。
 * @returns token 字符串；存储为空时返回 null
 */
export function getBearerToken(): string | null {
  const rawToken = uni.getStorageSync('token') || ''
  if (!rawToken)
    return null
  try {
    const parsed = JSON.parse(rawToken)
    return parsed.token || rawToken
  }
  catch {
    return rawToken
  }
}

export function getLastPage() {
  // getCurrentPages() 至少有1个元素，所以不再额外判断
  // const lastPage = getCurrentPages().at(-1)
  // 上面那个在低版本安卓中打包会报错，所以改用下面这个【虽然我加了 src/interceptions/prototype.ts，但依然报错】
  const pages = getCurrentPages()
  return pages[pages.length - 1]
}

/**
 * 获取当前页面路由的 path 路径和 redirectPath 路径
 * path 如 '/pages/login/index'
 * redirectPath 如 '/pages/demo/base/route-interceptor'
 */
export function currRoute() {
  const lastPage = getLastPage()
  const currRoute = (lastPage as unknown as { $page?: { fullPath: string } }).$page
  // 经过多端测试，只有 fullPath 靠谱，其他都不靠谱
  const { fullPath } = currRoute as { fullPath: string }
  // eg: /pages/login/index?redirect=%2Fpages%2Fdemo%2Fbase%2Froute-interceptor (小程序)
  // eg: /pages/login/index?redirect=%2Fpages%2Froute-interceptor%2Findex%3Fname%3Dfeige%26age%3D30(h5)
  return getUrlObj(fullPath)
}

function ensureDecodeURIComponent(url: string) {
  if (url.startsWith('%')) {
    return ensureDecodeURIComponent(decodeURIComponent(url))
  }
  return url
}
/**
 * 解析 url 得到 path 和 query
 * 比如输入url: /pages/login/index?redirect=%2Fpages%2Fdemo%2Fbase%2Froute-interceptor
 * 输出: {path: /pages/login/index, query: {redirect: /pages/demo/base/route-interceptor}}
 */
export function getUrlObj(url: string) {
  const [path, queryStr] = url.split('?')
  // console.log(path, queryStr)

  if (!queryStr) {
    return {
      path,
      query: {},
    }
  }
  const query: Record<string, string> = {}
  queryStr.split('&').forEach((item) => {
    const [key, value] = item.split('=')
    // console.log(key, value)
    query[key] = ensureDecodeURIComponent(value) // 这里需要统一 decodeURIComponent 一下，可以兼容h5和微信y
  })
  return { path, query }
}
/**
 * 得到所有的需要登录的 pages，包括主包和分包的
 * 这里设计得通用一点，可以传递 key 作为判断依据，默认是 needLogin, 与 route-block 配对使用
 * 如果没有传 key，则表示所有的 pages，如果传递了 key, 则表示通过 key 过滤
 */
export function getAllPages(key = 'needLogin') {
  // 这里处理主包
  const mainPages = pages
    .filter(page => !key || page[key])
    .map(page => ({
      ...page,
      path: `/${page.path}`,
    }))

  // 这里处理分包
  const subPages: { path: string }[] = []
  safeSubPackages.forEach((subPageObj) => {
    // console.log(subPageObj)
    const { root } = subPageObj

    subPageObj.pages
      .filter(page => !key || page[key])
      .forEach((page) => {
        subPages.push({
          ...page,
          path: `/${root}/${page.path}`,
        })
      })
  })
  const result = [...mainPages, ...subPages]
  // console.log(`getAllPages by ${key} result: `, result)
  return result
}

/**
 * 得到所有的需要登录的 pages，包括主包和分包的
 * 只得到 path 数组
 */
export const getNeedLoginPages = (): string[] => getAllPages('needLogin').map(page => page.path)

/**
 * 得到所有的需要登录的 pages，包括主包和分包的
 * 只得到 path 数组
 */
export const needLoginPages: string[] = getAllPages('needLogin').map(page => page.path)

/**
 * 根据微信小程序当前环境，判断应该获取的 baseUrl
 */
export function getEnvBaseUrl() {
  // 若存在用户设置的覆盖地址，优先返回
  const override = getServerBaseUrlOverride()
  if (override)
    return override

  // 请求基准地址（默认来源于 env）
  let baseUrl = import.meta.env.VITE_SERVER_BASEURL

  // # 有些同学可能需要在微信小程序里面根据 develop、trial、release 分别设置上传地址，参考代码如下。
  // Migrated to LiMa: WeChat mini-program now talks to chat.donglicao.com by default.
  const VITE_SERVER_BASEURL__WEIXIN_DEVELOP = 'https://chat.donglicao.com'
  const VITE_SERVER_BASEURL__WEIXIN_TRIAL = 'https://chat.donglicao.com'
  const VITE_SERVER_BASEURL__WEIXIN_RELEASE = 'https://chat.donglicao.com'

  // 微信小程序端环境区分
  if (isMpWeixin) {
    const {
      miniProgram: { envVersion },
    } = uni.getAccountInfoSync()

    switch (envVersion) {
      case 'develop':
        baseUrl = VITE_SERVER_BASEURL__WEIXIN_DEVELOP || baseUrl
        break
      case 'trial':
        baseUrl = VITE_SERVER_BASEURL__WEIXIN_TRIAL || baseUrl
        break
      case 'release':
        baseUrl = VITE_SERVER_BASEURL__WEIXIN_RELEASE || baseUrl
        break
    }
  }

  return baseUrl
}

/**
 * 构建单设备实时状态 WebSocket URL（M2 协议，服务端已实现）。
 * 路径：/device/v1/app/devices/{deviceId}/ws
 * 鉴权：URL query param ?authorization=Bearer xxx
 */
export function buildDeviceStatusWsUrl(deviceId: string, token: string): string {
  const base = getEnvBaseUrl().replace(/\/$/, '')
  const proto = base.startsWith('https') ? 'wss' : 'ws'
  const rest = base.replace(/^https?:\/\//, '')
  return `${proto}://${rest}/device/v1/app/devices/${deviceId}/ws?authorization=Bearer ${encodeURIComponent(token)}`
}

/**
 * 根据微信小程序当前环境，判断应该获取的 UPLOAD_BASEURL
 */
const M6_PENDING_TABBAR_INDEX = 0
const M6_PENDING_TRANSFER_BADGE_KEY = 'm6_pending_transfer_count'
const M6_PENDING_VOICE_APPROVAL_BADGE_KEY = 'm6_pending_voice_approval_count'

type M6PendingBadgeKind = 'transfer' | 'voiceApproval'

export function updateM6PendingTabBarBadge(kind: M6PendingBadgeKind, count: number) {
  const key = kind === 'transfer' ? M6_PENDING_TRANSFER_BADGE_KEY : M6_PENDING_VOICE_APPROVAL_BADGE_KEY
  uni.setStorageSync(key, String(Math.max(0, Number(count) || 0)))
  applyM6PendingTabBarBadge()
}

export function applyM6PendingTabBarBadge() {
  const total = readM6PendingBadgeCount(M6_PENDING_TRANSFER_BADGE_KEY)
    + readM6PendingBadgeCount(M6_PENDING_VOICE_APPROVAL_BADGE_KEY)
  if (total > 0) {
    uni.setTabBarBadge({
      index: M6_PENDING_TABBAR_INDEX,
      text: total > 99 ? '99+' : String(total),
      fail() {},
    })
    return
  }
  uni.removeTabBarBadge({
    index: M6_PENDING_TABBAR_INDEX,
    fail() {},
  })
}

function readM6PendingBadgeCount(key: string) {
  const value = Number(uni.getStorageSync(key) || 0)
  return Number.isFinite(value) && value > 0 ? value : 0
}

export function getEnvBaseUploadUrl() {
  // 请求基准地址
  let baseUploadUrl = import.meta.env.VITE_UPLOAD_BASEURL

  // Migrated to LiMa: upload endpoint now points to chat.donglicao.com.
  const VITE_UPLOAD_BASEURL__WEIXIN_DEVELOP = 'https://chat.donglicao.com/upload'
  const VITE_UPLOAD_BASEURL__WEIXIN_TRIAL = 'https://chat.donglicao.com/upload'
  const VITE_UPLOAD_BASEURL__WEIXIN_RELEASE = 'https://chat.donglicao.com/upload'

  // 微信小程序端环境区分
  if (isMpWeixin) {
    const {
      miniProgram: { envVersion },
    } = uni.getAccountInfoSync()

    switch (envVersion) {
      case 'develop':
        baseUploadUrl = VITE_UPLOAD_BASEURL__WEIXIN_DEVELOP || baseUploadUrl
        break
      case 'trial':
        baseUploadUrl = VITE_UPLOAD_BASEURL__WEIXIN_TRIAL || baseUploadUrl
        break
      case 'release':
        baseUploadUrl = VITE_UPLOAD_BASEURL__WEIXIN_RELEASE || baseUploadUrl
        break
    }
  }

  return baseUploadUrl
}
