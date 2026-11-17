import { ref } from 'vue'

export const THEME_KEY = 'collab-theme'
const choices = ['light', 'dark', 'system']
export const theme = ref('system')
let media

function applyTheme() {
  const dark = theme.value === 'dark' || (theme.value === 'system' && media?.matches)
  document.documentElement.classList.toggle('dark', Boolean(dark))
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
}

export function setTheme(value) {
  theme.value = choices.includes(value) ? value : 'system'
  try { localStorage.setItem(THEME_KEY, theme.value) } catch { /* Storage may be unavailable. */ }
  applyTheme()
}

export function initTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    theme.value = choices.includes(saved) ? saved : 'system'
  } catch { theme.value = 'system' }
  media = window.matchMedia('(prefers-color-scheme: dark)')
  applyTheme()
  media.addEventListener('change', applyTheme)
  return () => media.removeEventListener('change', applyTheme)
}
