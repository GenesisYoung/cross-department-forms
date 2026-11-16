import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed, nextTick } from 'vue'
import zhCn from './messages.zh-CN.json'
import zhTw from './messages.zh-TW.json'
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
    setLocale('zh-CN')
    expect(label.value).toBe('新建表格')
    setLocale('en')
    expect(label.value).toBe('New sheet')
  })

  it.each(['en', 'zh-TW', 'zh-CN'])('remembers %s and safely handles unavailable browser storage', (language) => {
    const setItem = vi.fn()
    vi.stubGlobal('localStorage', { setItem })
    setLocale(language)
    expect(setItem).toHaveBeenCalledWith(LOCALE_KEY, language)
    vi.stubGlobal('localStorage', { setItem: () => { throw new Error('blocked') } })
    expect(() => setLocale('en')).not.toThrow()
    expect(normalizeLocale('unsupported')).toBe('en')
  })

  it.each([['zh-TW', '登入'], ['zh-CN', '登录']])('restores saved %s on loading the application', async (language, signIn) => {
    vi.stubGlobal('localStorage', { getItem: () => language })
    vi.resetModules()
    const fresh = await import('./i18n.js')
    expect(fresh.locale.value).toBe(language)
    expect(fresh.tr('Sign in')).toBe(signIn)
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

  it('covers every existing translation and preserves interpolation placeholders', () => {
    expect(Object.keys(zhCn).sort()).toEqual(Object.keys(zhTw).sort())
    for (const [key, value] of Object.entries(zhCn)) {
      expect(value.trim(), key).not.toBe('')
      expect((value.match(/\{\d+\}/g) ?? []).sort(), key).toEqual((key.match(/\{\d+\}/g) ?? []).sort())
    }
  })

  it('localizes simplified Chinese dates while preserving user data and formulas', () => {
    setLocale('zh-CN')
    expect(timeAgo(new Date().toISOString())).toBe('刚刚')
    expect(timeAgo(new Date(Date.now() - 120000).toISOString())).toBe('2 分钟前')
    expect(show(null)).toBe('（空白）')
    const iso = '2020-10-12T12:00:00Z'
    expect(timeAgo(iso)).toBe(new Date(iso).toLocaleDateString('zh-CN'))
    expect(formatTime(iso)).toBe(new Date(iso).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }))
    expect(tr('History of {0}', ['My English sheet'])).toBe('My English sheet 的历史记录')
    expect(tr('Unknown backend message')).toBe('Unknown backend message')
    expect(tr('Type a value or a formula, e.g. =SUM(E2:E20)')).toContain('=SUM(E2:E20)')
    expect(show('Warehouse')).toBe('Warehouse')
  })

  it('updates the document language and title on each language switch', async () => {
    const document = { documentElement: { lang: '' }, title: '' }
    vi.stubGlobal('document', document)
    vi.resetModules()
    const fresh = await import('./i18n.js')
    for (const language of ['zh-CN', 'zh-TW', 'en']) {
      fresh.setLocale(language)
      await nextTick()
      expect(document.documentElement.lang).toBe(language)
      expect(document.title).toBe(language === 'en' ? 'Collab Dashboard' : language === 'zh-CN' ? '协作工作台' : '協作工作台')
    }
  })
})
