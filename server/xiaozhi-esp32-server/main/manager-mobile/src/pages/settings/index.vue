<route lang="jsonc" type="page">
{
  "layout": "default",
  "style": {
    "navigationBarTitleText": "设置",
    "navigationStyle": "custom"
  }
}
</route>

<script lang="ts" setup>
import type { V2NotificationSubscription } from '@/api/v2'
import type { Language } from '@/store/lang'
import { computed, onMounted, reactive, ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { v2DeleteAccount, v2ListNotificationSubscriptions, v2SubscribeNotifications, v2UnsubscribeNotification } from '@/api/v2'
import { changeLanguage, getCurrentLanguage, getSupportedLanguages, t } from '@/i18n'
import { useConfigStore } from '@/store'
import {
  clearServerBaseUrlOverride,
  getEnvBaseUrl,
  getServerBaseUrlOverride,
  isValidServerBaseUrl,
  setServerBaseUrlOverride,
} from '@/utils'
import { isMp } from '@/utils/platform'

defineOptions({
  name: 'SettingsPage',
})

const toast = useToast()

// 缓存信息
const cacheInfo = reactive({
  storageSize: '0MB',
  imageCache: '0MB',
  dataCache: '0MB',
})

const configStore = useConfigStore()

// 服务端地址设置
const baseUrlInput = ref('')
const urlError = ref('')
const accountDeleteLoading = ref(false)

// 系统信息（保留）
const systemInfo = computed(() => {
  const info = uni.getSystemInfoSync()
  return `${info.platform} ${info.system}`
})

// 读取本地覆盖地址
function loadServerBaseUrl() {
  const override = getServerBaseUrlOverride()
  baseUrlInput.value = override || getEnvBaseUrl()
}

// 获取缓存信息
function getCacheInfo() {
  try {
    const info = uni.getStorageInfoSync()
    const totalSize = (info.currentSize || 0) / 1024 // KB to MB
    cacheInfo.storageSize = `${totalSize.toFixed(2)}MB`
  }
  catch (error) {
    console.error('获取缓存信息失败:', error)
  }
}

// 验证URL格式
function validateUrl() {
  urlError.value = ''

  if (!baseUrlInput.value) {
    return
  }

  if (!isValidServerBaseUrl(baseUrlInput.value)) {
    urlError.value = t('settings.validServerUrl')
  }
}

// 测试服务端地址
async function testServerBaseUrl() {
  // 先清除错误信息
  urlError.value = ''

  if (!baseUrlInput.value || !isValidServerBaseUrl(baseUrlInput.value)) {
    return false
  }

  try {
    const response = await uni.request({
      url: `${baseUrlInput.value.replace(/\/$/, '')}/health`,
      method: 'GET',
      timeout: 3000,
    })

    if (response.statusCode === 200) {
      return true
    }
    else {
      toast.error({
        msg: t('message.invalidAddress'),
        duration: 3000,
      })
      return false
    }
  }
  catch (error) {
    console.error('测试服务端地址失败:', error)
    toast.error({
      msg: t('message.invalidAddress'),
      duration: 3000,
    })
    return false
  }
}

// 保存服务端地址
async function saveServerBaseUrl() {
  if (!baseUrlInput.value || !isValidServerBaseUrl(baseUrlInput.value)) {
    toast.warning(t('settings.validServerUrl'))
    return
  }

  // 测试地址有效性
  const isServerValid = await testServerBaseUrl()
  if (!isServerValid) {
    return
  }
  setServerBaseUrlOverride(baseUrlInput.value)

  // 切换请求地址后清空所有缓存
  clearAllCacheAfterUrlChange()

  uni.showModal({
    title: t('settings.restartApp'),
    content: t('settings.serverUrlSavedAndCacheCleared'),
    confirmText: t('settings.restartNow'),
    cancelText: t('settings.restartLater'),
    success: (res) => {
      if (res.confirm) {
        restartApp()
      }
      else {
        toast.success(t('settings.restartSuccess'))
      }
    },
  })
}

// 语言切换
const supportedLanguages = getSupportedLanguages()
const currentLanguage = ref<Language>(getCurrentLanguage())
const showLanguageSheet = ref(false)

function handleLanguageChange(lang: Language) {
  changeLanguage(lang)
  showLanguageSheet.value = false
  currentLanguage.value = lang
  toast.success(t('settings.languageChanged'))
}

// 重置为 env 默认
function resetServerBaseUrl() {
  clearServerBaseUrlOverride()
  baseUrlInput.value = getEnvBaseUrl()

  // 切换请求地址后清空所有缓存
  clearAllCacheAfterUrlChange()

  uni.showModal({
    title: t('settings.restartApp'),
    content: t('settings.resetToDefaultAndCacheCleared'),
    confirmText: t('settings.restartNow'),
    cancelText: t('settings.restartLater'),
    success: (res) => {
      if (res.confirm) {
        restartApp()
      }
      else {
        toast.success(t('settings.resetSuccess'))
      }
    },
  })
}

// 重启应用（App 原生重启；其他端回到首页）
function restartApp() {
  // #ifdef APP-PLUS
  plus.runtime.restart()
  // #endif
  // #ifndef APP-PLUS
  uni.reLaunch({ url: '/pages/v2/device-list/index' })
  // #endif
}

// 切换地址后自动清空所有缓存
function clearAllCacheAfterUrlChange() {
  try {
    // 备份运行时覆盖地址，确保清理后恢复
    const preservedOverride = getServerBaseUrlOverride()

    // 完全清空所有缓存，包括token
    uni.clearStorageSync()

    // 清空localStorage（H5环境）
    // #ifdef H5
    if (typeof localStorage !== 'undefined') {
      localStorage.clear()
    }
    // #endif

    // 恢复运行时覆盖地址（如有），需要在清理完成后再写入
    if (preservedOverride) {
      setServerBaseUrlOverride(preservedOverride)
    }

    // 重新获取缓存信息
    getCacheInfo()
  }
  catch (error) {
    console.error('清除缓存失败:', error)
  }
}

// 清除缓存
async function clearCache() {
  try {
    uni.showModal({
      title: t('settings.confirmClear'),
      content: t('settings.confirmClearMessage'),
      confirmText: t('common.confirm'),
      cancelText: t('common.cancel'),
      success: (res) => {
        if (res.confirm) {
          clearAllCacheAfterUrlChange()
          toast.success(t('settings.cacheCleared'))

          // 延迟跳转到登录页
          setTimeout(() => {
            uni.reLaunch({ url: '/pages/v2/login/index' })
          }, 1500)
        }
      },
    })
  }
  catch (error) {
    console.error('清除缓存失败:', error)
    toast.error(t('settings.clearCacheFailed'))
  }
}

// 关于我们
function showAbout() {
  uni.showModal({
    title: t('settings.aboutApp', { appName: import.meta.env.VITE_APP_TITLE }),
    content: t('settings.aboutContent', {
      appName: import.meta.env.VITE_APP_TITLE,
      version: '0.9.3',
    }),
    showCancel: false,
    confirmText: t('common.confirm'),
  })
}

function openPrivacyPermissions() {
  uni.navigateTo({ url: '/pages/settings/privacy-permissions' })
}

function handleAccountDeletion() {
  if (accountDeleteLoading.value) {
    return
  }
  uni.showModal({
    title: t('settings.deleteConfirmTitle'),
    content: t('settings.deleteConfirmContent'),
    confirmText: t('settings.deleteConfirmContinue'),
    cancelText: t('common.cancel'),
    success: (first) => {
      if (!first.confirm) {
        return
      }
      uni.showModal({
        title: t('settings.deleteSecondConfirmTitle'),
        content: t('settings.deleteSecondConfirmContent'),
        confirmText: t('settings.deleteSecondConfirmAction'),
        cancelText: t('common.cancel'),
        success: async (second) => {
          if (!second.confirm) {
            return
          }
          await submitAccountDeletion()
        },
      })
    },
  })
}

async function submitAccountDeletion() {
  accountDeleteLoading.value = true
  try {
    const response = await v2DeleteAccount()
    clearAllCacheAfterUrlChange()
    toast.success(t('settings.accountDeleted', { days: response.auditRetentionDays }))
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/v2/login/index' })
    }, 800)
  }
  catch (error) {
    console.error('delete account failed:', error)
    toast.error(t('settings.deleteFailed'))
  }
  finally {
    accountDeleteLoading.value = false
  }
}

// 通知订阅
const notificationSubs = ref<V2NotificationSubscription[]>([])
const notificationLoading = ref(false)
const notificationEnabled = ref(false)

// 微信订阅消息模板（需在公众平台创建后填入真实 template_id）
const NOTIFICATION_TEMPLATES = [
  'task_completed',
  'task_failed',
  'device_offline',
  'firmware_update',
]

async function loadNotificationSubs() {
  try {
    notificationSubs.value = await v2ListNotificationSubscriptions()
    notificationEnabled.value = notificationSubs.value.some(s => s.status === 'active')
  }
  catch {
    notificationSubs.value = []
  }
}

async function handleToggleNotifications() {
  if (notificationLoading.value)
    return
  notificationLoading.value = true
  try {
    if (notificationEnabled.value) {
      // 关闭：取消所有活跃订阅
      for (const sub of notificationSubs.value) {
        if (sub.status === 'active') {
          await v2UnsubscribeNotification(sub.subscriptionId)
        }
      }
      notificationEnabled.value = false
      toast.success(t('settings.notificationsOff'))
    }
    else {
      // 开启：请求微信订阅授权 → 后端订阅
      // #ifdef MP-WEIXIN
      const reqTemplateIds = NOTIFICATION_TEMPLATES.map(tid => `tmpl_${tid}`)
      const wxRes = await uni.requestSubscribeMessage({ tmplIds: reqTemplateIds })
      const accepted = reqTemplateIds.filter(tid => (wxRes as any)[tid] === 'accept')
      if (!accepted.length) {
        toast.warning(t('settings.notificationsRejected'))
        return
      }
      const openid = uni.getStorageSync('openid') || ''
      if (!openid) {
        toast.warning(t('settings.notificationsNeedLogin'))
        return
      }
      // 获取所有设备 ID
      const devices = uni.getStorageSync('device_ids') || []
      if (!devices.length) {
        toast.warning(t('settings.notificationsNoDevices'))
        return
      }
      await v2SubscribeNotifications(openid, accepted, devices)
      notificationEnabled.value = true
      toast.success(t('settings.notificationsOn'))
      // #endif
      // #ifndef MP-WEIXIN
      toast.info(t('settings.notificationsMpOnly'))
      // #endif
      await loadNotificationSubs()
    }
  }
  catch (e: any) {
    console.error('toggle notifications failed:', e)
    toast.error(e?.message || t('settings.notificationsFailed'))
  }
  finally {
    notificationLoading.value = false
  }
}

onMounted(async () => {
  // 仅在非小程序环境加载服务端地址设置
  if (!isMp) {
    loadServerBaseUrl()
  }
  getCacheInfo()

  // 加载通知订阅状态
  loadNotificationSubs()

  // 动态设置导航栏标题为国际化文本
  uni.setNavigationBarTitle({
    title: t('settings.title'),
  })
})
</script>

<template>
  <view class="page-enter min-h-screen" style="background: #07070f;">
    <wd-navbar
      :title="t('settings.title')" safe-area-inset-top placeholder fixed
      custom-class="!bg-[#07070f]"
      title-class="!text-[#f0f4f8]"
    />

    <view class="p-[24rpx]">
      <!-- 网络设置 - 仅在非小程序环境显示 -->
      <view v-if="!isMp" class="mb-[32rpx]">
        <view class="mb-[24rpx] flex items-center">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            {{ t('settings.networkSettings') }}
          </text>
        </view>

        <view
          class="overflow-hidden border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
          style="background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
        >
          <view class="mb-[24rpx]">
            <text class="text-[28rpx] text-[#f0f4f8] font-semibold">
              {{ t('settings.serverApiUrl') }}
            </text>
            <text class="mt-[8rpx] block text-[24rpx] text-[#5a6372]">
              {{ t('settings.modifyWillClearCache') }}
            </text>
          </view>

          <view class="mb-[24rpx]">
            <view class="w-full overflow-hidden border border-[rgba(255,255,255,0.04)] rounded-[16rpx]" style="background: #0a0a14;">
              <wd-input
                v-model="baseUrlInput" type="text" clearable :maxlength="200"
                :placeholder="t('settings.enterServerUrl')"
                custom-class="!border-none !bg-transparent h-[64rpx] px-[24rpx] items-center"
                input-class="text-[28rpx] text-[#f0f4f8]" @input="validateUrl" @blur="validateUrl"
              />
            </view>
            <text v-if="urlError" class="mt-[8rpx] block text-[24rpx] text-[#ff4d4f]">
              {{ urlError }}
            </text>
          </view>

          <view class="flex gap-[16rpx]">
            <wd-button
              type="primary"
              custom-class="flex-1 h-[88rpx] rounded-[20rpx] text-[28rpx] font-semibold border-none"
              @click="saveServerBaseUrl"
            >
              {{ t('settings.saveSettings') }}
            </wd-button>
            <wd-button
              type="default"
              custom-class="flex-1 h-[88rpx] rounded-[20rpx] text-[28rpx] font-semibold border-[rgba(255,255,255,0.04)] text-[#8b95a8]"
              @click="resetServerBaseUrl"
            >
              {{ t('settings.resetDefault') }}
            </wd-button>
          </view>
        </view>
      </view>

      <!-- 缓存管理 -->
      <view class="mb-[32rpx]">
        <view class="mb-[24rpx] flex items-center">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            {{ t('settings.cacheManagement') }}
          </text>
        </view>

        <view
          class="border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
          style="background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
        >
          <view class="space-y-[16rpx]">
            <!-- 缓存信息展示，参考插件样式 -->
            <view
              class="flex items-center justify-between border border-[rgba(255,255,255,0.04)] rounded-[16rpx] p-[24rpx]"
              style="background: #0a0a14;"
            >
              <view>
                <text class="text-[28rpx] text-[#f0f4f8] font-medium">
                  {{ t('settings.totalCacheSize') }}
                </text>
                <text class="mt-[4rpx] block text-[24rpx] text-[#5a6372]">
                  {{ t('settings.appDataSize') }}
                </text>
              </view>
              <text class="text-[28rpx] text-[#8b95a8] font-semibold">
                {{ cacheInfo.storageSize }}
              </text>
            </view>

            <!-- 清除缓存按钮，参考插件编辑按钮样式 -->
            <view
              class="flex items-center justify-between border border-[rgba(255,255,255,0.04)] rounded-[16rpx] p-[24rpx]"
              style="background: #0a0a14;"
            >
              <view>
                <text class="text-[28rpx] text-[#f0f4f8] font-medium">
                  {{ t('settings.cacheClear') }}
                </text>
                <text class="mt-[4rpx] block text-[24rpx] text-[#5a6372]">
                  {{ t('settings.clearAllCache') }}
                </text>
              </view>
              <view
                class="cursor-pointer rounded-[24rpx] px-[28rpx] py-[16rpx] text-[24rpx] text-[#ff6b6b] font-semibold transition-all duration-300 active:scale-95"
                style="background: rgba(255,107,107,0.1);"
                @click="clearCache"
              >
                {{ t('settings.clearCache') }}
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 隐私与权限 -->
      <view class="mb-[32rpx]">
        <view class="mb-[24rpx] flex items-center">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            {{ t('settings.privacyTitle') }}
          </text>
        </view>

        <view
          class="border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
          style="background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
        >
          <view
            class="flex cursor-pointer items-center justify-between border border-[rgba(255,255,255,0.04)] rounded-[16rpx] p-[24rpx]"
            style="background: #0a0a14;"
            @click="openPrivacyPermissions"
          >
            <view>
              <text class="text-[28rpx] text-[#f0f4f8] font-medium">
                {{ t('settings.privacyAuth') }}
              </text>
              <text class="mt-[4rpx] block text-[24rpx] text-[#5a6372]">
                {{ t('settings.privacyDesc') }}
              </text>
            </view>
            <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[#5a6372]" />
          </view>
        </view>
      </view>

      <!-- 通知订阅 -->
      <view class="mb-[32rpx]">
        <view class="mb-[24rpx] flex items-center">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            {{ t('settings.notificationsTitle') }}
          </text>
        </view>

        <view
          class="border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
          style="background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
        >
          <view
            class="flex items-center justify-between border border-[rgba(255,255,255,0.04)] rounded-[16rpx] p-[24rpx]"
            style="background: #0a0a14;"
          >
            <view>
              <text class="text-[28rpx] text-[#f0f4f8] font-medium">
                {{ t('settings.pushNotifications') }}
              </text>
              <text class="mt-[4rpx] block text-[24rpx] text-[#5a6372]">
                {{ t('settings.pushNotificationsDesc') }}
              </text>
            </view>
            <wd-switch
              v-model="notificationEnabled"
              :loading="notificationLoading"
              @change="handleToggleNotifications"
            />
          </view>
        </view>
      </view>

      <!-- 应用信息 -->
      <view class="mb-[32rpx]">
        <view class="mb-[24rpx] flex items-center">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            {{ t('settings.accountDeletion') }}
          </text>
        </view>

        <view
          class="border border-[rgba(255,107,107,0.15)] rounded-[24rpx] p-[32rpx]"
          style="background: rgba(255,107,107,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
        >
          <view class="flex items-center justify-between gap-[24rpx]">
            <view class="min-w-0 flex-1">
              <text class="text-[28rpx] text-[#f0f4f8] font-medium">
                {{ t('settings.accountDeletionTitle') }}
              </text>
              <text class="mt-[4rpx] block text-[24rpx] text-[#8f4a4a] leading-[34rpx]">
                {{ t('settings.accountDeletionDesc') }}
              </text>
            </view>
            <wd-button
              type="error"
              :loading="accountDeleteLoading"
              custom-class="h-[72rpx] rounded-[16rpx] px-[28rpx] text-[24rpx] font-semibold"
              @click="handleAccountDeletion"
            >
              {{ t('settings.delete') }}
            </wd-button>
          </view>
        </view>
      </view>

      <view class="mb-[32rpx]">
        <view class="mb-[24rpx] flex items-center">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            {{ t('settings.appInfo') }}
          </text>
        </view>

        <view
          class="border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
          style="background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
        >
          <view
            class="flex cursor-pointer items-center justify-between border border-[rgba(255,255,255,0.04)] rounded-[16rpx] p-[24rpx]"
            style="background: #0a0a14;"
            @click="showAbout"
          >
            <view>
              <text class="text-[28rpx] text-[#f0f4f8] font-medium">
                {{ t('settings.aboutUs') }}
              </text>
              <text class="mt-[4rpx] block text-[24rpx] text-[#5a6372]">
                {{ t('settings.appVersion') }}
              </text>
            </view>
            <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[#5a6372]" />
          </view>
        </view>
      </view>

      <!-- 语言设置 -->
      <view class="mb-[32rpx]">
        <view class="mb-[24rpx] flex items-center">
          <text class="text-[32rpx] text-[#f0f4f8] font-bold">
            {{ t('settings.languageSettings') }}
          </text>
        </view>

        <view
          class="border border-[rgba(255,255,255,0.04)] rounded-[24rpx] p-[32rpx]"
          style="background: rgba(255,255,255,0.03); box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.2);"
        >
          <view
            class="flex cursor-pointer items-center justify-between border border-[rgba(255,255,255,0.04)] rounded-[16rpx] p-[24rpx]"
            style="background: #0a0a14;"
            @click="showLanguageSheet = true"
          >
            <view>
              <text class="text-[32rpx] text-[#f0f4f8] font-medium">
                {{ t('settings.language') }}
              </text>
              <text class="mt-[4rpx] block text-[24rpx] text-[#5a6372]">
                {{ t('settings.selectLanguage') }}
              </text>
            </view>
            <view class="flex items-center">
              <text class="mr-[16rpx] text-[32rpx] text-[#5a6372] font-semibold">
                {{ supportedLanguages.find(lang => lang.code === currentLanguage)?.name }}
              </text>
              <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[#5a6372]" />
            </view>
          </view>
        </view>
      </view>

      <!-- 语言选择弹窗 -->
      <wd-action-sheet v-model="showLanguageSheet" :title="t('settings.selectLanguage')" :close-on-click-modal="true">
        <view class="language-sheet">
          <scroll-view scroll-y class="language-list">
            <view
              v-for="lang in supportedLanguages" :key="lang.code" class="language-item"
              @click="handleLanguageChange(lang.code)"
            >
              <text class="language-name">
                {{ lang.name }}
              </text>
            </view>
          </scroll-view>
        </view>
      </wd-action-sheet>

      <!-- 底部安全距离 -->
      <view style="height: env(safe-area-inset-bottom);" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
// 语言选择弹窗样式
.language-sheet {
  .language-list {
    max-height: 50vh;

    .language-item {
      padding: 30rpx 0;
      text-align: center;
      border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);

      .language-name {
        font-size: 28rpx;
        color: #f0f4f8;
      }

      &:last-child {
        border-bottom: none;
      }

      &:active {
        background-color: rgba(255, 255, 255, 0.03);
      }
    }
  }
}
</style>
