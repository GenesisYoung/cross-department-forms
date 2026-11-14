import { defineStore } from 'pinia'
import api, { getToken, setToken } from '../api.js'
import { connectRealtime, disconnectRealtime } from '../realtime.js'

export const useAuth = defineStore('auth', {
  state: () => ({ user: null, departments: [] }),
  getters: {
    loggedIn: (s) => !!s.user,
    isAdmin: (s) => s.user?.role === 'ADMIN',
    canCreate: (s) => s.user && s.user.role !== 'MEMBER',
    deptCode: (s) => s.user?.department?.code
  },
  actions: {
    async login(username, password) {
      const res = await api.post('/auth/login', { username, password })
      setToken(res.token)
      this.user = res.user
      await this.afterLogin()
    },
    async restore() {
      if (!getToken()) return false
      try {
        this.user = await api.get('/auth/me', { silent: true })
        await this.afterLogin()
        return true
      } catch {
        setToken(null)
        return false
      }
    },
    async afterLogin() {
      this.departments = await api.get('/departments')
      connectRealtime()
    },
    logout() {
      setToken(null)
      this.user = null
      disconnectRealtime()
    }
  }
})
