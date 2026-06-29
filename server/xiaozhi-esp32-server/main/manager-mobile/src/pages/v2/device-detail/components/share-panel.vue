<script lang="ts" setup>
import type { V2ShareResponse } from '@/api/v2'
import { t } from '@/i18n'

defineProps<{
  shares: V2ShareResponse[]
}>()

const emit = defineEmits<{
  createShare: []
  revokeShare: [shareToken: string]
}>()
const shareLoading = defineModel<boolean>('shareLoading', { default: false })
const sharePermission = defineModel<string>('sharePermission', { default: 'view' })
const shareExpiry = defineModel<string>('shareExpiry', { default: '7d' })

const expiryOptions = [
  { label: '1天', value: '1d' },
  { label: '7天', value: '7d' },
  { label: '30天', value: '30d' },
  { label: '永久', value: '365d' },
]

function expiryToISO(expiry: string): string {
  const now = new Date()
  const match = expiry.match(/^(\d+)d$/)
  const days = match ? Number.parseInt(match[1], 10) : 7
  now.setDate(now.getDate() + days)
  return now.toISOString()
}

defineExpose({ expiryToISO })
</script>

<template>
  <view class="bento-card">
    <view class="bento-title">
      {{ t('v2.detail.shareTitle') }}
    </view>
    <text class="state-label">
      {{ t('v2.detail.shareDesc') }}
    </text>

    <!-- 创建分享 -->
    <view class="share-create">
      <view class="share-options">
        <view class="option-group">
          <text class="option-label">
            {{ t('v2.detail.sharePermission') }}
          </text>
          <view class="option-tags">
            <wd-tag
              :type="sharePermission === 'view' ? 'primary' : 'default'"
              size="small"
              round
              custom-class="!mr-[12rpx]"
              @click="sharePermission = 'view'"
            >
              {{ t('v2.detail.shareView') }}
            </wd-tag>
            <wd-tag
              :type="sharePermission === 'control' ? 'primary' : 'default'"
              size="small"
              round
              @click="sharePermission = 'control'"
            >
              {{ t('v2.detail.shareControl') }}
            </wd-tag>
          </view>
        </view>
        <view class="option-group">
          <text class="option-label">
            {{ t('v2.detail.shareExpiry') }}
          </text>
          <view class="option-tags">
            <wd-tag
              v-for="opt in expiryOptions"
              :key="opt.value"
              :type="shareExpiry === opt.value ? 'primary' : 'default'"
              size="small"
              round
              custom-class="!mr-[12rpx]"
              @click="shareExpiry = opt.value"
            >
              {{ opt.label }}
            </wd-tag>
          </view>
        </view>
      </view>
      <wd-button type="primary" round size="small" :loading="shareLoading" @click="emit('createShare')">
        {{ t('v2.detail.createShare') }}
      </wd-button>
    </view>

    <!-- 已有分享列表 -->
    <view v-if="shares.length" class="share-list">
      <view class="share-list-title">
        {{ t('v2.detail.activeShares') }} ({{ shares.length }})
      </view>
      <view v-for="share in shares" :key="share.shareId" class="share-item">
        <view class="share-item-info">
          <view class="share-token-row">
            <text class="share-token-label">
              {{ t('v2.detail.shareToken') }}:
            </text>
            <text class="share-token-value" @click="() => {}">
              {{ share.shareToken.slice(0, 8) }}...{{ share.shareToken.slice(-4) }}
            </text>
          </view>
          <view class="share-meta">
            <wd-tag :type="share.permission === 'control' ? 'warning' : 'primary'" size="mini" round custom-class="!mr-[8rpx]">
              {{ share.permission === 'control' ? t('v2.detail.shareControl') : t('v2.detail.shareView') }}
            </wd-tag>
            <text class="share-expires">
              {{ t('v2.detail.expiresAt') }}: {{ share.expiresAt?.slice(0, 10) || '-' }}
            </text>
          </view>
        </view>
        <wd-button type="error" round plain size="small" :disabled="shareLoading" @click="emit('revokeShare', share.shareToken)">
          {{ t('v2.detail.revokeShare') }}
        </wd-button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.bento-card {
  background: var(--surface);
  border: 1rpx solid var(--border);
  border-radius: var(--r);
  padding: 28rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(24rpx);
}

.bento-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8rpx;
}

.state-label {
  font-size: 24rpx;
  color: var(--muted);
}

.share-create {
  margin-top: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.share-options {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.option-group {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.option-label {
  font-size: 24rpx;
  color: var(--muted);
}

.option-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.share-list {
  margin-top: 24rpx;
  border-top: 1rpx solid var(--border);
  padding-top: 20rpx;
}

.share-list-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 16rpx;
}

.share-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
}

.share-item:last-child {
  border-bottom: none;
}

.share-item-info {
  flex: 1;
  min-width: 0;
}

.share-token-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 8rpx;
}

.share-token-label {
  font-size: 22rpx;
  color: var(--muted);
}

.share-token-value {
  font-size: 24rpx;
  color: var(--text);
  font-family: monospace;
}

.share-meta {
  display: flex;
  align-items: center;
}

.share-expires {
  font-size: 22rpx;
  color: var(--muted);
}
</style>
