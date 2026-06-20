<script lang="ts" setup>
import type { V2DeviceInfo } from '@/api/v2/types'
import { t } from '@/i18n'

defineProps<{
  deviceInfo: V2DeviceInfo | null
  deviceId: string
  connected: boolean
  workspaceLabel: string
  infoLoading: boolean
}>()
</script>

<template>
  <wd-cell-group border custom-class="!mt-[20rpx]">
    <wd-cell
      :title="deviceInfo?.model || deviceId"
      :label="`${t('v2.detail.hwRev')} ${deviceInfo?.hwRev || '—'} | ${t('v2.detail.fwRev')} ${deviceInfo?.fwRev || '—'}`"
    >
      <template #value>
        <wd-tag :type="connected ? 'success' : 'default'" size="mini">
          {{ connected ? t('v2.deviceDetail.connected') : t('v2.deviceDetail.disconnected') }}
        </wd-tag>
      </template>
    </wd-cell>
    <wd-cell :title="t('v2.detail.deviceId')" :value="deviceId" />
    <wd-cell :title="t('v2.detail.workspace')" :label="workspaceLabel" />
  </wd-cell-group>
</template>
