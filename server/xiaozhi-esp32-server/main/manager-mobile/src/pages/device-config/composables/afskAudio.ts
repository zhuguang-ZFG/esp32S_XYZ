/**
 * AFSK 声波配网的音频编码工具（P3.1 从 ultrasonic-config.vue 提取）。
 *
 * 纯函数：字符串 → UTF-8 字节 → AFSK 调制 → 16bit PCM → WAV → base64 dataURI。
 * 与 Vue 无耦合，便于单测与复用。参数与原组件保持一致（参考随附 HTML 参考实现）。
 */

// AFSK 调制参数
export const MARK = 1800 // 二进制 1 的频率 (Hz)
export const SPACE = 1500 // 二进制 0 的频率 (Hz)
export const SAMPLE_RATE = 44100 // 默认采样率
export const BIT_RATE = 100 // 比特率 (bps)
export const START_BYTES = [0x01, 0x02] // 起始标记
export const END_BYTES = [0x03, 0x04] // 结束标记

// 字符串转 UTF-8 字节数组（uniapp 兼容，不依赖 TextEncoder）
export function stringToBytes(str: string): number[] {
  const bytes: number[] = []
  for (let i = 0; i < str.length; i++) {
    const code = str.charCodeAt(i)
    if (code < 0x80) {
      bytes.push(code)
    }
    else if (code < 0x800) {
      bytes.push(0xC0 | (code >> 6))
      bytes.push(0x80 | (code & 0x3F))
    }
    else if (code < 0xD800 || code >= 0xE000) {
      bytes.push(0xE0 | (code >> 12))
      bytes.push(0x80 | ((code >> 6) & 0x3F))
      bytes.push(0x80 | (code & 0x3F))
    }
    else {
      // 代理对处理
      i++
      const hi = code
      const lo = str.charCodeAt(i)
      const codePoint = 0x10000 + (((hi & 0x3FF) << 10) | (lo & 0x3FF))
      bytes.push(0xF0 | (codePoint >> 18))
      bytes.push(0x80 | ((codePoint >> 12) & 0x3F))
      bytes.push(0x80 | ((codePoint >> 6) & 0x3F))
      bytes.push(0x80 | (codePoint & 0x3F))
    }
  }
  return bytes
}

// 校验和：字节和取低 8 位
export function checksum(data: number[]): number {
  return data.reduce((sum, b) => (sum + b) & 0xFF, 0)
}

// 字节转比特位（高位在前）
export function toBits(byte: number): number[] {
  const bits: number[] = []
  for (let i = 7; i >= 0; i--) {
    bits.push((byte >> i) & 1)
  }
  return bits
}

// 浮点采样转 16bit PCM 小端字节
export function floatTo16BitPCM(floatSamples: Float32Array): Uint8Array {
  const buffer = new Uint8Array(floatSamples.length * 2)
  for (let i = 0; i < floatSamples.length; i++) {
    const s = Math.max(-1, Math.min(1, floatSamples[i]))
    const val = s < 0 ? s * 0x8000 : s * 0x7FFF
    buffer[i * 2] = val & 0xFF
    buffer[i * 2 + 1] = (val >> 8) & 0xFF
  }
  return buffer
}

// base64 编码表（btoa 缺失时的回退实现）
const base64Chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

export function base64Encode(bytes: Uint8Array): string {
  let result = ''
  let i = 0
  while (i < bytes.length) {
    const a = bytes[i++]
    const b = i < bytes.length ? bytes[i++] : 0
    const c = i < bytes.length ? bytes[i++] : 0
    const bitmap = (a << 16) | (b << 8) | c
    result += base64Chars.charAt((bitmap >> 18) & 63)
    result += base64Chars.charAt((bitmap >> 12) & 63)
    result += i - 2 < bytes.length ? base64Chars.charAt((bitmap >> 6) & 63) : '='
    result += i - 1 < bytes.length ? base64Chars.charAt(bitmap & 63) : '='
  }
  return result
}

export function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  if (typeof btoa !== 'undefined') {
    let binary = ''
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i])
    }
    return btoa(binary)
  }
  return base64Encode(bytes)
}

// 构建 WAV 文件（44 字节头 + PCM 数据），采样率可传入
export function buildWav(pcm: Uint8Array, sampleRate: number = SAMPLE_RATE): ArrayBuffer {
  const wavHeader = new Uint8Array(44)
  const dataLen = pcm.length
  const fileLen = 36 + dataLen

  const writeStr = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      wavHeader[offset + i] = str.charCodeAt(i)
    }
  }
  const write32 = (offset: number, value: number) => {
    wavHeader[offset] = value & 0xFF
    wavHeader[offset + 1] = (value >> 8) & 0xFF
    wavHeader[offset + 2] = (value >> 16) & 0xFF
    wavHeader[offset + 3] = (value >> 24) & 0xFF
  }
  const write16 = (offset: number, value: number) => {
    wavHeader[offset] = value & 0xFF
    wavHeader[offset + 1] = (value >> 8) & 0xFF
  }

  writeStr(0, 'RIFF')
  write32(4, fileLen)
  writeStr(8, 'WAVE')
  writeStr(12, 'fmt ')
  write32(16, 16)
  write16(20, 1)
  write16(22, 1)
  write32(24, sampleRate)
  write32(28, sampleRate * 2)
  write16(32, 2)
  write16(34, 16)
  writeStr(36, 'data')
  write32(40, dataLen)

  const result = new ArrayBuffer(44 + dataLen)
  const resultView = new Uint8Array(result)
  resultView.set(wavHeader)
  resultView.set(pcm, 44)
  return result
}

// 预计算音频时长（秒）：起始 + 数据 + 校验和 + 结束，按 BIT_RATE 估算
export function estimateDurationSeconds(ssid: string, password: string): number {
  const dataStr = `${ssid}\n${password}`
  const textBytes = stringToBytes(dataStr)
  const totalBits = (START_BYTES.length + textBytes.length + 1 + END_BYTES.length) * 8
  return Math.ceil(totalBits / BIT_RATE)
}

/**
 * 生成配网 WAV 的 base64 dataURI。
 * 采用降低的采样率（默认 22050）与 0.5 音量以缩小文件体积。
 */
export function buildProvisioningAudioDataUri(
  ssid: string,
  password: string,
  reducedSampleRate = 22050,
): string {
  const dataStr = `${ssid}\n${password}`
  const textBytes = stringToBytes(dataStr)
  const fullBytes = [...START_BYTES, ...textBytes, checksum(textBytes), ...END_BYTES]

  let bits: number[] = []
  fullBytes.forEach((b) => {
    bits = bits.concat(toBits(b))
  })

  const samplesPerBit = reducedSampleRate / BIT_RATE
  const totalSamples = Math.floor(bits.length * samplesPerBit)
  const floatBuf = new Float32Array(totalSamples)
  for (let i = 0; i < bits.length; i++) {
    const freq = bits[i] ? MARK : SPACE
    for (let j = 0; j < samplesPerBit; j++) {
      const time = (i * samplesPerBit + j) / reducedSampleRate
      floatBuf[i * samplesPerBit + j] = Math.sin(2 * Math.PI * freq * time) * 0.5
    }
  }

  const pcmBuf = floatTo16BitPCM(floatBuf)
  const wavBuffer = buildWav(pcmBuf, reducedSampleRate)
  const base64 = arrayBufferToBase64(wavBuffer)
  return `data:audio/wav;base64,${base64}`
}
