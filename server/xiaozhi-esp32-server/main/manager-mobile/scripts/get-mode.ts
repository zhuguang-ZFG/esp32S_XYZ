import process from 'node:process'

/**
 * 手动解析命令行参数获取 vite/uni 构建 mode。
 * 优先级：--mode <value> > build -> production > development
 */
export function getMode() {
  const args = process.argv.slice(2)
  const modeFlagIndex = args.findIndex(arg => arg === '--mode')
  return modeFlagIndex !== -1
    ? args[modeFlagIndex + 1]
    : args[0] === 'build'
      ? 'production'
      : 'development'
}
