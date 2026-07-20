<route lang="jsonc" type="page">
{
  "needLogin": true,
  "layout": "tabbar",
  "style": {
    "navigationBarTitleText": "设置",
    "navigationStyle": "custom"
  }
}
</route>

<script lang="ts" setup>
import type { Language } from '@/store/lang'
import { onMounted, ref } from 'vue'
import { useToast } from 'wot-design-uni/components/wd-toast'
import { changeLanguage, getCurrentLanguage, getSupportedLanguages, t } from '@/i18n'
import manifest from '@/manifest.json'
import { isMp } from '@/utils/platform'
import SectionCard from '@/components/section-card.vue'
import { useAccountDeletion } from '@/hooks/useAccountDeletion'
import { useCacheManager } from '@/hooks/useCacheManager'
import { useNotifications } from '@/hooks/useNotifications'
import { useServerUrl } from '@/hooks/useServerUrl'

// --- 退出登录（P2-18 从 mine 页合并） ---
function goLogout() {
  uni.vibrateShort({ type: 'medium' })
  uni.showModal({
    title: t('mine.logoutConfirmTitle'),
    content: t('mine.logoutConfirmContent'),
    success: (res) => {
      if (res.confirm) {
        uni.clearStorageSync()
        uni.reLaunch({ url: '/pages/v2/login/index' })
      }
    },
  })
}

function goVoiceprint() {
  uni.navigateTo({ url: '/pages/voiceprint/index' })
}

defineOptions({ name: 'SettingsPage' })

const toast = useToast()

// --- 缓存管理 ---
const { cacheInfo, getCacheInfo, clearAllCacheAfterUrlChange, clearCache } = useCacheManager()

// --- 服务端地址 ---
const { baseUrlInput, urlError, loadServerBaseUrl, validateUrl, saveServerBaseUrl, resetServerBaseUrl } = useServerUrl(clearAllCacheAfterUrlChange)

// --- 通知订阅 ---
const { notificationEnabled, notificationLoading, loadNotificationSubs, handleToggleNotifications } = useNotifications()

// --- 账号注销 ---
const { accountDeleteLoading, handleAccountDeletion } = useAccountDeletion(clearAllCacheAfterUrlChange)

// --- 语言切换 ---
const supportedLanguages = getSupportedLanguages()
const currentLanguage = ref<Language>(getCurrentLanguage())
const showLanguageSheet = ref(false)

function handleLanguageChange(lang: Language) {
  changeLanguage(lang)
  showLanguageSheet.value = false
  currentLanguage.value = lang
  toast.success(t('settings.languageChanged'))
}

// --- 关于 ---
function showAbout() {
  uni.showModal({
    title: t('settings.aboutApp', { appName: import.meta.env.VITE_APP_TITLE }),
    content: t('settings.aboutContent', {
      appName: import.meta.env.VITE_APP_TITLE,
      version: manifest.versionName,
    }),
    showCancel: false,
    confirmText: t('common.confirm'),
  })
}

function openPrivacyPermissions() {
  uni.navigateTo({ url: '/pages/settings/privacy-permissions' })
}

onMounted(async () => {
  if (!isMp)
    loadServerBaseUrl()
  getCacheInfo()
  loadNotificationSubs()
  uni.setNavigationBarTitle({ title: t('settings.title') })
})
</script>

<template>
  <view class="page-enter min-h-screen" style="background: var(--bg);">
    <wd-navbar
      :title="t('settings.title')" safe-area-inset-top placeholder fixed
      custom-class="!bg-[var(--bg)]"
      title-class="!text-[var(--text)]"
    />

    <view class="p-[24rpx]">
      <!-- 网络设置 - 仅在非小程序环境显示 -->
      <SectionCard v-if="!isMp" :title="t('settings.networkSettings')">
        <view class="mb-[24rpx]">
          <text class="text-[28rpx] text-[var(--text)] font-semibold">
            {{ t('settings.serverApiUrl') }}
          </text>
          <text class="mt-[8rpx] block text-[24rpx] text-[var(--dim)]">
            {{ t('settings.modifyWillClearCache') }}
          </text>
        </view>
        <view class="mb-[24rpx]">
          <view class="w-full overflow-hidden border border-[var(--border)] rounded-[16rpx]" style="background: var(--bg2);">
            <wd-input
              v-model="baseUrlInput" type="text" clearable :maxlength="200"
              :placeholder="t('settings.enterServerUrl')"
              custom-class="!border-none !bg-transparent h-[64rpx] px-[24rpx] items-center"
              input-class="text-[28rpx] text-[var(--text)]" @input="validateUrl" @blur="validateUrl"
            />
          </view>
          <text v-if="urlError" class="mt-[8rpx] block text-[24rpx] text-[var(--danger)]">
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
            custom-class="flex-1 h-[88rpx] rounded-[20rpx] text-[28rpx] font-semibold border-[var(--border)] text-[var(--muted)]"
            @click="resetServerBaseUrl"
          >
            {{ t('settings.resetDefault') }}
          </wd-button>
        </view>
      </SectionCard>

      <!-- 缓存管理 -->
      <SectionCard :title="t('settings.cacheManagement')">
        <view class="space-y-[16rpx]">
          <view
            class="flex items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
            style="background: var(--bg2);"
          >
            <view>
              <text class="text-[28rpx] text-[var(--text)] font-medium">
                {{ t('settings.totalCacheSize') }}
              </text>
              <text class="mt-[4rpx] block text-[24rpx] text-[var(--dim)]">
                {{ t('settings.appDataSize') }}
              </text>
            </view>
            <text class="text-[28rpx] text-[var(--muted)] font-semibold">
              {{ cacheInfo.storageSize }}
            </text>
          </view>
          <view
            class="flex items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
            style="background: var(--bg2);"
          >
            <view>
              <text class="text-[28rpx] text-[var(--text)] font-medium">
                {{ t('settings.cacheClear') }}
              </text>
              <text class="mt-[4rpx] block text-[24rpx] text-[var(--dim)]">
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
      </SectionCard>

      <!-- 隐私与权限 -->
      <SectionCard :title="t('settings.privacyTitle')">
        <view
          class="flex cursor-pointer items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
          style="background: var(--bg2);"
          @click="openPrivacyPermissions"
        >
          <view>
            <text class="text-[28rpx] text-[var(--text)] font-medium">
              {{ t('settings.privacyAuth') }}
            </text>
            <text class="mt-[4rpx] block text-[24rpx] text-[var(--dim)]">
              {{ t('settings.privacyDesc') }}
            </text>
          </view>
          <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[var(--dim)]" />
        </view>
      </SectionCard>

      <!-- 通知订阅 -->
      <SectionCard :title="t('settings.notificationsTitle')">
        <view
          class="flex items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
          style="background: var(--bg2);"
        >
          <view>
            <text class="text-[28rpx] text-[var(--text)] font-medium">
              {{ t('settings.pushNotifications') }}
            </text>
            <text class="mt-[4rpx] block text-[24rpx] text-[var(--dim)]">
              {{ t('settings.pushNotificationsDesc') }}
            </text>
          </view>
          <wd-switch
            v-model="notificationEnabled"
            :loading="notificationLoading"
            @change="handleToggleNotifications"
          />
        </view>
      </SectionCard>

      <!-- 账号注销 -->
      <SectionCard :title="t('settings.accountDeletion')" variant="danger">
        <view class="flex items-center justify-between gap-[24rpx]">
          <view class="min-w-0 flex-1">
            <text class="text-[28rpx] text-[var(--text)] font-medium">
              {{ t('settings.accountDeletionTitle') }}
            </text>
            <text class="mt-[4rpx] block text-[24rpx] text-[var(--danger)] leading-[34rpx]">
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
      </SectionCard>

      <!-- 关于 -->
      <SectionCard :title="t('settings.appInfo')">
        <view
          class="flex cursor-pointer items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
          style="background: var(--bg2);"
          @click="showAbout"
        >
          <view>
            <text class="text-[28rpx] text-[var(--text)] font-medium">
              {{ t('settings.aboutUs') }}
            </text>
            <text class="mt-[4rpx] block text-[24rpx] text-[var(--dim)]">
              {{ t('settings.appVersion') }}
            </text>
          </view>
          <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[var(--dim)]" />
        </view>
      </SectionCard>

      <!-- 语言设置 -->
      <SectionCard :title="t('settings.languageSettings')">
        <view
          class="flex cursor-pointer items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
          style="background: var(--bg2);"
          @click="showLanguageSheet = true"
        >
          <view>
            <text class="text-[32rpx] text-[var(--text)] font-medium">
              {{ t('settings.language') }}
            </text>
            <text class="mt-[4rpx] block text-[24rpx] text-[var(--dim)]">
              {{ t('settings.selectLanguage') }}
            </text>
          </view>
          <view class="flex items-center">
            <text class="mr-[16rpx] text-[32rpx] text-[var(--dim)] font-semibold">
              {{ supportedLanguages.find(lang => lang.code === currentLanguage)?.name }}
            </text>
            <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[var(--dim)]" />
          </view>
        </view>
      </SectionCard>

      <!-- 声纹录入（P2-18 从 mine 页合并） -->
      <SectionCard :title="t('mine.featureCenter')">
        <view
          class="flex cursor-pointer items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
          style="background: var(--bg2);"
          @click="goVoiceprint"
        >
          <view>
            <text class="text-[28rpx] text-[var(--text)] font-medium">
              {{ t('mine.voiceprint') }}
            </text>
            <text class="mt-[4rpx] block text-[24rpx] text-[var(--dim)]">
              {{ t('mine.voiceprintDesc') }}
            </text>
          </view>
          <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[var(--dim)]" />
        </view>
      </SectionCard>

      <!-- 退出登录（P2-18 从 mine 页合并） -->
      <SectionCard :title="t('mine.system')" variant="danger">
        <view
          class="flex cursor-pointer items-center justify-between border border-[var(--border)] rounded-[16rpx] p-[24rpx]"
          style="background: var(--bg2);"
          @click="goLogout"
        >
          <view>
            <text class="text-[28rpx] text-[var(--text)] font-medium">
              {{ t('mine.logout') }}
            </text>
            <text class="mt-[4rpx] block text-[24rpx] text-[var(--danger)]">
              {{ t('mine.logoutConfirmContent') }}
            </text>
          </view>
          <wd-icon name="arrow-right" custom-class="text-[32rpx] text-[var(--dim)]" />
        </view>
      </SectionCard>

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
.language-sheet {
  .language-list {
    max-height: 50vh;

    .language-item {
      padding: 30rpx 0;
      text-align: center;
      border-bottom: 1rpx solid var(--border);

      .language-name {
        font-size: 28rpx;
        color: var(--text);
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
