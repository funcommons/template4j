import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RenderedMarkdown from '../RenderedMarkdown.vue'

// Contract tests for the markdown-it + DOMPurify rewrite of RenderedMarkdown.
// The component's public API must stay a single `content: string` prop, with
// `wrapper.html()` exposing sanitized markdown output.

function mountWith(content: string) {
  return mount(RenderedMarkdown, { props: { content } })
}

describe('RenderedMarkdown', () => {
  it('renders an ATX heading into an h1 element', () => {
    const html = mountWith('# 标题').html()
    expect(html).toContain('<h1>标题</h1>')
  })

  it('renders inline backticks into a code element', () => {
    const html = mountWith('use `code` here').html()
    expect(html).toContain('<code>code</code>')
  })

  it('renders links with the https href and a noopener rel', () => {
    const html = mountWith('[text](https://example.com)').html()
    // markdown-it may normalize the URL with a trailing slash, so only
    // assert the href prefix here.
    expect(html).toContain('<a href="https://example.com')
    expect(html).toMatch(/rel="noopener( noreferrer)?"/)
  })

  it('strips the onerror handler from injected img tags', () => {
    const html = mountWith('<img src=x onerror="alert(1)">').html()
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('alert(1)')
  })

  it('drops script tags entirely', () => {
    const html = mountWith('<script>alert(1)</script>').html()
    expect(html).not.toContain('<script')
  })

  it('neutralizes javascript: hrefs from markdown links', () => {
    const html = mountWith('[click](javascript:alert(1))').html()
    expect(html).not.toContain('href="javascript:')
  })

  it('neutralizes javascript: hrefs from raw html anchors', () => {
    const html = mountWith('<a href="javascript:alert(1)">click</a>').html()
    expect(html).not.toContain('href="javascript:')
  })

  it('renders unordered list items into li elements', () => {
    const html = mountWith('- item').html()
    expect(html).toContain('<li>item</li>')
  })

  it('mounts without throwing on empty content', () => {
    expect(() => mountWith('')).not.toThrow()
  })
})
