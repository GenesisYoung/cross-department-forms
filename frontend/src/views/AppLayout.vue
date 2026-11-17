<template>
  <el-container class="shell">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo" @click="$router.push('/')">
        <img src="/favicon.svg" alt="" />
        <span v-if="!collapsed">{{ tr("Collab Dashboard") }}</span>
      </div>
      <el-menu :default-active="activeMenu" router :collapse="collapsed" class="menu">
        <el-menu-item index="/"><el-icon><DataBoard /></el-icon><template #title>{{ tr("Dashboard") }}</template></el-menu-item>
        <el-menu-item index="/sheets"><el-icon><Grid /></el-icon><template #title>{{ tr("Sheets") }}</template></el-menu-item>
        <el-menu-item index="/templates"><el-icon><Files /></el-icon><template #title>{{ tr("Templates") }}</template></el-menu-item>
        <el-menu-item index="/notifications">
          <el-icon><Bell /></el-icon>
          <template #title>
            {{ tr("Notifications") }}
            <el-badge v-if="notes.unread" :value="notes.unread" :max="99" :type="notes.importantUnread ? 'danger' : 'primary'" class="menu-badge" />
          </template>
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin"><el-icon><UserFilled /></el-icon><template #title>{{ tr("Admin") }}</template></el-menu-item>
      </el-menu>
      <div class="collapse" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
      </div>
    </el-aside>
    <el-container class="main-col">
      <el-header class="header" height="52px">
        <div class="crumb">{{ title }}</div>
        <div class="header-right">
          <ThemeSelect />
          <LanguageSelect />
          <el-tooltip :content="live ? tr('Real-time connection active') : tr('Real-time connection lost — reconnecting')">
            <span class="live" :class="{ on: live }"><i></i></span>
          </el-tooltip>
          <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadLatest">
            <template #reference>
              <el-badge :value="notes.unread" :hidden="!notes.unread" :max="99" :type="notes.importantUnread ? 'danger' : 'primary'">
                <el-button circle :icon="Bell" />
              </el-badge>
            </template>
            <div class="pop-head">
              <b>{{ tr("Notifications") }}</b>
              <el-button link type="primary" size="small" :disabled="!notes.unread" @click="notes.markAllRead()">{{ tr("Mark all read") }}</el-button>
            </div>
            <el-empty v-if="!latest.length" :description="tr('Nothing yet')" :image-size="60" />
            <div v-for="n in latest" :key="n.id" class="pop-item" :class="{ unread: !n.readAt, important: n.severity === 'IMPORTANT' }" @click="openNotification(n)">
              <div class="pop-title">{{ n.title }}</div>
              <div class="pop-msg">{{ n.message.split('\n')[0] }}</div>
              <div class="pop-meta">{{ n.actorName }} · {{ timeAgo(n.createdAt) }}</div>
            </div>
            <el-button link type="primary" class="see-all" @click="$router.push('/notifications')">{{ tr("See all") }}</el-button>
          </el-popover>
          <el-dropdown @command="onUser">
            <span class="user">
              <el-avatar :size="28" class="avatar">{{ initials }}</el-avatar>
              <span class="user-text">
                <span>{{ auth.user?.displayName }}</span>
                <small>{{ auth.user?.department?.name }} · {{ roleLabel }}</small>
              </span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password" :icon="Key">{{ tr("Change password") }}</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="SwitchButton" divided>{{ tr("Sign out") }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main" :class="{ flush: $route.name === 'sheet' }">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="pwdOpen" :title="tr('Change password')" width="400px">
      <el-form label-position="top">
        <el-form-item :label="tr('Current password')"><el-input v-model="pwd.currentPassword" type="password" show-password /></el-form-item>
        <el-form-item :label="tr('New password (min 6 characters)')"><el-input v-model="pwd.newPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdOpen = false">{{ tr("Cancel") }}</el-button>
        <el-button type="primary" :disabled="pwd.newPassword.length < 6" @click="changePassword">{{ tr("Change") }}</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import ThemeSelect from '../components/ThemeSelect.vue'
import LanguageSelect from '../components/LanguageSelect.vue'
import { tr } from '../i18n.js'

import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Key, SwitchButton } from '@element-plus/icons-vue'
import api from '../api.js'
import { useAuth } from '../stores/auth.js'
import { useNotifications } from '../stores/notifications.js'
import { onConnectionChange } from '../realtime.js'
import { timeAgo } from '../utils.js'

const auth = useAuth()
const notes = useNotifications()
const route = useRoute()
const router = useRouter()
const collapsed = ref(window.innerWidth < 900)
const live = ref(false)
const latest = ref([])
const pwdOpen = ref(false)
const pwd = reactive({ currentPassword: '', newPassword: '' })

notes.listen(router)
const stop = onConnectionChange((v) => { live.value = v; if (v) notes.refresh() })
onBeforeUnmount(stop)

const activeMenu = computed(() => (route.path.startsWith('/sheets') ? '/sheets' : route.path))
const title = computed(() => ({ dashboard: tr("Dashboard"), sheets: tr("Sheets"), sheet: tr("Sheet"), templates: tr("Templates"), notifications: tr("Notifications"), admin: tr("Administration") }[route.name] ?? ''))
const initials = computed(() => {
  // first letters of the name, ignoring any "(role)" suffix, e.g. "Peter (PMC Planner)" -> "P"
  const name = (auth.user?.displayName ?? '?').replace(/\(.*?\)/g, '').trim() || '?'
  return name.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()
})
const roleLabel = computed(() => ({ ADMIN: tr("Admin"), MANAGER: tr("Manager"), MEMBER: tr("Member") }[auth.user?.role]))

async function loadLatest() {
  latest.value = await api.get('/notifications', { params: { size: 8 } })
}
async function openNotification(n) {
  if (!n.readAt) await notes.markRead(n.id)
  if (n.sheetId) router.push({ name: 'sheet', params: { id: n.sheetId } })
}
function onUser(cmd) {
  if (cmd === 'logout') {
    auth.logout()
    notes.reset()
    router.push('/login')
  } else {
    Object.assign(pwd, { currentPassword: '', newPassword: '' })
    pwdOpen.value = true
  }
}
async function changePassword() {
  await api.post('/auth/password', pwd)
  pwdOpen.value = false
  ElMessage.success(tr("Password changed"))
}
</script>

<style scoped>
.shell { height: 100vh; }
.aside { background: var(--panel); border-right: 1px solid var(--border); display: flex; flex-direction: column; transition: width 0.2s; overflow: hidden; }
.logo { display: flex; align-items: center; gap: 10px; height: 52px; padding: 0 18px; font-weight: 600; color: var(--text-1); cursor: pointer; white-space: nowrap; }
.logo img { width: 26px; height: 26px; }
.menu { border-right: 0; flex: 1; background: transparent; }
.menu :deep(.el-menu-item) { display: flex; align-items: center; }
.menu-badge { margin-left: 8px; display: inline-flex; }
.menu-badge :deep(.el-badge__content) { position: static; transform: none; }
.collapse { padding: 14px 22px; cursor: pointer; color: var(--text-2); border-top: 1px solid var(--border); }
.main-col { min-width: 0; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border); background: var(--panel); padding: 0 16px; }
.crumb { font-weight: 600; color: var(--text-1); }
.header-right { display: flex; align-items: center; gap: 16px; }
.live i { display: inline-block; width: 9px; height: 9px; border-radius: 50%; background: var(--text-3); }
.live.on i { background: var(--ok); box-shadow: 0 0 0 3px var(--ok-soft); }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--text-1); }
.avatar { background: var(--accent); color: #fff; font-size: 12px; }
.user-text { display: flex; flex-direction: column; line-height: 1.2; font-size: 13px; }
.user-text small { color: var(--text-2); font-size: 11px; }
.main { background: var(--page); padding: 20px; overflow: auto; }
.main.flush { padding: 0; overflow: hidden; display: flex; flex-direction: column; }
.pop-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.pop-item { padding: 8px; border-radius: 6px; cursor: pointer; border-left: 3px solid transparent; }
.pop-item:hover { background: var(--hover); }
.pop-item.unread { background: var(--accent-soft); }
.pop-item.important { border-left-color: var(--warn); }
.pop-title { font-size: 13px; font-weight: 600; }
.pop-msg { font-size: 12px; color: var(--text-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pop-meta { font-size: 11px; color: var(--text-3); }
.see-all { margin-top: 6px; }
@media (max-width: 640px) {
  .user-text { display: none; }
  .main { padding: 12px; }
}
</style>
