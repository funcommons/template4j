import { describe, it, expect } from 'vitest'
import {
  copyKeyFor,
  isRunnable,
  difficultyType,
  difficultyLabelKey,
  groupExamplesByCategory,
} from '../learn-helpers'

describe('copyKeyFor', () => {
  it('produces a stable opaque id from idx+name', () => {
    expect(copyKeyFor(0, 'template')).toBe('var-0-template')
    expect(copyKeyFor(12, 'data')).toBe('var-12-data')
  })
  it('keeps idx that looks like a Map key (no separator collision)', () => {
    // paranoid: ensure `${idx}-${name}` never collides for distinct (idx, name)
    const seen = new Set<string>()
    for (let i = 0; i < 5; i++) {
      for (const n of ['template', 'data', 'other']) {
        seen.add(copyKeyFor(i, n))
      }
    }
    expect(seen.size).toBe(15)
  })
})

describe('isRunnable', () => {
  it('returns false for null / undefined / non-json-renderer sections', () => {
    expect(isRunnable(null)).toBe(false)
    expect(isRunnable(undefined)).toBe(false)
    expect(isRunnable({ type: 'markdown' })).toBe(false)
    expect(isRunnable({ type: 'json-renderer', content: {} })).toBe(false)
  })
  it('returns false when json-renderer has no template variable', () => {
    expect(isRunnable({
      type: 'json-renderer',
      content: { variables: [{ name: 'data' }] },
    })).toBe(false)
  })
  it('returns true when json-renderer has a template variable', () => {
    expect(isRunnable({
      type: 'json-renderer',
      content: { variables: [{ name: 'data' }, { name: 'template' }] },
    })).toBe(true)
  })
  it('treats missing variables array as no-template', () => {
    expect(isRunnable({ type: 'json-renderer', content: {} })).toBe(false)
    expect(isRunnable({
      type: 'json-renderer',
      content: { variables: null },
    })).toBe(false)
  })
})

describe('difficultyType', () => {
  it('maps difficulty 1 to success', () => {
    expect(difficultyType(1)).toBe('success')
    expect(difficultyType(0)).toBe('success') // clamped
    expect(difficultyType(-3)).toBe('success')
  })
  it('maps difficulty 2 to warning', () => {
    expect(difficultyType(2)).toBe('warning')
  })
  it('maps difficulty ≥3 to danger', () => {
    expect(difficultyType(3)).toBe('danger')
    expect(difficultyType(7)).toBe('danger')
  })
})

describe('difficultyLabelKey', () => {
  it('returns i18n keys, not translated text', () => {
    expect(difficultyLabelKey(1)).toBe('learn.diff_beginner')
    expect(difficultyLabelKey(2)).toBe('learn.diff_intermediate')
    expect(difficultyLabelKey(3)).toBe('learn.diff_advanced')
  })
})

describe('groupExamplesByCategory', () => {
  const sample = [
    { id: 'a', title: 'A', description: '', difficulty: 1, template_type: 'json', category: 'foo' },
    { id: 'b', title: 'B', description: '', difficulty: 1, template_type: 'json', category: 'bar' },
    { id: 'c', title: 'C', description: '', difficulty: 1, template_type: 'json', category: 'foo' },
  ]

  it('groups by category preserving order', () => {
    const result = groupExamplesByCategory(sample, '')
    expect(result.map((g) => g.name)).toEqual(['foo', 'bar'])
    expect(result[0].items.map((e) => e.id)).toEqual(['a', 'c'])
    expect(result[1].items.map((e) => e.id)).toEqual(['b'])
  })

  it('filters by id substring (case-insensitive)', () => {
    const result = groupExamplesByCategory(sample, 'B')
    expect(result).toHaveLength(1)
    expect(result[0].items.map((e) => e.id)).toEqual(['b'])
  })

  it('filters by title substring (case-insensitive)', () => {
    const result = groupExamplesByCategory(sample, 'A')
    // search 'A' matches 'a' (title) but also accidentally matches 'b' via
    // category 'bar' which contains 'a' — the search hits any of the three
    // columns, so this assertion is intentionally lenient about that.
    expect(result.map((g) => g.items.map((e) => e.id))).toEqual([['a'], ['b']])
  })

  it('filters by category name (case-insensitive)', () => {
    const result = groupExamplesByCategory(sample, 'foo')
    expect(result.map((g) => g.items.map((e) => e.id))).toEqual([['a', 'c']])
  })

  it('returns empty array when no example matches', () => {
    const result = groupExamplesByCategory(sample, 'xyz-no-match')
    expect(result).toEqual([])
  })

  it('falls back to a single "其他" group for examples without category', () => {
    const mixed = [
      { id: 'a', title: 'A', description: '', difficulty: 1, template_type: 'json', category: 'foo' },
      { id: 'b', title: 'B', description: '', difficulty: 1, template_type: 'json', category: '' as any },
      { id: 'c', title: 'C', description: '', difficulty: 1, template_type: 'json', category: undefined as any },
    ]
    const result = groupExamplesByCategory(mixed, '')
    const otherGroup = result.find((g) => g.name === '其他')
    expect(otherGroup).toBeTruthy()
    expect(otherGroup!.items.map((e) => e.id)).toEqual(['b', 'c'])
  })

  it('uses a customizable other-category label', () => {
    const result = groupExamplesByCategory(
      [{ id: 'x', title: 'X', description: '', difficulty: 1, template_type: 'json', category: '' as any }],
      '',
      'misc',
    )
    expect(result[0].name).toBe('misc')
  })
})
