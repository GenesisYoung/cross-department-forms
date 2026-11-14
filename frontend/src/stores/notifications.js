import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import api from '../api.js'
import { subscribe } from '../realtime.js'

export const useNotifications = defineStore('notifications', {
  state: () => ({ unread: 0, importantUnread: 0, latest: [], listening: false, version: 0 }),
  actions: {
    async refresh() {
      const c = await api.get('/notifications/count', { silent: true })
      this.unread = c.unread
      this.importantUnread = c.importantUnread
    },
    listen(router) {
      if (this.listening) return
      this.listening = true
      this.refresh()
      subscribe('/user/queue/notifications', (n) => {
        this.unread++
        if (n.severity === 'IMPORTANT') this.importantUnread++
        this.latest.unshift(n)
        this.latest = this.latest.slice(0, 20)
        this.version++
        const toast = ElNotification({
          title: n.title,
          message: `${n.message.split('\n').slice(0, 3).join('\n')}\n— ${n.actorName ?? ''} (${n.actorDepartment ?? ''})`,
          type: n.severity === 'IMPORTANT' ? 'warning' : 'info',
          duration: n.severity === 'IMPORTANT' ? 0 : 6000,
          customClass: 'notify-toast',
          onClick: () => {
            if (n.sheetId) router.push({ name: 'sheet', params: { id: n.sheetId } })
            toast.close()
          }
        })
      })
    },
    async markRead(id) {
      const n = await api.post(`/notifications/${id}/read`)
      await this.refresh()
      this.version++
      return n
    },
    async markAllRead() {
      await api.post('/notifications/read-all')
      await this.refresh()
      this.version++
    },
    reset() {
      this.$reset()
    }
  }
})
