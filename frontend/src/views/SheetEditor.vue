<template>
  <div class="editor" v-loading="loading">
    <template v-if="sheet">
      <!-- title row -->
      <div class="title-row">
        <div class="title-left">
          <el-button text :icon="ArrowLeft" @click="$router.push({ name: 'sheets' })" />
          <div>
            <div class="sheet-name">
              {{ sheet.name }}
              <span class="dept-pill" :style="deptStyle(sheet.ownerDepartment.code)">{{ sheet.ownerDepartment.name }}</span>
              <el-tooltip :content="accessHelp" placement="bottom">
                <el-tag size="small" :type="accessTag.type" effect="plain" class="access">{{ accessTag.label }}</el-tag>
              </el-tooltip>
            </div>
            <div class="sheet-sub">{{ sheet.description || tr("No description") }} · v{{ sheet.version }} {{ tr("· updated") }} {{ timeAgo(sheet.updatedAt) }}</div>
          </div>
        </div>
        <div class="title-right">
          <span class="live" :class="{ on: live }"><i></i>{{ live ? tr("Live") : tr("Reconnecting…") }}</span>
          <el-tooltip :content="sheet.subscribed ? tr('You get a notification for every change') : tr('Get notified about every change (watched columns always notify)')">
            <el-button :type="sheet.subscribed ? 'primary' : 'default'" plain :icon="sheet.subscribed ? 'BellFilled' : 'Bell'" @click="toggleSubscribe">
              {{ sheet.subscribed ? tr("Following") : tr("Follow") }}
            </el-button>
          </el-tooltip>
          <el-button :icon="Clock" @click="openHistory()">{{ tr("History") }}</el-button>
          <el-button v-if="isOwner" :icon="Share" @click="shareOpen = true">{{ tr("Share") }}</el-button>
          <el-dropdown trigger="click" @command="onMore">
            <el-button :icon="MoreFilled" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="export" :icon="Download">{{ tr("Export to Excel (.xlsx)") }}</el-dropdown-item>
                <el-dropdown-item command="template" :icon="DocumentCopy" :disabled="!auth.canCreate">{{ tr("Save as template…") }}</el-dropdown-item>
                <el-dropdown-item command="settings" :icon="Setting" :disabled="!isOwner">{{ tr("Sheet settings…") }}</el-dropdown-item>
                <el-dropdown-item command="delete" :icon="Delete" :disabled="!isOwner" divided>{{ tr("Delete sheet") }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- toolbar -->
      <div class="toolbar">
        <el-button-group>
          <el-tooltip :content="tr('Undo (Ctrl+Z)')"><el-button size="small" :icon="RefreshLeft" :disabled="!undoStack.length" @click="undo" /></el-tooltip>
          <el-tooltip :content="tr('Redo (Ctrl+Y)')"><el-button size="small" :icon="RefreshRight" :disabled="!redoStack.length" @click="redo" /></el-tooltip>
        </el-button-group>
        <span class="sep"></span>
        <el-button-group>
          <el-tooltip :content="tr('Bold (Ctrl+B)')"><el-button size="small" :type="activeStyle.b ? 'primary' : 'default'" @click="toggleStyle('b')"><b>B</b></el-button></el-tooltip>
          <el-tooltip :content="tr('Italic (Ctrl+I)')"><el-button size="small" :type="activeStyle.i ? 'primary' : 'default'" @click="toggleStyle('i')"><i>I</i></el-button></el-tooltip>
        </el-button-group>
        <el-button-group>
          <el-button size="small" :type="activeStyle.al === 'left' ? 'primary' : 'default'" @click="setStyle('al', activeStyle.al === 'left' ? null : 'left')" :title="tr('Align left')">⟸</el-button>
          <el-button size="small" :type="activeStyle.al === 'center' ? 'primary' : 'default'" @click="setStyle('al', activeStyle.al === 'center' ? null : 'center')" :title="tr('Center')">≡</el-button>
          <el-button size="small" :type="activeStyle.al === 'right' ? 'primary' : 'default'" @click="setStyle('al', activeStyle.al === 'right' ? null : 'right')" :title="tr('Align right')">⟹</el-button>
        </el-button-group>
        <el-tooltip :content="tr('Fill color')">
          <span class="picker"><el-color-picker size="small" :model-value="activeStyle.bg || null" :predefine="FILLS" @change="(v) => setStyle('bg', v)" /></span>
        </el-tooltip>
        <el-tooltip :content="tr('Text color')">
          <span class="picker"><el-color-picker size="small" :model-value="activeStyle.fc || null" :predefine="TEXTS" @change="(v) => setStyle('fc', v)" /></span>
        </el-tooltip>
        <el-select size="small" class="fmt" :model-value="activeStyle.fmt || ''" @change="(v) => setStyle('fmt', v || null)">
          <el-option v-for="f in FORMATS" :key="f.value" :label="tr(f.label)" :value="f.value" />
        </el-select>
        <span class="sep"></span>
        <el-button size="small" :icon="Plus" :disabled="!canRows" @click="structure('APPEND_ROWS', 0, 50)">{{ tr("50 rows") }}</el-button>
        <el-button size="small" :icon="Plus" :disabled="!isOwner" @click="structure('APPEND_COLS', 0, 1)">{{ tr("Column") }}</el-button>
        <el-button size="small" :icon="Operation" :disabled="!isOwner" @click="openColumn(selection.c)">{{ tr("Column rules") }}</el-button>
        <span class="grow"></span>
        <span class="legend"><el-icon class="watch"><Bell /></el-icon> {{ tr("watched column") }}</span>
        <span class="legend"><el-icon><Lock /></el-icon> {{ tr("column reserved for a department") }}</span>
        <span class="legend"><i class="ro-swatch"></i> {{ tr("read-only for you") }}</span>
      </div>

      <!-- formula bar -->
      <div class="formula-bar">
        <div class="ref">{{ refLabel }}</div>
        <span class="fx">fx</span>
        <el-autocomplete
          ref="fbar"
          v-model="formulaDraft"
          class="fx-input"
          :fetch-suggestions="suggestFunctions"
          :trigger-on-focus="false"
          :disabled="!!activeReadonly"
          :placeholder="activeReadonly ? tr('Read-only: ') + activeReadonly : tr('Type a value or a formula, e.g. =SUM(E2:E20)')"
          @select="onFunctionPick"
          @keydown.enter.prevent="commitFormulaBar"
          @keydown.esc="resetFormulaBar(); grid?.focus()"
        />
      </div>

      <SpreadsheetGrid
        ref="grid"
        :row-count="sheet.rowCount"
        :col-count="sheet.colCount"
        :header-rows="sheet.headerRows"
        :columns="columnsByIndex"
        :get-raw="getRaw"
        :get-style="getStyle"
        :get-display="getDisplay"
        :can-edit="canEdit"
        :flashes="flashes"
        :render-key="renderKey"
        :can-structure="{ rows: canRows, cols: isOwner }"
        @edit="(e) => applyEdits(e)"
        @select="onSelect"
        @resize-col="resizeColumn"
        @action="onGridAction"
      />

      <!-- workbook tabs (one per worksheet of an imported Excel file) -->
      <div v-if="sheet.tabs?.length > 1" class="tab-bar" role="tablist">
        <button
          v-for="t in sheet.tabs"
          :key="t.id"
          type="button"
          role="tab"
          class="tab"
          :class="{ active: t.id === sheet.id }"
          :aria-selected="t.id === sheet.id"
          :title="t.name"
          @click="switchTab(t.id)"
        >{{ t.name }}</button>
      </div>

      <div class="status-bar">
        <span v-if="stats.count > 1">{{ tr("Count:") }} {{ stats.count }}</span>
        <span v-if="stats.nums">{{ tr("Sum:") }} {{ fmt(stats.sum) }}</span>
        <span v-if="stats.nums">{{ tr("Avg:") }} {{ fmt(stats.sum / stats.nums) }}</span>
        <span class="grow"></span>
        <span v-if="saving" class="saving"><el-icon class="is-loading"><Loading /></el-icon> {{ tr("Saving…") }}</span>
        <span v-else-if="lastSaved">{{ tr("All changes saved") }}</span>
        <span v-if="lastRemote" class="remote">{{ tr("Last update by") }} {{ lastRemote.by }} · {{ timeAgo(lastRemote.at) }}</span>
      </div>
    </template>

    <ShareDialog v-if="sheet" v-model="shareOpen" :sheet="sheet" @saved="reload" />
    <ColumnDialog v-if="sheet" v-model="columnOpen" :sheet-id="sheet.id" :col="columnIndex" :column="columnsByIndex[columnIndex]" :header="headerText(columnIndex)" @saved="reload" />
    <HistoryDrawer v-if="sheet" v-model="historyOpen" :sheet-id="sheet.id" :cell="historyCell" @jump="jumpTo" />

    <el-dialog v-model="settingsOpen" :title="tr('Sheet settings')" width="480px">
      <el-form label-position="top">
        <el-form-item :label="tr('Name')"><el-input v-model="settings.name" maxlength="160" /></el-form-item>
        <el-form-item :label="tr('Description')"><el-input v-model="settings.description" type="textarea" maxlength="500" /></el-form-item>
        <el-form-item :label="tr('Header rows (frozen, owner-only)')"><el-input-number v-model="settings.headerRows" :min="0" :max="20" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsOpen = false">{{ tr("Cancel") }}</el-button>
        <el-button type="primary" @click="saveSettings">{{ tr("Save") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateOpen" :title="tr('Save as template')" width="480px">
      <el-form label-position="top">
        <el-form-item :label="tr('Template name')"><el-input v-model="templateForm.name" maxlength="120" /></el-form-item>
        <el-form-item :label="tr('Description')"><el-input v-model="templateForm.description" type="textarea" maxlength="500" /></el-form-item>
        <el-form-item>
          <el-checkbox v-model="templateForm.includeData">{{ tr("Include current data (otherwise only headers, column rules and widths)") }}</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateOpen = false">{{ tr("Cancel") }}</el-button>
        <el-button type="primary" :disabled="!templateForm.name" @click="saveTemplate">{{ tr("Save template") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { tr } from '../i18n.js'

import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Clock, Share, MoreFilled, Download, DocumentCopy, Setting, Delete, RefreshLeft, RefreshRight, Plus, Operation } from '@element-plus/icons-vue'
import api, { download } from '../api.js'
import { subscribe, onConnectionChange, CLIENT_ID } from '../realtime.js'
import { useAuth } from '../stores/auth.js'
import { createEvaluator, displayValue, isError, colName, FUNCTION_NAMES } from '../formula/engine.js'
import { timeAgo, deptStyle } from '../utils.js'
import SpreadsheetGrid from '../components/SpreadsheetGrid.vue'
import ShareDialog from '../components/ShareDialog.vue'
import ColumnDialog from '../components/ColumnDialog.vue'
import HistoryDrawer from '../components/HistoryDrawer.vue'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()
const auth = useAuth()

const FILLS = ['#ffffff', '#fff3c4', '#ffe0cc', '#ffd6d6', '#dff5e1', '#dcebff', '#ece3ff', '#e8eef9', '#f0f0f0']
const TEXTS = ['#1f2329', '#c0392b', '#d35400', '#1e8449', '#1f4e8c', '#7d3c98', '#7f8c8d']
const FORMATS = [
  { label: "General", value: '' }, { label: '0', value: '0' }, { label: '0.00', value: '0.00' },
  { label: '1,234', value: '#,##0' }, { label: '1,234.00', value: '#,##0.00' }, { label: '12%', value: '0%' }, { label: '12.3%', value: '0.0%' }
]

const loading = ref(true)
const sheet = ref(null)
const cells = shallowRef(new Map()) // "r,c" -> { v, s (json string), so (parsed) }
const renderKey = ref(0)
const selection = reactive({ r: 0, c: 0, r1: 0, r2: 0, c1: 0, c2: 0 })
const grid = ref(null)
const fbar = ref(null)
const formulaDraft = ref('')
const flashes = reactive({})
const live = ref(false)
const saving = ref(0)
const lastSaved = ref(false)
const lastRemote = ref(null)
const undoStack = ref([])
const redoStack = ref([])
let lastVersion = 0

const shareOpen = ref(false)
const columnOpen = ref(false)
const columnIndex = ref(0)
const historyOpen = ref(false)
const historyCell = ref(null)
const settingsOpen = ref(false)
const settings = reactive({ name: '', description: '', headerRows: 1 })
const templateOpen = ref(false)
const templateForm = reactive({ name: '', description: '', includeData: false })

// ------------------------------------------------------------------ data access
const key = (r, c) => r + ',' + c
const getRaw = (r, c) => cells.value.get(key(r, c))?.v ?? null
const getStyle = (r, c) => cells.value.get(key(r, c))?.so ?? null
let evaluator = createEvaluator(getRaw)

function getDisplay(r, c) {
  const v = evaluator.value(r, c)
  const s = getStyle(r, c)
  const kind = v === null ? 'empty' : isError(v) ? 'err' : typeof v === 'number' ? 'num' : typeof v === 'boolean' ? 'bool' : 'text'
  return { text: displayValue(v, s?.fmt), kind, value: v }
}

const columnsByIndex = computed(() => {
  const arr = []
  for (const c of sheet.value?.columns ?? []) arr[c.index] = c
  return arr
})
const isOwner = computed(() => sheet.value?.access === 'OWNER')
const canRows = computed(() => ['OWNER', 'EDIT'].includes(sheet.value?.access))

/** Mirrors the server-side rule in PermissionService.denyReason (the server stays authoritative). */
function canEdit(r, c) {
  const s = sheet.value
  if (!s) return tr('loading')
  if (s.access === 'OWNER') return null
  if (r < s.headerRows) return tr('header rows can only be edited by the sheet owner')
  const depts = columnsByIndex.value[c]?.editDepartments ?? []
  if (depts.length) return depts.includes(auth.deptCode) ? null : tr('this column is reserved for ') + depts.join(', ')
  return s.access === 'EDIT' ? null : tr('your department has view-only access')
}

const accessTag = computed(() => ({
  OWNER: { label: tr("Owner"), type: 'success' },
  EDIT: { label: tr("Can edit"), type: 'primary' },
  VIEW: { label: tr("View only"), type: 'info' }
}[sheet.value?.access] ?? { label: '', type: 'info' }))
const accessHelp = computed(() => {
  const own = columnsByIndex.value.filter((c) => c?.editDepartments?.includes(auth.deptCode)).map((c) => c.title || colName(c.index))
  const base = {
    OWNER: tr("You manage this sheet: sharing, column rules, header rows and structure."),
    EDIT: tr("Your department can edit data rows (except columns reserved for other departments)."),
    VIEW: tr("Your department can view this sheet.")
  }[sheet.value?.access] ?? ''
  return own.length ? tr("{0} You can edit: {1}.", [base, own.join(', ')]) : base
})

function refresh() {
  evaluator.invalidate()
  renderKey.value++
}

function loadDetail(detail) {
  const map = new Map()
  for (const cell of detail.cells) map.set(key(cell.r, cell.c), toEntry(cell.v, cell.s))
  cells.value = map
  evaluator = createEvaluator(getRaw)
  sheet.value = detail
  lastVersion = detail.version
  refresh()
  syncFormulaBar()
}
function toEntry(v, s) {
  let so = null
  if (s) { try { so = JSON.parse(s) } catch { so = null } }
  return { v: v ?? null, s: s ?? null, so }
}
const styleString = (so) => {
  if (!so) return null
  const clean = Object.fromEntries(Object.entries(so).filter(([, v]) => v !== null && v !== undefined && v !== false && v !== ''))
  return Object.keys(clean).length ? JSON.stringify(clean) : null
}

async function reload() {
  const detail = await api.get(`/sheets/${props.id}`)
  loadDetail(detail)
}

// ------------------------------------------------------------------ editing
function setLocal(r, c, v, s) {
  const k = key(r, c)
  if ((v === null || v === '') && !s) cells.value.delete(k)
  else cells.value.set(k, toEntry(v === '' ? null : v, s))
}

/**
 * edits: [{ r, c, v, s }] where s may be an object (from the grid) or a JSON string.
 * Applies optimistically, sends to the server, reverts on failure.
 */
async function applyEdits(edits, { record = true } = {}) {
  const s = sheet.value
  if (!s || !edits.length) return
  const norm = edits.map((e) => ({ r: e.r, c: e.c, v: e.v === '' ? null : e.v ?? null, s: typeof e.s === 'string' ? e.s : styleString(e.s) }))
  const before = norm.map((e) => ({ r: e.r, c: e.c, v: getRaw(e.r, e.c), s: cells.value.get(key(e.r, e.c))?.s ?? null }))
  for (const e of norm) setLocal(e.r, e.c, e.v, e.s)
  const maxR = Math.max(...norm.map((e) => e.r)), maxC = Math.max(...norm.map((e) => e.c))
  if (maxR >= s.rowCount) s.rowCount = maxR + 1
  if (maxC >= s.colCount) s.colCount = maxC + 1
  refresh()
  syncFormulaBar()
  if (record) {
    undoStack.value.push({ before, after: norm })
    if (undoStack.value.length > 100) undoStack.value.shift()
    redoStack.value = []
  }
  saving.value++
  try {
    const res = await api.patch(`/sheets/${s.id}/cells`, { clientId: CLIENT_ID, edits: norm })
    lastVersion = Math.max(lastVersion, res.version)
    s.version = Math.max(s.version, res.version)
    s.updatedAt = new Date().toISOString()
    lastSaved.value = true
  } catch {
    for (const b of before) setLocal(b.r, b.c, b.v, b.s)
    if (record) undoStack.value.pop()
    refresh()
    syncFormulaBar()
  } finally {
    saving.value--
  }
}

function undo() {
  const entry = undoStack.value.pop()
  if (!entry) return
  redoStack.value.push(entry)
  applyEdits(entry.before, { record: false })
}
function redo() {
  const entry = redoStack.value.pop()
  if (!entry) return
  undoStack.value.push(entry)
  applyEdits(entry.after, { record: false })
}

// ------------------------------------------------------------------ styles
const activeStyle = computed(() => {
  void renderKey.value
  return getStyle(selection.r, selection.c) || {}
})
function setStyle(prop, value) {
  const edits = []
  let blocked = 0
  for (let r = selection.r1; r <= selection.r2; r++) {
    for (let c = selection.c1; c <= selection.c2; c++) {
      if (canEdit(r, c)) { blocked++; continue }
      const so = { ...(getStyle(r, c) || {}), [prop]: value }
      edits.push({ r, c, v: getRaw(r, c), s: so })
    }
  }
  if (blocked) ElMessage.warning(tr("{0} read-only cell(s) skipped", [blocked]))
  if (edits.length > 20000) return ElMessage.warning(tr("Select a smaller range to format"))
  applyEdits(edits)
  grid.value?.focus()
}
const toggleStyle = (prop) => setStyle(prop, activeStyle.value[prop] ? null : true)

// ------------------------------------------------------------------ selection & formula bar
const refLabel = computed(() => {
  const a = colName(selection.c1) + (selection.r1 + 1)
  if (selection.r1 === selection.r2 && selection.c1 === selection.c2) return colName(selection.c) + (selection.r + 1)
  return a + ':' + colName(selection.c2) + (selection.r2 + 1)
})
const activeReadonly = computed(() => {
  void renderKey.value
  return sheet.value ? canEdit(selection.r, selection.c) : null
})
function onSelect(sel) {
  Object.assign(selection, sel)
  syncFormulaBar()
}
function syncFormulaBar() {
  formulaDraft.value = getRaw(selection.r, selection.c) ?? ''
}
const resetFormulaBar = syncFormulaBar
function commitFormulaBar() {
  if (activeReadonly.value) return
  const raw = getRaw(selection.r, selection.c) ?? ''
  if (formulaDraft.value !== raw) applyEdits([{ r: selection.r, c: selection.c, v: formulaDraft.value, s: getStyle(selection.r, selection.c) }])
  grid.value?.select(selection.r + 1, selection.c)
}
function suggestFunctions(q, cb) {
  const m = /([A-Za-z]+)$/.exec(q)
  if (!q.startsWith('=') || !m) return cb([])
  const prefix = m[1].toUpperCase()
  cb(FUNCTION_NAMES.filter((f) => f.startsWith(prefix)).slice(0, 12).map((f) => ({ value: q.slice(0, q.length - m[1].length) + f + '(' })))
}
function onFunctionPick(item) {
  formulaDraft.value = item.value
}

const stats = computed(() => {
  void renderKey.value
  let count = 0, nums = 0, sum = 0
  const area = (selection.r2 - selection.r1 + 1) * (selection.c2 - selection.c1 + 1)
  if (area > 50000) return { count: 0, nums: 0, sum: 0 }
  for (let r = selection.r1; r <= selection.r2; r++) {
    for (let c = selection.c1; c <= selection.c2; c++) {
      if (!getRaw(r, c)) continue
      count++
      const v = evaluator.value(r, c)
      if (typeof v === 'number') { nums++; sum += v }
    }
  }
  return { count, nums, sum }
})
const fmt = (n) => displayValue(Math.round(n * 1e6) / 1e6, '#,##0.##')

// ------------------------------------------------------------------ structure & columns
async function structure(op, index, count) {
  const detail = await api.post(`/sheets/${sheet.value.id}/structure`, { op, index, count, clientId: CLIENT_ID })
  undoStack.value = []
  redoStack.value = []
  loadDetail(detail)
}
async function resizeColumn(c, width) {
  const col = columnsByIndex.value[c]
  if (col) col.width = width
  else sheet.value.columns.push({ index: c, title: null, width, watched: false, editDepartments: [] })
  if (canRows.value) await api.put(`/sheets/${sheet.value.id}/columns/${c}/width`, { width }, { silent: true }).catch(() => {})
}
function openColumn(c) {
  columnIndex.value = c
  columnOpen.value = true
}
function headerText(c) {
  const hr = sheet.value?.headerRows ?? 0
  return hr > 0 ? getRaw(hr - 1, c) : null
}

async function onGridAction(action, sel) {
  switch (action) {
    case 'undo': return undo()
    case 'redo': return redo()
    case 'bold': return toggleStyle('b')
    case 'italic': return toggleStyle('i')
    case 'history': return openHistory({ r: sel.r, c: sel.c })
    case 'column': return openColumn(sel.c)
    case 'insert-row-above': return structure('INSERT_ROWS', sel.r1, sel.r2 - sel.r1 + 1)
    case 'insert-row-below': return structure('INSERT_ROWS', sel.r2 + 1, sel.r2 - sel.r1 + 1)
    case 'insert-col-left': return structure('INSERT_COLS', sel.c1, sel.c2 - sel.c1 + 1)
    case 'insert-col-right': return structure('INSERT_COLS', sel.c2 + 1, sel.c2 - sel.c1 + 1)
    case 'delete-rows':
      await ElMessageBox.confirm(tr("Delete rows {0}–{1}? Formulas pointing at them become #REF!.", [sel.r1 + 1, sel.r2 + 1]), tr("Delete rows"), { type: 'warning' })
      return structure('DELETE_ROWS', sel.r1, sel.r2 - sel.r1 + 1)
    case 'delete-cols':
      await ElMessageBox.confirm(tr("Delete columns {0}–{1} including their rules?", [colName(sel.c1), colName(sel.c2)]), tr("Delete columns"), { type: 'warning' })
      return structure('DELETE_COLS', sel.c1, sel.c2 - sel.c1 + 1)
  }
}

// ------------------------------------------------------------------ misc actions
function openHistory(cell = null) {
  historyCell.value = cell
  historyOpen.value = true
}
function jumpTo({ r, c }) {
  historyOpen.value = false
  grid.value?.select(r, c)
}
async function toggleSubscribe() {
  const s = sheet.value
  const res = s.subscribed ? await api.delete(`/sheets/${s.id}/subscription`) : await api.put(`/sheets/${s.id}/subscription`)
  s.subscribed = res.subscribed
  ElMessage.success(res.subscribed ? tr("You will be notified about every change") : tr("Unfollowed (watched columns still notify you)"))
}
async function onMore(cmd) {
  const s = sheet.value
  if (cmd === 'export') return download(`/sheets/${s.id}/export`, s.name + '.xlsx')
  if (cmd === 'template') {
    Object.assign(templateForm, { name: s.name + tr(' template'), description: s.description || '', includeData: false })
    templateOpen.value = true
  }
  if (cmd === 'settings') {
    Object.assign(settings, { name: s.name, description: s.description || '', headerRows: s.headerRows })
    settingsOpen.value = true
  }
  if (cmd === 'delete') {
    const msg = s.tabs?.length > 1
      ? tr("Delete \"{0}\" with all {1} tabs and their change history? This cannot be undone.", [s.name, s.tabs.length])
      : tr("Delete \"{0}\" and its whole change history? This cannot be undone.", [s.name])
    await ElMessageBox.confirm(msg, tr("Delete sheet"), { type: 'warning', confirmButtonText: tr("Delete") })
    await api.delete(`/sheets/${s.id}`)
    ElMessage.success(tr("Sheet deleted"))
    router.push({ name: 'sheets' })
  }
}
function switchTab(id) {
  if (id === sheet.value?.id) return
  router.push({ name: 'sheet', params: { id: String(id) } })
}
async function saveSettings() {
  const detail = await api.put(`/sheets/${sheet.value.id}`, settings)
  loadDetail(detail)
  settingsOpen.value = false
}
async function saveTemplate() {
  await api.post(`/sheets/${sheet.value.id}/save-as-template`, templateForm)
  templateOpen.value = false
  ElMessage.success(tr("Template saved"))
}

// ------------------------------------------------------------------ realtime
let unsubscribeSheet = null
let reloadTimer = null
function scheduleReload() {
  clearTimeout(reloadTimer)
  reloadTimer = setTimeout(() => { if (!saving.value) reload(); else scheduleReload() }, 300)
}
function onSheetEvent(e) {
  const mine = e.clientId === CLIENT_ID
  if (e.type === 'SHEET_DELETED') {
    if (!mine) { ElMessage.warning(tr("\"{0}\" was deleted by {1}", [e.sheetName, e.actorName])); router.push({ name: 'sheets' }) }
    return
  }
  if (e.type === 'CELLS_UPDATED') {
    const gap = e.version > lastVersion + 1 && !saving.value
    lastVersion = Math.max(lastVersion, e.version)
    if (mine) return
    if (gap) return scheduleReload()
    for (const ch of e.changes) {
      setLocal(ch.r, ch.c, ch.v, ch.s)
      if (ch.r >= sheet.value.rowCount) sheet.value.rowCount = ch.r + 1
      if (ch.c >= sheet.value.colCount) sheet.value.colCount = ch.c + 1
      const k = key(ch.r, ch.c)
      flashes[k] = { by: `${e.actorName} (${e.actorDepartmentName})` }
      setTimeout(() => delete flashes[k], 2600)
    }
    sheet.value.version = Math.max(sheet.value.version, e.version)
    sheet.value.updatedAt = e.at
    lastRemote.value = { by: `${e.actorName} (${e.actorDepartmentName})`, at: e.at }
    if (grid.value?.isEditing?.() === false) syncFormulaBar()
    refresh()
    return
  }
  // structure / meta / sharing changes: reload authoritative state
  if (!mine) {
    if (e.type === 'STRUCTURE_CHANGED') {
      undoStack.value = []
      redoStack.value = []
      ElMessage.info(tr("{0} changed the sheet layout", [e.actorName]))
    }
    scheduleReload()
  }
}

const stopConn = onConnectionChange((v) => {
  const wasLive = live.value
  live.value = v
  if (v && !wasLive && sheet.value) scheduleReload() // catch up after a disconnect
})

async function open(id) {
  loading.value = true
  unsubscribeSheet?.()
  undoStack.value = []
  redoStack.value = []
  try {
    const detail = await api.get(`/sheets/${id}`)
    loadDetail(detail)
    unsubscribeSheet = subscribe(`/topic/sheets/${id}`, onSheetEvent)
    const q = router.currentRoute.value.query
    if (q.r !== undefined) setTimeout(() => grid.value?.select(+q.r, +(q.c ?? 0)), 50)
  } catch {
    router.push({ name: 'sheets' })
  } finally {
    loading.value = false
  }
}
watch(() => props.id, open, { immediate: true })

onBeforeUnmount(() => {
  unsubscribeSheet?.()
  stopConn()
  clearTimeout(reloadTimer)
})
</script>

<style scoped>
.editor { display: flex; flex-direction: column; height: 100%; min-height: 0; background: var(--panel); }
.title-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 16px 6px; gap: 12px; flex-wrap: wrap; }
.title-left { display: flex; align-items: center; gap: 6px; min-width: 0; }
.title-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sheet-name { font-size: 17px; font-weight: 600; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sheet-sub { color: var(--text-2); font-size: 12px; margin-top: 2px; }
.access { cursor: help; }
.toolbar { display: flex; align-items: center; gap: 6px; padding: 6px 16px; border-top: 1px solid var(--border); flex-wrap: wrap; }
.toolbar .sep { width: 1px; height: 20px; background: var(--border); margin: 0 4px; }
.toolbar .grow, .status-bar .grow { flex: 1; }
.fmt { width: 110px; }
.picker { display: inline-flex; }
.legend { font-size: 12px; color: var(--text-2); display: inline-flex; align-items: center; gap: 4px; margin-left: 8px; }
.legend .watch { color: var(--warn); }
.ro-swatch { display: inline-block; width: 12px; height: 12px; border: 1px solid var(--grid-line); background: var(--ro-tint); }
.formula-bar { display: flex; align-items: center; gap: 8px; padding: 6px 16px; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.formula-bar .ref { width: 96px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; padding: 4px 8px; border: 1px solid var(--border); border-radius: 4px; text-align: center; }
.formula-bar .fx { font-style: italic; color: var(--text-2); font-weight: 600; }
.fx-input { flex: 1; }
.fx-input :deep(input) { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.tab-bar { display: flex; gap: 2px; padding: 0 16px; border-top: 1px solid var(--border); background: var(--grid-head); overflow-x: auto; scrollbar-width: thin; }
.tab-bar .tab { flex: none; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 5px 14px; font: inherit; font-size: 12px; color: var(--text-2); background: transparent; border: 1px solid transparent; border-top: none; border-radius: 0 0 6px 6px; cursor: pointer; }
.tab-bar .tab:hover { background: var(--hover); color: var(--text-1); }
.tab-bar .tab.active { background: var(--panel); color: var(--text-1); font-weight: 600; border-color: var(--border); box-shadow: inset 0 2px 0 var(--el-color-primary); }
.status-bar { display: flex; gap: 16px; padding: 4px 16px; font-size: 12px; color: var(--text-2); border-top: 1px solid var(--border); background: var(--grid-head); }
.status-bar .remote { color: var(--warn-text); }
.live { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-2); margin-right: 4px; }
.live i { width: 8px; height: 8px; border-radius: 50%; background: var(--text-3); }
.live.on i { background: var(--ok); box-shadow: 0 0 0 3px var(--ok-soft); }
</style>
