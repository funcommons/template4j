import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import CodeEditor from '../CodeEditor.vue'

// Contract tests for CodeEditor. They are intentionally independent of the
// highlighting backend (hand-written regex vs. the upcoming Shiki rewrite):
// only the textarea, an aria-hidden overlay mirroring its content, and the
// update:modelValue emit are asserted. No highlight class name or color is
// asserted — the overlay's text content must simply keep the source text.

function mountEditor(modelValue: string, placeholder?: string) {
  const props = placeholder === undefined
    ? { modelValue }
    : { modelValue, placeholder }
  return mount(CodeEditor, { props })
}

function textareaOf(wrapper: VueWrapper) {
  return wrapper.find('textarea')
}

// The highlight layer sits behind the textarea and mirrors its content.
// Match by the ce-overlay class family first; fall back to the aria-hidden
// mirror element so the test survives a class rename in the Shiki rewrite.
function overlayOf(wrapper: VueWrapper) {
  const byClass = wrapper.find('[class*="ce-overlay"]')
  if (byClass.exists()) return byClass
  return wrapper.find('[aria-hidden="true"]')
}

describe('CodeEditor', () => {
  it('renders a textarea holding the bound modelValue', () => {
    const wrapper = mountEditor('{"msg": "Hello {@name}!"}')
    const textarea = textareaOf(wrapper)
    expect(textarea.exists()).toBe(true)
    expect((textarea.element as HTMLTextAreaElement).value).toBe(
      '{"msg": "Hello {@name}!"}',
    )
  })

  it('emits update:modelValue with the new text on input', () => {
    const wrapper = mountEditor('')
    textareaOf(wrapper).setValue('{"a":1}')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted![emitted!.length - 1]).toEqual(['{"a":1}'])
  })

  it('never leaks a raw script tag from modelValue into the rendered html', () => {
    // The highlight layer is v-html driven, so whatever renders the content
    // must escape or sanitize it before injection.
    const wrapper = mountEditor('<script>alert(1)</script>')
    expect(wrapper.html()).not.toContain('<script')
  })

  it('renders a highlight overlay whose text keeps the placeholder content', () => {
    const wrapper = mountEditor('{"msg": "Hello {@name}!"}')
    const overlay = overlayOf(wrapper)
    expect(overlay.exists()).toBe(true)
    // textContent must stay contiguous: both the regex and the Shiki
    // tokenizer preserve source text, so no specific span/class is asserted.
    expect(overlay.text()).toContain('{@name}')
  })

  it('forwards the placeholder prop to the textarea', () => {
    const wrapper = mountEditor('', 'Type JSON here')
    expect(textareaOf(wrapper).attributes('placeholder')).toBe('Type JSON here')
  })

  it('does not throw when typing a {@ suggestion trigger', () => {
    // Zero suggestions shown is acceptable; only a crash is a failure.
    const wrapper = mountEditor('')
    expect(() => textareaOf(wrapper).setValue('path{@')).not.toThrow()
  })

  it('mounts without throwing on empty modelValue', () => {
    expect(() => mountEditor('')).not.toThrow()
  })
})
