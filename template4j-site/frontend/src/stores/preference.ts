import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

export const usePreference = defineStore('preference', () => {
  const theme = ref(localStorage.getItem('theme') || 'light')
  const brand = ref(localStorage.getItem('brand') || 'mchuan')
  const locale = ref(localStorage.getItem('locale') || 'zh-CN')

  function setTheme(v: string) { theme.value = v; localStorage.setItem('theme', v) }
  function setBrand(v: string) { brand.value = v; localStorage.setItem('brand', v) }
  function setLocale(v: string) {
    locale.value = v
    localStorage.setItem('locale', v)
    try {
      const { global } = useI18n()
      global.locale.value = v
    } catch { /* i18n not yet initialized */ }
  }

  return { theme, brand, locale, setTheme, setBrand, setLocale }
})
