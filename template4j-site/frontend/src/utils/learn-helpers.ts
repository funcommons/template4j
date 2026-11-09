// Pure helpers extracted from Learn.vue. Kept side-effect-free so they can be
// unit-tested without spinning up Vue / Element Plus / a real DOM. Each
// function was originally embedded in <script setup> and gets re-imported
// into Learn.vue.

/**
 * Build the dom-stable string used as both a data-testid suffix and a
 * Clipboard copy-key. The opposite-direction match (`copyKeyFor(idx, name)
 * === copiedKey`) is what flips the ⧉ button to ✓, so the format must not
 * drift between calls.
 *
 * Example: copyKeyFor(2, 'data') === 'var-2-data'
 */
export function copyKeyFor(idx: number, name: string): string {
  return `var-${idx}-${name}`
}

/**
 * A json-renderer section is "runnable" iff it provides at least one
 * variable called `template`. Sections without a template variable are
 * code-only (Java snippet, see codeOnlyHint) and don't expose a Run button.
 */
export function isRunnable(section: any): boolean {
  if (!section || section.type !== 'json-renderer') return false
  const vars = section.content?.variables || []
  return vars.some((v: any) => v?.name === 'template')
}

/**
 * Bucket difficulty (1..3) into Element Plus semantic types.
 *   1 → success (green)
 *   2 → warning (orange)
 *   ≥3 → danger (red)
 *
 * Values outside 1..3 are clamped to the danger bucket — better an over-
 * red chip than a silent success.
 */
export function difficultyType(d: number): 'success' | 'warning' | 'danger' {
  if (d <= 1) return 'success'
  if (d <= 2) return 'warning'
  return 'danger'
}

/**
 * Map a difficulty value to a human label key. Pass i18n.t or a stub:
 *
 *   difficultyLabel(2, k => k) === 'learn.diff_intermediate'
 *
 * Returns the key (not translated text) so callers can plug the
 * translation function of their choice.
 */
export function difficultyLabelKey(d: number): string {
  if (d <= 1) return 'learn.diff_beginner'
  if (d <= 2) return 'learn.diff_intermediate'
  return 'learn.diff_advanced'
}

import type { ExampleMeta } from '@/types/api'

/**
 * Group an examples list by category while filtering by `searchKey`. Returns
 * a frozen shape that's stable across re-renders (caller can use `category`
 * as a v-for key). Order of categories follows first-appearance in `examples`.
 *
 * Empty matches produce a single-entry "其他" group only when no real
 * category is provided in the input — current data always has one.
 */
export function groupExamplesByCategory(
  examples: ExampleMeta[],
  searchKey: string,
  otherCategoryLabel = '其他',
): Array<{ name: string; items: ExampleMeta[] }> {
  const needle = searchKey.trim().toLowerCase()
  const filtered = examples.filter((e) =>
    !needle
    || e.id.includes(needle)
    || e.title.toLowerCase().includes(needle)
    || (e.category || '').toLowerCase().includes(needle)
  )
  const groups = new Map<string, ExampleMeta[]>()
  for (const e of filtered) {
    const key = e.category || otherCategoryLabel
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(e)
  }
  return Array.from(groups.entries()).map(([name, items]) => ({ name, items }))
}
