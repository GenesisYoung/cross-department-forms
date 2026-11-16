import { ref, watchEffect } from 'vue'
import zhTw from './messages.zh-TW.json'
import zhCn from './messages.zh-CN.json'

const messages = { 'zh-TW': zhTw, 'zh-CN': zhCn }

export const LOCALE_KEY = 'collab.locale'
export function normalizeLocale(value) {
  return value === 'zh-TW' || value === 'zh-CN' ? value : 'en'
}
function savedLocale() {
  try { return normalizeLocale(localStorage.getItem(LOCALE_KEY)) } catch { return 'en' }
}
export const locale = ref(savedLocale())
export function setLocale(value) {
  locale.value = normalizeLocale(value)
  try { localStorage.setItem(LOCALE_KEY, locale.value) } catch { /* Storage may be unavailable. */ }
}
/** English source keys keep the fallback readable; interpolate values without translating user data. */
export function tr(key, values = []) {
  const text = messages[locale.value]?.[key] ?? key
  return text.replace(/\{(\d+)\}/g, (match, index) => String(values[index] ?? match))
}
watchEffect(() => {
  if (typeof document === 'undefined') return
  document.documentElement.lang = locale.value
  document.title = tr('Collab Dashboard')
})
