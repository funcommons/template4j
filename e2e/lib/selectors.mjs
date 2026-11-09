// data-testid helper builders. Prevents tests from depending on the
// internal class-name chain of Element Plus / our components.

export const TESTID = {
  page: 'learn-page',
  sidebar: 'learn-sidebar',
  search: 'learn-search',
  menu: 'learn-menu',
  cat: (name) => `learn-cat-${name}`,
  item: (id) => `learn-item-${id}`,
  detail: 'learn-detail',
  hero: 'learn-hero',
  title: 'learn-title',
  prev: 'learn-prev',
  next: 'learn-next',
  intro: 'learn-intro',
  sections: 'learn-sections',
  mdSection: (i) => `learn-md-section-${i}`,
  jrSection: (i) => `learn-jr-section-${i}`,
  runBtn: (i) => `[data-testid="learn-run-${i}"]`,
  output: (i) => `[data-testid="learn-output-${i}"]`,
  placeholder: (i) => `[data-testid="learn-placeholder-${i}"]`,
  result: (i) => `[data-testid="learn-result-${i}"]`,
  error: (i) => `[data-testid="learn-error-${i}"]`,
  copyBtn: (i, name) => `[data-testid="learn-copy-${i}-${name}"]`,
  varInput: (i, name) => `[data-testid="learn-input-${i}-${name}"]`,
}

export const I18N_LABEL = {
  run: ['运行', 'Run'],
  prev: ['上一个', 'Previous'],
  next: ['下一个', 'Next'],
  copy: ['复制', 'Copy'],
  placeholder: ['点击「运行」查看渲染结果', 'Click "Run" to render'],
}

/**
 * Build a RegExp matching either zh-CN or en-US copy of a UI label.
 * Use as `page.locator(...).filter({ hasText: labelRegex('运行', 'Run') })`.
 */
export function labelRegex(...candidates) {
  const body = candidates.map((c) => c.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')
  return new RegExp(body)
}
