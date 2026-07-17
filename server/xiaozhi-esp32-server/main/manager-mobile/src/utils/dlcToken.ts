const STORAGE_PREFIX = 'dlc_api_token:'

export function persistDlcApiToken(deviceId: string, token: string) {
  if (!deviceId || !token)
    return
  uni.setStorageSync(`${STORAGE_PREFIX}${deviceId}`, token)
}

export function getStoredDlcApiToken(deviceId?: string): string {
  if (!deviceId)
    return ''
  return String(uni.getStorageSync(`${STORAGE_PREFIX}${deviceId}`) || '')
}

export function provisioningDlcSecret(deviceId?: string): string {
  return getStoredDlcApiToken(deviceId)
}
