<script lang="ts" setup>
import type { ConfigProviderThemeVars } from 'wot-design-uni/components/wd-config-provider/types'
import FgTabbar from './fg-tabbar/fg-tabbar.vue'

const themeVars: ConfigProviderThemeVars = {
  colorTheme: '#3b82f6',
  buttonPrimaryBgColor: '#3b82f6',
  buttonPrimaryColor: '#3b82f6',
}
</script>

<template>
  <wd-config-provider :theme-vars="themeVars">
    <slot />
    <FgTabbar />
    <wd-toast />
    <wd-message-box />
  </wd-config-provider>
</template>
