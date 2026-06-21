import { defineUniPages } from '@uni-helper/vite-plugin-uni-pages'
import { tabBar } from './src/layouts/fg-tabbar/tabbarList'

const usedWotComponents = [
  'config-provider',
  'toast',
  'message-box',
  'icon',
  'tabbar',
  'tabbar-item',
  'navbar',
  'button',
  'action-sheet',
  'loading',
  'swipe-action',
  'fab',
  'popup',
  'input',
  'tag',
  'text',
  'status-tip',
].join('|')

export default defineUniPages({
  globalStyle: {
    navigationStyle: 'default',
    navigationBarTitleText: '小智',
    navigationBarBackgroundColor: '#f8f8f8',
    navigationBarTextStyle: 'black',
    backgroundColor: '#FFFFFF',
  },
  easycom: {
    autoscan: true,
    custom: {
      '^fg-(.*)': '@/components/fg-$1/fg-$1.vue',
      [`^wd-(${usedWotComponents})$`]: 'wot-design-uni/components/wd-$1/wd-$1.vue',
    },
  },
  // tabbar 的配置统一在 "./src/layouts/fg-tabbar/tabbarList.ts" 文件中
  tabBar: tabBar as any,
})
