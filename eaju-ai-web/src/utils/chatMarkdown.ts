import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github.css'

const MD_ALLOWED_TAGS = [
  'p',
  'br',
  'strong',
  'b',
  'em',
  'i',
  'del',
  's',
  'ul',
  'ol',
  'li',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'blockquote',
  'code',
  'pre',
  'a',
  'hr',
  'table',
  'thead',
  'tbody',
  'tr',
  'th',
  'td',
  'div',
  'span',
  'button',
]

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

md.renderer.rules.link_open = (tokens, idx, options, _env, self) => {
  const token = tokens[idx]
  if (token.attrIndex('target') < 0) {
    token.attrPush(['target', '_blank'])
  }
  if (token.attrIndex('rel') < 0) {
    token.attrPush(['rel', 'noopener noreferrer'])
  }
  return self.renderToken(tokens, idx, options)
}

md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const info = token.info ? md.utils.unescapeAll(token.info).trim() : ''
  const rawLang = info.split(/\s+/g)[0] || ''
  const lang = /^[a-z0-9+#.\-]+$/i.test(rawLang) ? rawLang : ''
  const code = token.content.replace(/\n$/, '')
  let inner: string
  if (lang && hljs.getLanguage(lang)) {
    try {
      inner = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
    } catch {
      inner = md.utils.escapeHtml(code)
    }
  } else if (lang) {
    inner = md.utils.escapeHtml(code)
  } else {
    try {
      inner = hljs.highlightAuto(code).value
    } catch {
      inner = md.utils.escapeHtml(code)
    }
  }
  const langLabel = lang ? md.utils.escapeHtml(lang) : 'code'
  const langClass = lang ? md.utils.escapeHtml(lang) : ''
  const codeClass =
    langClass !== '' ? `hljs language-${langClass}` : 'hljs'
  return (
    `<div class="md-code-block">` +
    `<div class="md-code-head">` +
    `<span class="md-code-lang">${langLabel}</span>` +
    `<button type="button" class="md-copy-btn" title="复制代码">复制</button></div>` +
    `<pre class="md-code-pre"><code class="${codeClass}">` +
    inner +
    `</code></pre></div>`
  )
}

/**
 * 将助手返回的 Markdown（含 ``` 代码块）转为可安全插入的 HTML。
 */
export function renderChatMarkdown(source: string): string {
  const raw = source ?? ''
  if (!raw.trim()) {
    return ''
  }
  const dirty = md.render(raw)
  return DOMPurify.sanitize(dirty, {
    ALLOWED_TAGS: MD_ALLOWED_TAGS,
    ALLOWED_ATTR: ['href', 'target', 'rel', 'title', 'class', 'colspan', 'rowspan', 'type'],
    ALLOW_DATA_ATTR: false,
  })
}
