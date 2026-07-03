/*
 * @Author: 菲鸽
 * @Date: 2024-03-28 19:13:55
 * @Last Modified by: 菲鸽
 * @Last Modified time: 2024-03-28 19:24:55
 */
// __UNI_PLATFORM__ 是 uni-app 编译时注入的全局变量；在测试环境（vitest）或 SSR 中可能不存在，回退到 h5 避免启动崩溃。
const PLATFORM_VALUE = (typeof __UNI_PLATFORM__ !== 'undefined' ? __UNI_PLATFORM__ : 'h5') as string

export const platform = PLATFORM_VALUE
export const isH5 = PLATFORM_VALUE === 'h5'
export const isApp = PLATFORM_VALUE === 'app'
export const isMp = PLATFORM_VALUE.startsWith('mp-')
export const isMpWeixin = PLATFORM_VALUE.startsWith('mp-weixin')
export const isMpAplipay = PLATFORM_VALUE.startsWith('mp-alipay')
export const isMpToutiao = PLATFORM_VALUE.startsWith('mp-toutiao')

const PLATFORM = {
  platform,
  isH5,
  isApp,
  isMp,
  isMpWeixin,
  isMpAplipay,
  isMpToutiao,
}
export default PLATFORM
