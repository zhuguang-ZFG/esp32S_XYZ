export const provisioningContract = {
  primaryChannel: 'ble_blufi',
  fallbackChannel: 'softap_http',
  blufiDeviceName: 'Xiaozhi-Blufi',
  legacyBlufiDeviceName: 'BLUFI_DEVICE',
  blufiServiceUuidCandidates: ['0000ffff-0000-1000-8000-00805f9b34fb', 'ffff'],
  blufiWriteCharacteristicUuidCandidates: ['0000ff01-0000-1000-8000-00805f9b34fb', 'ff01'],
  blufiNotifyCharacteristicUuidCandidates: ['0000ff02-0000-1000-8000-00805f9b34fb', 'ff02'],
  softApBaseUrl: 'http://192.168.4.1',
  softApScanPath: '/scan',
  softApSubmitPath: '/submit',
  softApExitPath: '/exit',
  softApSsidHint: 'xiaozhi-XXXXXX',
  submitPayloadFields: ['ssid', 'password', 'server_host', 'device_secret'] as const,
}

export function softApUrl(path: string) {
  return `${provisioningContract.softApBaseUrl}${path}`
}

export interface ProvisioningCredentialPayload {
  ssid: string
  password: string
  server_host?: string
  device_secret?: string
}

export function normalizeBleUuid(uuid: string) {
  return uuid.toLowerCase().replace(/-/g, '')
}

export function matchesBleUuid(uuid: string, candidates: readonly string[]) {
  const normalized = normalizeBleUuid(uuid)
  return candidates.some(candidate => normalized === normalizeBleUuid(candidate))
}

export function createDesignTimeBlufiCredentialPayload(payload: ProvisioningCredentialPayload) {
  // This is a design-time contract payload. Production BluFi must use Espressif protocol frames.
  return encodeUtf8(JSON.stringify({
    ssid: payload.ssid,
    password: payload.password,
    server_host: payload.server_host || '',
    device_secret: payload.device_secret || '',
  }))
}

function encodeUtf8(value: string) {
  const encoded = encodeURIComponent(value).replace(/%([0-9A-F]{2})/g, (_, hex) =>
    String.fromCharCode(Number.parseInt(hex, 16)),
  )
  const buffer = new ArrayBuffer(encoded.length)
  const view = new Uint8Array(buffer)
  for (let i = 0; i < encoded.length; i += 1)
    view[i] = encoded.charCodeAt(i)
  return buffer
}
