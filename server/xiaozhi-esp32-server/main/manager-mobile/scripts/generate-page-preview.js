/* eslint-disable no-console */
const fs = require('node:fs')
const path = require('node:path')

const projectRoot = path.resolve(__dirname, '..')
const pagesJsonPath = path.join(projectRoot, 'src', 'pages.json')
const outDir = path.join(projectRoot, 'preview')
const outHtmlPath = path.join(outDir, 'pages-preview.html')
const outJsonPath = path.join(outDir, 'pages-preview.json')

function normalizeRoute(route) {
  if (!route)
    return '/'
  const clean = String(route).replace(/^\//, '')
  return `/#/${clean}`
}

function collectPages(config) {
  const pages = []
  ;(config.pages || []).forEach((item) => {
    if (item && item.path) {
      pages.push({
        path: item.path,
        package: 'main',
        title: item?.style?.navigationBarTitleText || '',
      })
    }
  })

  ;(config.subPackages || []).forEach((subPkg) => {
    const root = String(subPkg?.root || '').replace(/\/$/, '')
    ;(subPkg?.pages || []).forEach((page) => {
      if (!page?.path)
        return
      const fullPath = root ? `${root}/${page.path}` : page.path
      pages.push({
        path: fullPath,
        package: root || 'sub',
        title: page?.style?.navigationBarTitleText || '',
      })
    })
  })

  return pages
}

function renderHtml(pages) {
  const items = pages.map((page, index) => {
    const route = normalizeRoute(page.path)
    const titleText = page.title ? `（${page.title}）` : ''
    return `
      <tr>
        <td>${index + 1}</td>
        <td><code>${page.path}</code></td>
        <td>${titleText || '-'}</td>
        <td><code>${page.package}</code></td>
        <td><code>${route}</code></td>
      </tr>
    `
  }).join('\n')

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>小程序页面预览总览</title>
  <style>
    body {
      margin: 24px;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      color: #1f2937;
      background: #f9fafb;
    }
    .card {
      background: #fff;
      border-radius: 12px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
      padding: 20px;
      max-width: 1100px;
      margin: 0 auto;
    }
    h1 {
      margin: 0 0 12px;
      font-size: 24px;
    }
    p {
      margin: 0 0 16px;
      color: #4b5563;
      line-height: 1.6;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      background: #fff;
    }
    th, td {
      border: 1px solid #e5e7eb;
      padding: 10px 12px;
      text-align: left;
      vertical-align: top;
      font-size: 14px;
    }
    th {
      background: #f3f4f6;
    }
    code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      font-size: 12px;
      color: #111827;
    }
    .hint {
      margin-top: 16px;
      font-size: 13px;
      color: #6b7280;
    }
  </style>
</head>
<body>
  <div class="card">
    <h1>小程序页面预览总览</h1>
    <p>该文件由 <code>scripts/generate-page-preview.js</code> 自动生成，基于 <code>src/pages.json</code> 汇总页面路由。你可以在 H5 预览环境中用 Route 字段快速跳转验证页面。</p>
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>页面路径</th>
          <th>页面标题</th>
          <th>分包</th>
          <th>H5 Route（用于预览）</th>
        </tr>
      </thead>
      <tbody>
        ${items}
      </tbody>
    </table>
    <p class="hint">注：小程序真实渲染仍以微信开发者工具为准；此总览页用于批量核对页面清单与预览路由。</p>
  </div>
</body>
</html>`
}

function main() {
  if (!fs.existsSync(pagesJsonPath)) {
    throw new Error(`未找到 pages.json: ${pagesJsonPath}`)
  }

  const configText = fs.readFileSync(pagesJsonPath, 'utf8')
  const config = JSON.parse(configText)
  const pages = collectPages(config)

  fs.mkdirSync(outDir, { recursive: true })
  fs.writeFileSync(outJsonPath, JSON.stringify({ generatedAt: new Date().toISOString(), pages }, null, 2), 'utf8')
  fs.writeFileSync(outHtmlPath, renderHtml(pages), 'utf8')

  console.log(`页面总数: ${pages.length}`)
  console.log(`JSON 输出: ${outJsonPath}`)
  console.log(`HTML 输出: ${outHtmlPath}`)
}

main()
