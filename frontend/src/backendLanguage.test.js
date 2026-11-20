import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed, createRenderer, h, nextTick } from 'vue'
import { locale, setLocale } from './i18n.js'
import { notificationText } from './notificationText.js'
import NotificationContent from './components/NotificationContent.js'
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
import { ElMessage } from 'element-plus'
import api, { download } from './api.js'

afterEach(() => { locale.value = 'en'; vi.clearAllMocks(); vi.unstubAllGlobals() })
const translations = {
  en: { title: 'English title', message: 'English sheet: A → B' },
  'zh-CN': { title: '简体标题', message: 'English sheet：A → B' },
  'zh-TW': { title: '繁體標題', message: 'English sheet：A → B' }
}

describe('backend language synchronization', () => {
  it('sends the latest language for every request including blob downloads', async () => {
    for (const language of ['en', 'zh-CN', 'zh-TW', 'en']) {
      setLocale(language)
      await api.get('/notifications', { responseType: 'blob', adapter: async (config) => {
        expect(config.headers['Accept-Language']).toBe(language)
        return { data: [], status: 200, headers: {}, config }
      } })
    }
  })
  it('shows backend errors verbatim and reads download JSON error blobs', async () => {
    setLocale('zh-TW')
    const adapter = async config => Promise.reject({ config, response: {
      status: 403, data: new Blob([JSON.stringify({ code: 'error.denied', message: '沒有存取權限' })])
    } })
    const previous = api.defaults.adapter
    api.defaults.adapter = adapter
    try { await expect(download('/sheets/1/export', 'test.xlsx')).rejects.toMatchObject({ message: '沒有存取權限', status: 403, code: 'error.denied' }) }
    finally { api.defaults.adapter = previous }
    expect(ElMessage.error).toHaveBeenCalledWith('沒有存取權限')
  })
  it('localizes network failures and handles errors without request configuration', async () => {
    setLocale('zh-CN')
    await expect(api.get('/test', { adapter: async () => Promise.reject({ code: 'ECONNABORTED' }) })).rejects.toThrow('请求超时')
    await expect(api.get('/test', { adapter: async () => Promise.reject({}) })).rejects.toThrow('网络连接失败')
  })
  it('switches already loaded notification text and supports older DTOs', () => {
    const note = { title: 'fallback', message: 'legacy content', translations }
    const text = computed(() => notificationText(note))
    for (const language of ['en', 'zh-CN', 'zh-TW', 'en']) {
      setLocale(language)
      expect(text.value).toEqual(translations[language])
    }
    expect(notificationText({ title: 'old', message: 'old body' })).toEqual({ title: 'old', message: 'old body' })
  })
  it('updates an already open toast while retaining user names and values', async () => {
    // A tiny in-memory Vue host exercises actual mounted component reactivity without a browser.
    const renderer = createRenderer({
      createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }),
      insert: (child, parent) => { parent.children.push(child); child.parent = parent },
      remove: child => { child.parent.children = child.parent.children.filter(c => c !== child) },
      setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text; node.children = [] },
      parentNode: node => node.parent, nextSibling: () => null, patchProp: () => {}, setScopeId: () => {}
    })
    const root = { children: [] }
    const note = { translations, actorName: 'Alice', actorDepartment: 'Warehouse' }
    const app = renderer.createApp({ render: () => h(NotificationContent, { notification: note }) })
    const text = node => (node.text ?? '') + (node.children ?? []).map(text).join('')
    app.mount(root)
    for (const language of ['zh-CN', 'zh-TW', 'en']) {
      setLocale(language); await nextTick()
      expect(text(root)).toContain(translations[language].title)
      expect(text(root)).toContain('English sheet')
      expect(text(root)).toContain('Alice (Warehouse)')
    }
    app.unmount()
  })
})
