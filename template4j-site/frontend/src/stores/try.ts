import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTryStore = defineStore('try', () => {
  const templateType = ref<'json' | 'yaml'>('json')
  const template = ref('')
  const data = ref('{}')
  const lastResult = ref<any>(null)
  const lastTiming = ref(0)
  const trace = ref<string[]>([])
  const history = ref<Array<{ ts: number; template: string; data: string; rendered: any }>>([])

  function setTemplate(v: string) { template.value = v }
  function setData(v: string) { data.value = v }
  function setTemplateType(v: 'json' | 'yaml') { templateType.value = v }
  function setResult(result: any, timing: number, t: string[]) {
    lastResult.value = result
    lastTiming.value = timing
    trace.value = t
    history.value.unshift({ ts: Date.now(), template: template.value, data: data.value, rendered: result })
    if (history.value.length > 20) history.value.pop()
  }

  return { templateType, template, data, lastResult, lastTiming, trace, history, setTemplate, setData, setTemplateType, setResult }
})
