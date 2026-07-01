import path from 'node:path'
import process from 'node:process'
import { defineUniPages } from '@uni-helper/vite-plugin-uni-pages'
import { loadEnv } from 'vite'
import { tabBar } from './src/layouts/fg-tabbar/tabbarList'

function getMode() {
  const args = process.argv.slice(2)
  const modeFlagIndex = args.findIndex(arg => arg === '--mode')
  return modeFlagIndex !== -1 ? args[modeFlagIndex + 1] : args[0] === 'build' ? 'production' : 'development'
}

const env = loadEnv(getMode(), path.resolve(process.cwd(), 'env'))
const { VITE_APP_TITLE } = env

export default defineUniPages({
  globalStyle: {
    navigationStyle: 'default',
    navigationBarTitleText: VITE_APP_TITLE || 'LiMa 星云',
    navigationBarBackgroundColor: '#07070f',
    navigationBarTextStyle: 'white',
    backgroundColor: '#07070f',
  },
  easycom: {
    autoscan: true,
    custom: {
      '^fg-(.*)': '@/components/fg-$1/fg-$1.vue',
      '^wd-(.*)': 'wot-design-uni/components/wd-$1/wd-$1.vue',
      '^(?!z-paging-refresh|z-paging-load-more)z-paging(.*)':
        'z-paging/components/z-paging$1/z-paging$1.vue',
    },
  },
  // tabbar 的配置统一在 "./src/layouts/fg-tabbar/tabbarList.ts" 文件中
  tabBar: tabBar as any,
})
