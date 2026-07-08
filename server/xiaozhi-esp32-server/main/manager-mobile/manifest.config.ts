import path from 'node:path'
import process from 'node:process'
import { defineManifestConfig } from '@uni-helper/vite-plugin-uni-manifest'
import { loadEnv } from 'vite'
import { getMode } from './scripts/get-mode'

// 获取环境变量的范例
const env = loadEnv(getMode(), path.resolve(process.cwd(), 'env'))
const {
  VITE_APP_TITLE,
  VITE_UNI_APPID,
  VITE_WX_APPID,
  VITE_APP_PUBLIC_BASE,
  VITE_FALLBACK_LOCALE,
} = env

export default defineManifestConfig({
  'name': VITE_APP_TITLE,
  'appid': VITE_UNI_APPID,
  'description': '',
  'versionName': '3.9.2',
  'versionCode': '392',
  'transformPx': false,
  'locale': VITE_FALLBACK_LOCALE, // 'zh-Hans'
  'h5': {
    router: {
      // base: VITE_APP_PUBLIC_BASE,
    },
  },
  /* 5+App特有相关 */
  'app-plus': {
    usingComponents: true,
    nvueStyleCompiler: 'uni-app',
    compilerVersion: 3,
    splashscreen: {
      alwaysShowBeforeRender: true,
      waiting: true,
      autoclose: true,
      delay: 0,
    },
    modules: {
      Payment: {},
      Push: {},
      Share: {},
      Speech: {},
      VideoPlayer: {},
      Contacts: {},
    },
    distribute: {
      android: {
        permissions: [
          '<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />',
          '<uses-permission android:name="android.permission.MOUNT_UNMOUNT_FILESYSTEMS" />',
          '<uses-permission android:name="android.permission.VIBRATE" />',
          '<uses-permission android:name="android.permission.READ_LOGS" />',
          '<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />',
          '<uses-feature android:name="android.hardware.camera.autofocus" />',
          '<uses-permission android:name="android.permission.WRITE_CONTACTS" />',
          '<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
          '<uses-permission android:name="android.permission.CAMERA" />',
          '<uses-permission android:name="android.permission.RECORD_AUDIO" />',
          '<uses-permission android:name="android.permission.GET_ACCOUNTS" />',
          '<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />',
          '<uses-permission android:name="android.permission.READ_PHONE_STATE" />',
          '<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />',
          '<uses-permission android:name="android.permission.WAKE_LOCK" />',
          '<uses-permission android:name="android.permission.CALL_PHONE" />',
          '<uses-permission android:name="android.permission.FLASHLIGHT" />',
          '<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />',
          '<uses-feature android:name="android.hardware.camera" />',
          '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />',
          '<uses-permission android:name="android.permission.READ_CONTACTS" />',
          '<uses-permission android:name="android.permission.WRITE_SETTINGS" />',
          '<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />',
          '<uses-permission android:name="android.permission.INTERNET" />',
          '<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />',
          '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
        ],
      },
      ios: {},
      sdkConfigs: {},
    },
  },
  /* 小程序特有相关 */
  'mp-weixin': {
    appid: VITE_WX_APPID,
    darkmode: true,
    // __usePrivacyCheck__: true,
    // 微信小程序 App 默认标题，如`
    // navigationBarTitleText: 'uni-app',
    // 分包优化
    optimization: {
      subPackages: true,
    },
    // 对微信小程序的 js 文件开启原生压缩，可能会导致部分 ES6 语法不兼容，需要关闭（例如：audioContext 的 setInterval）
    // minified: true,
    // 微信小程序自定义组件尺寸单位，默认为 rpx
    // component2: true,
    // 开启素描相机
    // runtimeCompiler: true,
  },
  'mp-alipay': {
    usingComponents: true,
  },
  'mp-baidu': {
    usingComponents: true,
  },
  'mp-toutiao': {
    usingComponents: true,
  },
  'mp-qq': {
    usingComponents: true,
  },
  'mp-kuaishou': {
    usingComponents: true,
  },
  'mp-lark': {
    usingComponents: true,
  },
  'mp-jd': {
    usingComponents: true,
  },
  'mp-xhs': {
    usingComponents: true,
  },
  // 百度小程序、抖音小程序需要设置，但用户不能自己修改地址
  'mp-360': {
    usingComponents: true,
  },
  'quickapp-webview': {
    // 快应用特有相关
  },
  // 路由跳转白名单：开发期关闭，生产构建改 true
  // urlCheck: false,
  urlCheck: true,
})
