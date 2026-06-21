import { createSSRApp } from 'vue'
import App from './App.vue'
// 导入国际化相关功能
import { initI18n } from './i18n'

import { routeInterceptor } from './router/interceptor'
import store from './store'
import '@/style/index.scss'

import 'virtual:uno.css'

export async function createApp() {
  const app = createSSRApp(App)
  app.use(store)
  app.use(routeInterceptor)

  // 初始化国际化（等待非默认语言包加载完成，避免启动时闪现 fallback 文案）
  await initI18n()

  return {
    app,
  }
}
