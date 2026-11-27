<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>{{ tr("Notifications") }}</h2>
        <p>{{ tr("Changes to watched columns arrive as") }} <b>{{ tr("important") }}</b> {{ tr("and stay here until you acknowledge them.") }}</p>
      </div>
      <div class="actions">
        <el-radio-group v-model="filter">
          <el-radio-button value="unread">{{ tr("Unread") }}</el-radio-button>
          <el-radio-button value="important">{{ tr("Important") }}</el-radio-button>
          <el-radio-button value="all">{{ tr("All") }}</el-radio-button>
        </el-radio-group>
        <el-button :disabled="!notes.unread" @click="readAll">{{ tr("Mark all read") }}</el-button>
      </div>
    </div>

    <div class="list" v-loading="loading">
      <el-empty v-if="!loading && !shown.length" :description="tr('You\'re all caught up')" />
      <div v-for="n in shown" :key="n.id" class="item" :class="{ unread: !n.readAt, important: n.severity === 'IMPORTANT' }">
        <el-icon class="icon"><component :is="n.severity === 'IMPORTANT' ? 'WarningFilled' : n.type === 'SHEET_SHARED' ? 'Share' : 'InfoFilled'" /></el-icon>
        <div class="body">
          <div class="title">{{ notificationText(n).title }}</div>
          <pre class="msg">{{ notificationText(n).message }}</pre>
          <div class="meta">
            {{ n.actorName }} · {{ n.actorDepartment }} · {{ formatTime(n.createdAt) }}
            <span v-if="n.readAt"> · {{ n.severity === 'IMPORTANT' ? tr('acknowledged') : tr('read') }} {{ timeAgo(n.readAt) }}</span>
          </div>
        </div>
        <div class="btns">
          <el-button v-if="n.sheetId" size="small" @click="open(n)">{{ tr("Open sheet") }}</el-button>
          <el-button v-if="!n.readAt" size="small" :type="n.severity === 'IMPORTANT' ? 'warning' : 'default'" @click="read(n)">
            {{ n.severity === 'IMPORTANT' ? tr("Acknowledge") : tr("Mark read") }}
          </el-button>
        </div>
      </div>
      <el-button v-if="hasMore" class="more" @click="loadMore">{{ tr("Load more") }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { notificationText } from '../notificationText.js'
import { tr } from '../i18n.js'

import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api.js'
import { useNotifications } from '../stores/notifications.js'
import { formatTime, timeAgo } from '../utils.js'

const notes = useNotifications()
const router = useRouter()
const items = ref([])
const loading = ref(true)
const filter = ref('unread')
const page = ref(0)
const hasMore = ref(false)
const SIZE = 30

async function load(reset = true) {
  if (reset) page.value = 0
  loading.value = true
  try {
    const res = await api.get('/notifications', { params: { unreadOnly: filter.value !== 'all', page: page.value, size: SIZE } })
    items.value = reset ? res : [...items.value, ...res]
    hasMore.value = res.length === SIZE
  } finally {
    loading.value = false
  }
}
const loadMore = () => { page.value++; load(false) }
watch(filter, () => load())
watch(() => notes.version, () => load())
load()

const shown = computed(() => (filter.value === 'important' ? items.value.filter((n) => n.severity === 'IMPORTANT') : items.value))
async function read(n) {
  const updated = await notes.markRead(n.id)
  Object.assign(n, updated)
}
async function readAll() {
  await notes.markAllRead()
}
async function open(n) {
  if (!n.readAt) await read(n)
  router.push({ name: 'sheet', params: { id: n.sheetId } })
}
</script>

<style scoped>
.page { max-width: 1000px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.page-head h2 { margin: 0; font-size: 22px; color: var(--text-1); }
.page-head p { margin: 4px 0 0; color: var(--text-2); }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.list { min-height: 120px; }
.item { display: flex; gap: 12px; background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; margin-bottom: 8px; border-left: 4px solid var(--border); }
.item.unread { border-left-color: var(--accent); }
.item.important.unread { border-left-color: var(--warn); background: var(--warn-soft); }
.icon { font-size: 20px; color: var(--accent); margin-top: 2px; }
.important .icon { color: var(--warn); }
.body { flex: 1; min-width: 0; }
.title { font-weight: 600; color: var(--text-1); }
.msg { font-family: inherit; white-space: pre-wrap; margin: 4px 0; font-size: 13px; color: var(--text-1); }
.meta { font-size: 12px; color: var(--text-3); }
.btns { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; }
.btns .el-button + .el-button { margin-left: 0; }
.more { width: 100%; }
@media (max-width: 640px) { .item { flex-wrap: wrap; } .btns { flex-direction: row; } }
</style>
