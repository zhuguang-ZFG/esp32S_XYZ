#!/usr/bin/env node
/**
 * 校验小程序中/英语言包的 key 集合是否一致。
 *
 * 只比较顶层 key（本项目的 i18n 为扁平 dot-notation），
 * 不比较占位符数量或嵌套结构。
 */

import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const ROOT = join(__dirname, '..')
const I18N_DIR = join(ROOT, 'src', 'i18n')
const FILES = {
  zh: join(I18N_DIR, 'zh_CN.ts'),
  en: join(I18N_DIR, 'en.ts'),
}

function extractKeys(source) {
  const keys = new Set()
  // 匹配 'key': ... 或 "key": ... 或 key: ... 的顶层键
  const regex = /^\s*['"]?([A-Za-z][A-Za-z0-9_.]*)['"]?:/gm
  let match
  while ((match = regex.exec(source)) !== null) {
    keys.add(match[1])
  }
  return keys
}

async function main() {
  const zhSource = await readFile(FILES.zh, 'utf-8')
  const enSource = await readFile(FILES.en, 'utf-8')

  const zhKeys = extractKeys(zhSource)
  const enKeys = extractKeys(enSource)

  const onlyInZh = [...zhKeys].filter(k => !enKeys.has(k)).sort()
  const onlyInEn = [...enKeys].filter(k => !zhKeys.has(k)).sort()

  if (onlyInZh.length === 0 && onlyInEn.length === 0) {
    console.log(`i18n key consistency OK: ${zhKeys.size} keys`)
    return 0
  }

  console.error('i18n key mismatch detected')
  if (onlyInZh.length) {
    console.error(`\nOnly in zh_CN.ts (${onlyInZh.length}):`)
    for (const k of onlyInZh) console.error(`  ${k}`)
  }
  if (onlyInEn.length) {
    console.error(`\nOnly in en.ts (${onlyInEn.length}):`)
    for (const k of onlyInEn) console.error(`  ${k}`)
  }
  return 1
}

main().then(code => process.exit(code)).catch(err => {
  console.error(err)
  process.exit(1)
})
