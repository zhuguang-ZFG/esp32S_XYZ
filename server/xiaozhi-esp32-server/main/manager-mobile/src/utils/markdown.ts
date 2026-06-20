/**
 * 轻量 Markdown → HTML 解析器（小程序专用）
 * 支持：代码块、行内代码、加粗、斜体、列表、引用、换行
 */

export interface MarkdownNode {
  type: 'text' | 'code-block' | 'inline-code' | 'bold' | 'italic' | 'list' | 'quote' | 'break'
  content: string
  lang?: string
}

/**
 * 将 Markdown 文本解析为 HTML 字符串，适合 rich-text 组件
 */
export function markdownToHtml(text: string): string {
  if (!text) return ''

  let html = escapeHtml(text)

  // 代码块（先处理，避免内部内容被其他规则干扰）
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, (_match, lang, code) => {
    const cls = lang ? `class="code-lang-${lang}"` : ''
    return `<pre ${cls}><code>${code.trim()}</code></pre>`
  })

  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

  // 加粗
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/__([^_]+)__/g, '<strong>$1</strong>')

  // 斜体
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>')
  html = html.replace(/_([^_]+)_/g, '<em>$1</em>')

  // 引用块
  html = html.replace(/^&gt;\s*(.+)$/gm, '<blockquote>$1</blockquote>')

  // 无序列表
  html = html.replace(/^-\s+(.+)$/gm, '<li>$1</li>')
  html = html.replace(/^(<li>.+<\/li>\n?)+/gm, (match) => {
    return `<ul>${match}</ul>`
  })

  // 有序列表
  html = html.replace(/^\d+\.\s+(.+)$/gm, '<li>$1</li>')
  html = html.replace(/^(<li>.+<\/li>\n?)+/gm, (match) => {
    if (match.includes('<ul>')) return match
    return `<ol>${match}</ol>`
  })

  // 换行 → <br>（保留段落结构）
  html = html.replace(/\n/g, '<br>')

  // 链接
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')

  // 清理多余的 <br> 在 block 元素之间
  html = html.replace(/<\/pre><br>/g, '</pre>')
  html = html.replace(/<br><pre/g, '<pre')
  html = html.replace(/<\/blockquote><br>/g, '</blockquote>')
  html = html.replace(/<br><blockquote/g, '<blockquote')
  html = html.replace(/<\/ul><br>/g, '</ul>')
  html = html.replace(/<br><ul/g, '<ul')
  html = html.replace(/<\/ol><br>/g, '</ol>')
  html = html.replace(/<br><ol/g, '<ol')

  return html
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/**
 * 提取文本中的代码块（用于复制按钮）
 */
export function extractCodeBlocks(text: string): Array<{ lang: string; code: string }> {
  const blocks: Array<{ lang: string; code: string }> = []
  const regex = /```(\w*)\n?([\s\S]*?)```/g
  let match
  while ((match = regex.exec(text)) !== null) {
    blocks.push({ lang: match[1] || 'text', code: match[2].trim() })
  }
  return blocks
}

/**
 * 检测是否包含 Markdown 语法
 */
export function hasMarkdown(text: string): boolean {
  return /```|`|\*\*|__|>|^-\s/.test(text)
}

/**
 * 纯文本复制（去除 Markdown 标记）
 */
export function stripMarkdown(text: string): string {
  return text
    .replace(/```\w*\n?([\s\S]*?)```/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/^-\s+/gm, '• ')
    .replace(/^\d+\.\s+/gm, '')
    .replace(/^&gt;\s*/gm, '')
}
