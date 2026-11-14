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
  if (s < 45) return 'just now'
  if (s < 3600) return Math.round(s / 60) + ' min ago'
  if (s < 86400) return Math.round(s / 3600) + ' h ago'
  if (s < 86400 * 7) return Math.round(s / 86400) + ' d ago'
  return new Date(iso).toLocaleDateString()
}
export function formatTime(iso) {
  return iso ? new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : ''
}
export const show = (v) => (v === null || v === undefined || v === '' ? '(empty)' : v)
export function deptDot(code) {
  return { background: `var(--hue-${deptHue(code)}-fg)` }
}
