import { tr, locale } from './i18n.js'
import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'collab.token'

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
}
export function setToken(token) {
  try { token ? localStorage.setItem(TOKEN_KEY, token) : localStorage.removeItem(TOKEN_KEY) } catch { /* private mode */ }
}

const api = axios.create({ baseURL: '/api', timeout: 30000 })

api.interceptors.request.use((cfg) => {
  cfg.headers['Accept-Language'] = locale.value
  const t = getToken()
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

api.interceptors.response.use(
  (res) => res.config.fullResponse ? res : res.data,
  async (err) => {
    const status = err.response?.status
    let data = err.response?.data
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      try { data = JSON.parse(await data.text()) } catch { data = null }
    }
    const message = data?.message || tr(err.code === 'ECONNABORTED' ? 'Request timed out' : err.response ? 'Request failed' : 'Network connection failed')
    if (status === 401 && !err.config?.url?.includes('/auth/login')) {
      setToken(null)
      if (!location.pathname.startsWith('/login')) location.href = '/login?expired=1'
    } else if (!err.config?.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(Object.assign(new Error(message), { status, code: data?.code }))
  }
)

/** Downloads a file with the auth header (keeps the token out of URLs). */
export async function download(url, fallbackName) {
  const res = await api.get(url, { responseType: 'blob', fullResponse: true })
  const cd = res.headers['content-disposition'] || ''
  const m = /filename\*=UTF-8''([^;]+)/.exec(cd) || /filename="?([^";]+)"?/.exec(cd)
  const name = m ? decodeURIComponent(m[1]) : fallbackName
  const a = document.createElement('a')
  a.href = URL.createObjectURL(res.data)
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}

export default api
