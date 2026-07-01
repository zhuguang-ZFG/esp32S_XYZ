import { t } from '@/i18n'

export function getStatusLabel(status: string) {
  return t(`create.status.${status}` as any) || status
}

export function getStatusColor(status: string) {
  const map: Record<string, string> = {
    pending: '#fbbf24',
    queued: '#fbbf24',
    created: '#fbbf24',
    dispatching: '#fbbf24',
    dispatched: '#fbbf24',
    accepted: '#2dd4a7',
    running: '#2dd4a7',
    progress: '#2dd4a7',
    done: '#4ade80',
    completed: '#4ade80',
    failed: '#f87171',
    error: '#f87171',
    cancelled: '#8b95a3',
    dead_letter: '#f87171',
  }
  return map[status] || '#8b95a3'
}

export function getProgressPercent(status: string) {
  const map: Record<string, number> = {
    pending: 10,
    queued: 20,
    created: 10,
    dispatching: 25,
    dispatched: 30,
    accepted: 35,
    running: 60,
    progress: 70,
    done: 100,
    completed: 100,
    failed: 100,
    error: 100,
    cancelled: 100,
    dead_letter: 100,
  }
  return map[status] ?? 10
}

export function formatTime(iso?: string) {
  if (!iso)
    return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export function svgToDataUri(svg: string): string {
  if (!svg)
    return ''
  try {
    // #ifdef MP-WEIXIN
    return `data:image/svg+xml;base64,${uni.arrayBufferToBase64(stringToArrayBuffer(svg))}`
    // #endif
    // #ifndef MP-WEIXIN
    return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
    // #endif
  }
  catch {
    return ''
  }
}

export function stringToArrayBuffer(str: string): ArrayBuffer {
  const bytes = new Uint8Array(str.length)
  for (let i = 0; i < str.length; i++)
    bytes[i] = str.charCodeAt(i)
  return bytes.buffer
}

export function saveImageToAlbum(url: string) {
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200) {
        uni.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: () => uni.showToast({ title: t('create.savedToAlbum'), icon: 'success' }),
          fail: () => uni.showToast({ title: t('create.saveFailed'), icon: 'none' }),
        })
      }
      else {
        uni.showToast({ title: t('create.downloadFailed'), icon: 'none' })
      }
    },
    fail: () => uni.showToast({ title: t('create.downloadFailed'), icon: 'none' }),
  })
}
