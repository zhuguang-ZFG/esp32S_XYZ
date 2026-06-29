const fs = require('node:fs')
const path = require('node:path')
const process = require('node:process')

const ci = require('miniprogram-ci')

const projectRoot = path.resolve(__dirname, '..')
const distPath = path.join(projectRoot, 'dist', 'build', 'mp-weixin')
const manifestPath = path.join(projectRoot, 'src', 'manifest.json')
const packagePath = path.join(projectRoot, 'package.json')

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'))
}

function resolvePrivateKey(appid) {
  const envPath = process.env.WX_PRIVATE_KEY_PATH
  if (envPath) {
    return path.resolve(envPath)
  }

  const candidates = [
    path.join(projectRoot, 'secrets', `private.${appid}.key`),
    path.join(projectRoot, `private.${appid}.key`),
    path.join(projectRoot, 'secrets', 'private.key'),
    path.join(projectRoot, 'private.key'),
  ]

  for (const p of candidates) {
    if (fs.existsSync(p)) {
      return p
    }
  }

  return candidates[0]
}

async function main() {
  const manifest = readJson(manifestPath)
  const pkg = readJson(packagePath)

  const appid = manifest?.['mp-weixin']?.appid || manifest?.appid
  if (!appid) {
    throw new Error(`无法从 ${manifestPath} 读取微信小程序 appid`)
  }

  const version = process.env.WX_UPLOAD_VERSION || pkg.version
  if (!version) {
    throw new Error('未指定上传版本号')
  }

  const desc = process.env.WX_UPLOAD_DESC || `v${version} auto upload`
  const privateKeyPath = resolvePrivateKey(appid)

  if (!fs.existsSync(distPath)) {
    throw new Error(`构建产物不存在: ${distPath}\n请先运行: pnpm run build:mp-weixin`)
  }

  if (!fs.existsSync(privateKeyPath)) {
    throw new Error(
      `未找到小程序代码上传私钥: ${privateKeyPath}\n\n`
      + '请按以下步骤处理:\n'
      + `1. 登录微信公众平台 https://mp.weixin.qq.com\n`
      + `2. 开发 → 开发管理 → 开发设置 → 小程序代码上传\n`
      + `3. 生成并下载私钥文件 private.${appid}.key\n`
      + `4. 将私钥放到项目目录（例如 secrets/private.${appid}.key）\n`
      + `5. 确保 .gitignore 中排除了 secrets/ 与 *.key\n\n`
      + `也可以通过环境变量指定私钥路径: WX_PRIVATE_KEY_PATH=/path/to/private.key`,
    )
  }

  const project = new ci.Project({
    appid,
    type: 'miniProgram',
    projectPath: distPath,
    privateKeyPath,
    ignores: ['node_modules/**/*'],
  })

  console.log(`开始上传微信小程序...`)
  console.log(`  appid: ${appid}`)
  console.log(`  version: ${version}`)
  console.log(`  desc: ${desc}`)
  console.log(`  projectPath: ${distPath}`)

  const result = await ci.upload({
    project,
    version,
    desc,
    setting: {
      es6: true,
      minified: true,
      minifyWXML: true,
      minifyWXSS: true,
      autoPrefixWXSS: true,
    },
    useProjectConfig: true,
    onProgressUpdate: (info) => {
      console.log(`  [ci] ${info.msg || JSON.stringify(info)}`)
    },
  })

  console.log('\n上传成功')
  console.log(JSON.stringify(result, null, 2))
  console.log(
    '\n下一步：登录微信公众平台 → 版本管理 → 开发版本，'
    + `找到 v${version}，点击「选为体验版」。`,
  )
}

main().catch((err) => {
  console.error('\n上传失败')
  console.error(err.message || err)
  process.exit(1)
})
