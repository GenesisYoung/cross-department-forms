import { locale, tr } from './i18n.js'

const DEPT_HUES = { WAREHOUSE: 'teal', PMC: 'violet', PURCHASE: 'amber', IT: 'slate' }
const EXTRA = ['rose', 'sky', 'lime', 'teal', 'violet', 'amber']

export function deptHue(code) {
  if (!code) return 'slate'
  if (DEPT_HUES[code]) return DEPT_HUES[code]
  let h = 0
  for (const ch of code) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return EXTRA[h % EXTRA.length]
}
/** Inline style for a department pill using CSS tokens defined in styles.css. */
export function deptStyle(code) {
  const hue = deptHue(code)
  return { background: `var(--hue-${hue}-bg)`, color: `var(--hue-${hue}-fg)` }
}

export function timeAgo(iso) {
  if (!iso) return ''
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 45) return tr('just now')
  if (s < 3600) return tr('{0} min ago', [Math.round(s / 60)])
  if (s < 86400) return tr('{0} h ago', [Math.round(s / 3600)])
  if (s < 86400 * 7) return tr('{0} d ago', [Math.round(s / 86400)])
  return new Date(iso).toLocaleDateString(locale.value === 'en' ? 'en-US' : locale.value)
}
export function formatTime(iso) {
  return iso ? new Date(iso).toLocaleString(locale.value === 'en' ? 'en-US' : locale.value, { dateStyle: 'medium', timeStyle: 'short' }) : ''
}
export const show = (v) => (v === null || v === undefined || v === '' ? tr('(empty)') : v)
export function deptDot(code) {
  return { background: `var(--hue-${deptHue(code)}-fg)` }
}
