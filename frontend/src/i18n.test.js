import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { locale, LOCALE_KEY, normalizeLocale, setLocale, tr } from './i18n.js'
import { formatTime, show, timeAgo } from './utils.js'

afterEach(() => {
  locale.value = 'en'
  vi.unstubAllGlobals()
})

describe('interface language', () => {
  it('updates computed labels immediately when switching back and forth', () => {
    const label = computed(() => tr('New sheet'))
    expect(label.value).toBe('New sheet')
    setLocale('zh-TW')
    expect(label.value).toBe('新增表格')
    setLocale('en')
    expect(label.value).toBe('New sheet')
  })

  it('remembers the language and safely handles unavailable browser storage', () => {
    const setItem = vi.fn()
    vi.stubGlobal('localStorage', { setItem })
    setLocale('zh-TW')
    expect(setItem).toHaveBeenCalledWith(LOCALE_KEY, 'zh-TW')
    vi.stubGlobal('localStorage', { setItem: () => { throw new Error('blocked') } })
    expect(() => setLocale('en')).not.toThrow()
    expect(normalizeLocale('unsupported')).toBe('en')
  })

  it('restores a saved preference on loading the application', async () => {
    vi.stubGlobal('localStorage', { getItem: () => 'zh-TW' })
    vi.resetModules()
    const fresh = await import('./i18n.js')
    expect(fresh.locale.value).toBe('zh-TW')
    expect(fresh.tr('Sign in')).toBe('登入')
  })

  it('preserves supplied names and values, including formula syntax', () => {
    setLocale('zh-TW')
    expect(tr('History of {0}', ['My English sheet'])).toBe('My English sheet 的歷程')
    expect(tr('Unknown backend message')).toBe('Unknown backend message')
    expect(tr('Type a value or a formula, e.g. =SUM(E2:E20)')).toContain('=SUM(E2:E20)')
    expect(show('Warehouse')).toBe('Warehouse')
    expect(show(0)).toBe(0)
  })

  it('localizes relative dates and empty values', () => {
    setLocale('zh-TW')
    expect(timeAgo(new Date().toISOString())).toBe('剛剛')
    expect(timeAgo(new Date(Date.now() - 120000).toISOString())).toBe('2 分鐘前')
    expect(show(null)).toBe('（空白）')
    const iso = '2026-10-12T12:00:00Z'
    expect(formatTime(iso)).toBe(new Date(iso).toLocaleString('zh-TW', { dateStyle: 'medium', timeStyle: 'short' }))
  })
})
