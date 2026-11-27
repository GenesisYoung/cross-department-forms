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
        <el-button-group>
          <el-tooltip :content="tr('Increase decimal places')"><el-button size="small" class="dec" @click="stepDecimals(1)">+.0</el-button></el-tooltip>
          <el-tooltip :content="tr('Decrease decimal places')"><el-button size="small" class="dec" @click="stepDecimals(-1)">−.0</el-button></el-tooltip>
        </el-button-group>
        <span class="sep"></span>
        <el-tooltip :content="tr('AutoSum — insert =SUM() for the selected cells')"><el-button size="small" class="sigma" @click="autoSum">Σ</el-button></el-tooltip>
        <span class="sep"></span>
        <el-dropdown trigger="click" @command="onMerge">
          <el-button size="small" :type="selMerged ? 'primary' : 'default'">{{ tr("Merge") }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="merge-center">{{ tr("Merge & center") }}</el-dropdown-item>
              <el-dropdown-item command="merge">{{ tr("Merge cells") }}</el-dropdown-item>
              <el-dropdown-item command="unmerge" divided :disabled="!selMerged">{{ tr("Unmerge cells") }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-tooltip :content="tr('Clear formatting')"><el-button size="small" :icon="Brush" @click="clearFormatting" /></el-tooltip>
        <el-tooltip :content="tr('Find & replace (Ctrl+F)')"><el-button size="small" :icon="Search" @click="openFind" /></el-tooltip>
        <span class="sep"></span>
        <el-dropdown trigger="click" @command="onSort">
          <el-button size="small" :icon="Sort">{{ tr("Sort") }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="asc">{{ tr("Sort A → Z (by column {0})", [colName(selection.c)]) }}</el-dropdown-item>
              <el-dropdown-item command="desc">{{ tr("Sort Z → A (by column {0})", [colName(selection.c)]) }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-tooltip :content="tr('Filter rows')"><el-button size="small" :type="activeFilters.length ? 'primary' : 'default'" :icon="Filter" @click="openFilter">{{ activeFilters.length ? tr("Filtered ({0})", [activeFilters.length]) : tr("Filter") }}</el-button></el-tooltip>
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
        :merges="merges"
        :merge-at="mergeAt"
        :hidden-rows="hiddenRows"
        @edit="(e) => applyEdits(e)"
        @select="onSelect"
        @resize-col="resizeColumn"
        @action="onGridAction"
      />

      <!-- find & replace -->
      <div v-if="findOpen" class="find-panel" @keydown.esc.stop="closeFind">
        <div class="find-row">
          <el-input ref="findInput" v-model="findText" size="small" :placeholder="tr('Find')" clearable
                    @keydown.enter.prevent="findNext" />
          <span class="find-count">{{ matches.length ? (matchIndex + 1 || 1) + '/' + matches.length : tr('0 results') }}</span>
          <el-button-group>
            <el-button size="small" :icon="Back" :disabled="!matches.length" @click="findPrev" />
            <el-button size="small" :icon="Right" :disabled="!matches.length" @click="findNext" />
          </el-button-group>
          <el-button size="small" text :icon="Close" @click="closeFind" />
        </div>
        <div class="find-row">
          <el-input v-model="replaceText" size="small" :placeholder="tr('Replace with')"
                    @keydown.enter.prevent="replaceCurrent" />
          <el-button size="small" @click="replaceCurrent">{{ tr("Replace") }}</el-button>
          <el-button size="small" @click="replaceAllMatches">{{ tr("Replace all") }}</el-button>
        </div>
        <el-checkbox v-model="findCase" size="small">{{ tr("Match case") }}</el-checkbox>
      </div>

      <!-- filter rows (view-only: hides non-matching rows locally, like an AutoFilter) -->
      <div v-if="filterOpen" class="filter-panel" @keydown.esc.stop="closeFilter">
        <div class="filter-head">
          <span class="filter-title">{{ tr("Filter · column {0}", [colName(filterCol)]) }}<span v-if="headerText(filterCol)" class="filter-hdr">{{ headerText(filterCol) }}</span></span>
          <el-button size="small" text :icon="Close" @click="closeFilter" />
        </div>
        <div class="filter-row">
          <el-select v-model="filterForm.op" size="small" class="filter-op">
            <el-option v-for="o in FILTER_OPS" :key="o.value" :label="tr(o.label)" :value="o.value" />
          </el-select>
          <el-input v-if="opNeedsValue(filterForm.op)" v-model="filterForm.val" size="small" :placeholder="tr('Value')"
                    clearable @keydown.enter.prevent="applyFilter" />
        </div>
        <div class="filter-row">
          <el-button size="small" type="primary" @click="applyFilter">{{ tr("Apply") }}</el-button>
          <el-button size="small" :disabled="!filters[filterCol]" @click="clearFilterCol(filterCol)">{{ tr("Clear this column") }}</el-button>
        </div>
        <div v-if="filterSummary.length" class="filter-active">
          <el-tag v-for="f in filterSummary" :key="f.c" size="small" closable type="info" @close="clearFilterCol(f.c)">{{ f.text }}</el-tag>
          <el-button size="small" text type="danger" @click="clearAllFilters">{{ tr("Clear all") }}</el-button>
        </div>
      </div>

      <!-- workbook tabs (one per worksheet; add your own or from an imported Excel file) -->
      <div v-if="showTabBar" class="tab-bar" role="tablist">
        <div
          v-for="t in displayTabs"
          :key="t.id"
          role="tab"
          class="tab"
          :class="{ active: t.id === sheet.id }"
          :aria-selected="t.id === sheet.id"
          :title="t.name"
          @click="switchTab(t.id)"
          @dblclick="renameTab(t)"
        >
          <span class="tab-label">{{ t.name }}</span>
          <el-icon v-if="canManageTabs && t.id === sheet.id && displayTabs.length > 1" class="tab-close"
                   :title="tr('Delete worksheet')" @click.stop="deleteTab(t)"><Close /></el-icon>
        </div>
        <button v-if="canManageTabs" type="button" class="tab-add" :title="tr('Add worksheet')" @click="addTab">
          <el-icon><Plus /></el-icon>
        </button>
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

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Clock, Share, MoreFilled, Download, DocumentCopy, Setting, Delete, RefreshLeft, RefreshRight, Plus, Operation, ArrowDown, Brush, Search, Close, Right, Back, Sort, Filter } from '@element-plus/icons-vue'
import api, { download } from '../api.js'
import { subscribe, onConnectionChange, CLIENT_ID } from '../realtime.js'
import { useAuth } from '../stores/auth.js'
import { createEvaluator, displayValue, isError, colName, cellRef, FUNCTION_NAMES } from '../formula/engine.js'
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
const findInput = ref(null)
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

// ------------------------------------------------------------------ merged cells
// A merge is stored on the top-left ("anchor") cell's style as m: [rowspan, colspan]; the
// cells it covers are cleared. The whole thing rides on the opaque style JSON — no schema change.
const merges = computed(() => {
  void renderKey.value
  const list = []
  for (const [k, cell] of cells.value) {
    const m = cell.so?.m
    if (Array.isArray(m) && m.length === 2) {
      const rs = Math.max(1, m[0] | 0), cs = Math.max(1, m[1] | 0)
      if (rs > 1 || cs > 1) {
        const [r, c] = k.split(',').map(Number)
        list.push({ r, c, rs, cs })
      }
    }
  }
  return list
})
const mergeCover = computed(() => {
  const map = new Map() // "r,c" -> anchor region, for every cell a region covers (anchor included)
  for (const a of merges.value) {
    for (let r = a.r; r < a.r + a.rs; r++) for (let c = a.c; c < a.c + a.cs; c++) map.set(r + ',' + c, a)
  }
  return map
})
function mergeAt(r, c) { return mergeCover.value.get(r + ',' + c) || null }
function isCovered(r, c) { const a = mergeAt(r, c); return !!a && !(a.r === r && a.c === c) }

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
async function applyEdits(edits, { record = true, pin = true } = {}) {
  const s = sheet.value
  if (!s || !edits.length) return
  const norm = edits.map((e) => ({ r: e.r, c: e.c, v: e.v === '' ? null : e.v ?? null, s: typeof e.s === 'string' ? e.s : styleString(e.s) }))
  const before = norm.map((e) => ({ r: e.r, c: e.c, v: getRaw(e.r, e.c), s: cells.value.get(key(e.r, e.c))?.s ?? null }))
  for (const e of norm) setLocal(e.r, e.c, e.v, e.s)
  if (pin) pinRows(norm.map((e) => e.r)) // a row being filled in mustn't vanish mid-edit under a filter
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
      if (isCovered(r, c)) continue // styling follows the merge anchor, not its covered cells
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

/** Adds or removes one decimal place on the active number format of every selected cell. */
function stepDecimals(delta) {
  const edits = []
  let blocked = 0
  for (let r = selection.r1; r <= selection.r2; r++) {
    for (let c = selection.c1; c <= selection.c2; c++) {
      if (isCovered(r, c)) continue
      if (canEdit(r, c)) { blocked++; continue }
      const so = getStyle(r, c) || {}
      const fmt = adjustDecimals(so.fmt || '', delta)
      edits.push({ r, c, v: getRaw(r, c), s: { ...so, fmt: fmt || null } })
    }
  }
  if (blocked) ElMessage.warning(tr("{0} read-only cell(s) skipped", [blocked]))
  if (edits.length) applyEdits(edits)
  grid.value?.focus()
}
function adjustDecimals(fmt, delta) {
  const pct = fmt.endsWith('%')
  const core = pct ? fmt.slice(0, -1) : fmt
  const grouping = core.includes(',')
  const current = core.includes('.') ? core.split('.')[1].length : 0
  const next = Math.max(0, Math.min(10, current + delta))
  const base = grouping ? '#,##0' : '0'
  const out = base + (next > 0 ? '.' + '0'.repeat(next) : '')
  return pct ? out + '%' : out
}

function clearFormatting() {
  const edits = []
  let blocked = 0
  for (let r = selection.r1; r <= selection.r2; r++) {
    for (let c = selection.c1; c <= selection.c2; c++) {
      if (isCovered(r, c)) continue
      if (canEdit(r, c)) { blocked++; continue }
      if (getStyle(r, c)) edits.push({ r, c, v: getRaw(r, c), s: null })
    }
  }
  if (blocked) ElMessage.warning(tr("{0} read-only cell(s) skipped", [blocked]))
  if (edits.length) applyEdits(edits)
  grid.value?.focus()
}

// ------------------------------------------------------------------ merge / unmerge
function mergeSelection(center = false) {
  // Union the selection with any merge it overlaps, so partial selections still merge cleanly.
  let { r1, r2, c1, c2 } = selection
  for (let i = 0; i < 20; i++) {
    let grew = false
    for (const a of merges.value) {
      if (a.r <= r2 && a.r + a.rs - 1 >= r1 && a.c <= c2 && a.c + a.cs - 1 >= c1) {
        if (a.r < r1) { r1 = a.r; grew = true }
        if (a.r + a.rs - 1 > r2) { r2 = a.r + a.rs - 1; grew = true }
        if (a.c < c1) { c1 = a.c; grew = true }
        if (a.c + a.cs - 1 > c2) { c2 = a.c + a.cs - 1; grew = true }
      }
    }
    if (!grew) break
  }
  const rs = r2 - r1 + 1, cs = c2 - c1 + 1
  if (rs < 2 && cs < 2) return
  for (let r = r1; r <= r2; r++) for (let c = c1; c <= c2; c++) {
    if (canEdit(r, c)) return ElMessage.warning(tr("Can't merge: the range contains read-only cells"))
  }
  const covered = []
  for (let r = r1; r <= r2; r++) for (let c = c1; c <= c2; c++) {
    if (r === r1 && c === c1) continue
    if (getRaw(r, c) !== null || getStyle(r, c)) covered.push([r, c])
  }
  const finish = () => {
    const edits = []
    const anchorStyle = { ...(getStyle(r1, c1) || {}), m: [rs, cs] }
    if (center) anchorStyle.al = 'center'
    edits.push({ r: r1, c: c1, v: getRaw(r1, c1), s: anchorStyle })
    for (const [r, c] of covered) edits.push({ r, c, v: null, s: null })
    applyEdits(edits)
    grid.value?.select(r1, c1)
  }
  const discards = covered.filter(([r, c]) => getRaw(r, c) !== null).length
  if (discards) {
    ElMessageBox.confirm(tr("Merging keeps only the top-left value and clears {0} other cell(s). Continue?", [discards]),
      tr("Merge cells"), { type: 'warning' }).then(finish).catch(() => {})
  } else finish()
}
function unmergeSelection() {
  const edits = []
  const seen = new Set()
  for (let r = selection.r1; r <= selection.r2; r++) {
    for (let c = selection.c1; c <= selection.c2; c++) {
      const a = mergeAt(r, c)
      if (!a || seen.has(a.r + ',' + a.c)) continue
      seen.add(a.r + ',' + a.c)
      if (canEdit(a.r, a.c)) continue
      const s = { ...(getStyle(a.r, a.c) || {}) }
      delete s.m
      edits.push({ r: a.r, c: a.c, v: getRaw(a.r, a.c), s })
    }
  }
  if (edits.length) applyEdits(edits)
  grid.value?.focus()
}
const selMerged = computed(() => { void renderKey.value; return !!mergeAt(selection.r, selection.c) })
function onMerge(cmd) {
  if (cmd === 'unmerge') return unmergeSelection()
  mergeSelection(cmd === 'merge-center')
}

// ------------------------------------------------------------------ sort (order)
// The extent of actual data, so a single-cell selection can "sort the whole table".
const usedBounds = computed(() => {
  void renderKey.value
  let maxRow = sheet.value?.headerRows ?? 0, maxCol = 0
  for (const k of cells.value.keys()) {
    const i = k.indexOf(',')
    const r = +k.slice(0, i), c = +k.slice(i + 1)
    if (r > maxRow) maxRow = r
    if (c > maxCol) maxCol = c
  }
  return { maxRow, maxCol }
})

/** Comparable key for a cell: numbers sort numerically, everything else as text; blanks always last. */
function sortKey(r, c) {
  const d = getDisplay(r, c)
  const v = d.value
  if (v === null || v === undefined || v === '') return { blank: true }
  if (typeof v === 'number') return { blank: false, n: v }
  return { blank: false, s: String(d.text ?? v) }
}
function compareKeys(a, b, desc) {
  if (a.blank && b.blank) return 0
  if (a.blank) return 1 // blanks sink to the bottom regardless of direction
  if (b.blank) return -1
  let r
  if (a.n != null && b.n != null) r = a.n - b.n
  else r = String(a.n ?? a.s).localeCompare(String(b.n ?? b.s), undefined, { numeric: true, sensitivity: 'base' })
  return desc ? -r : r
}

/** Reorders rows within the selected block by the active column (destructive, persisted, undoable). */
function sortRange(desc) {
  const s = sheet.value
  if (!s) return
  const hr = s.headerRows
  let { r1, r2, c1, c2 } = selection
  if (r1 === r2) { // single row/cell selected → sort the whole used table by this column
    const b = usedBounds.value
    r1 = hr; r2 = b.maxRow; c1 = 0; c2 = b.maxCol
  }
  if (r1 < hr) r1 = hr // never reorder frozen header rows
  if (r2 <= r1) return ElMessage.info(tr("Select at least two rows to sort"))
  const keyCol = Math.min(Math.max(selection.c, c1), c2)
  // a merge inside the band would be torn apart by reordering — refuse rather than corrupt it
  for (const m of merges.value) {
    if (m.r <= r2 && m.r + m.rs - 1 >= r1 && m.c <= c2 && m.c + m.cs - 1 >= c1) return ElMessage.warning(tr("Can't sort a range that contains merged cells"))
  }
  for (let r = r1; r <= r2; r++) for (let c = c1; c <= c2; c++) if (canEdit(r, c)) return ElMessage.warning(tr("Can't sort: the range contains read-only cells"))
  if ((r2 - r1 + 1) * (c2 - c1 + 1) > 20000) return ElMessage.warning(tr("Select a smaller range to sort"))

  const rows = []
  for (let r = r1; r <= r2; r++) {
    const line = []
    for (let c = c1; c <= c2; c++) line.push({ v: getRaw(r, c), s: cells.value.get(key(r, c))?.s ?? null })
    rows.push({ line, key: sortKey(r, keyCol) })
  }
  const order = rows.map((_, i) => i).sort((a, b) => compareKeys(rows[a].key, rows[b].key, desc) || (a - b))
  if (order.every((v, i) => v === i)) return ElMessage.info(tr("Already sorted"))
  const edits = []
  for (let i = 0; i < order.length; i++) {
    const src = rows[order[i]].line
    const r = r1 + i
    for (let j = 0; j < src.length; j++) {
      const c = c1 + j
      const curV = getRaw(r, c), curS = cells.value.get(key(r, c))?.s ?? null
      if (src[j].v !== curV || src[j].s !== curS) edits.push({ r, c, v: src[j].v, s: src[j].s })
    }
  }
  if (edits.length) { applyEdits(edits, { pin: false }); ElMessage.success(tr("Sorted {0} rows by column {1}", [order.length, colName(keyCol)])) }
  grid.value?.focus()
}
const onSort = (cmd) => sortRange(cmd === 'desc')

// ------------------------------------------------------------------ filter (view-only)
// Each entry filters one column; rows that fail any active condition are hidden locally (not
// persisted, not shared) — the grid collapses them out of view, like an Excel AutoFilter.
const filters = reactive({}) // colIndex -> { op, val }
const filterOpen = ref(false)
const filterCol = ref(0)
const filterForm = reactive({ op: 'contains', val: '' })
const FILTER_OPS = [
  { value: 'contains', label: 'contains' }, { value: 'eq', label: 'equals' }, { value: 'neq', label: 'does not equal' },
  { value: 'gt', label: 'greater than' }, { value: 'gte', label: 'greater or equal' },
  { value: 'lt', label: 'less than' }, { value: 'lte', label: 'less or equal' },
  { value: 'empty', label: 'is empty' }, { value: 'notempty', label: 'is not empty' }
]
const opNeedsValue = (op) => !['empty', 'notempty'].includes(op)
const activeFilters = computed(() => Object.entries(filters)
  .filter(([, f]) => f && f.op && (!opNeedsValue(f.op) || (f.val ?? '') !== ''))
  .map(([c, f]) => ({ c: Number(c), op: f.op, val: f.val })))

function matchFilter(r, f) {
  const d = getDisplay(r, f.c)
  const v = d.value
  const empty = v === null || v === undefined || v === ''
  switch (f.op) {
    case 'empty': return empty
    case 'notempty': return !empty
    case 'contains': return String(d.text ?? '').toLowerCase().includes(String(f.val).toLowerCase())
    case 'eq': case 'neq': {
      const want = f.val
      let eq
      if (typeof v === 'number' && want !== '' && !isNaN(Number(want))) eq = v === Number(want)
      else eq = String(d.text ?? '').toLowerCase() === String(want).toLowerCase()
      return f.op === 'eq' ? eq : !eq
    }
    case 'gt': case 'gte': case 'lt': case 'lte': {
      const n = typeof v === 'number' ? v : Number(v)
      const w = Number(f.val)
      if (isNaN(n) || isNaN(w)) return false
      return f.op === 'gt' ? n > w : f.op === 'gte' ? n >= w : f.op === 'lt' ? n < w : n <= w
    }
    default: return true
  }
}
// Rows that stay visible under the current filter even though they don't match it: rows
// added or typed into while the filter is on (otherwise a blank new row vanishes the moment
// it's created, so you can't fill it in). Like Excel, they stay until the filter is changed.
const pinnedRows = ref(new Set())
const pinRows = (rows) => {
  if (!activeFilters.value.length) return
  const next = new Set(pinnedRows.value)
  for (const r of rows) next.add(r)
  pinnedRows.value = next
}
/** Keeps pinned row indices aligned after a local row insert/delete. */
function shiftPinned(op, index, count) {
  if (!pinnedRows.value.size) return
  const next = new Set()
  for (const r of pinnedRows.value) {
    if (op === 'INSERT_ROWS') next.add(r >= index ? r + count : r)
    else if (op === 'DELETE_ROWS') { if (r < index) next.add(r); else if (r >= index + count) next.add(r - count) }
    else next.add(r)
  }
  pinnedRows.value = next
}
watch(activeFilters, () => { pinnedRows.value = new Set() })

// Totals rows (what AutoSum writes): a row holding a SUM/SUBTOTAL over a multi-row range that
// ends above it. These summarise the data and are never filtered out. A per-row SUM over cells
// in its own row (e.g. =SUM(A2:C2)) doesn't count, so ordinary data rows still filter.
const TOTALS_RE = /\b(?:SUM|SUBTOTAL)\s*\(([^)]*)\)/gi
const RANGE_RE = /\$?[A-Z]+\$?(\d+)\s*:\s*\$?[A-Z]+\$?(\d+)/gi
function isTotalsFormula(v, r) {
  if (typeof v !== 'string' || !v.startsWith('=')) return false
  for (const m of v.matchAll(TOTALS_RE)) {
    for (const rg of m[1].matchAll(RANGE_RE)) {
      const a = Number(rg[1]) - 1, b = Number(rg[2]) - 1
      if (Math.min(a, b) < Math.max(a, b) && Math.max(a, b) < r) return true
    }
  }
  return false
}
const totalsRows = computed(() => {
  void renderKey.value
  const out = new Set()
  for (const [k, e] of cells.value) {
    const r = Number(k.slice(0, k.indexOf(',')))
    if (!out.has(r) && isTotalsFormula(e?.v, r)) out.add(r)
  }
  return out
})

const hiddenRows = computed(() => {
  void renderKey.value
  const fs = activeFilters.value
  const hidden = new Set()
  if (!fs.length || !sheet.value) return hidden
  const hr = sheet.value.headerRows
  const pinned = pinnedRows.value, totals = totalsRows.value
  for (let r = hr; r < sheet.value.rowCount; r++) {
    if (pinned.has(r) || totals.has(r)) continue
    if (!fs.every((f) => matchFilter(r, f))) hidden.add(r)
  }
  return hidden
})
const filterSummary = computed(() => activeFilters.value.map((f) => {
  const name = headerText(f.c) || colName(f.c)
  const op = tr(FILTER_OPS.find((o) => o.value === f.op)?.label || f.op)
  return { c: f.c, text: opNeedsValue(f.op) ? `${name} ${op} "${f.val}"` : `${name} ${op}` }
}))
function openFilter() {
  findOpen.value = false
  filterCol.value = selection.c
  const cur = filters[selection.c]
  filterForm.op = cur?.op || 'contains'
  filterForm.val = cur?.val ?? ''
  filterOpen.value = true
}
function closeFilter() { filterOpen.value = false; grid.value?.focus() }
function applyFilter() {
  const c = filterCol.value
  if (!opNeedsValue(filterForm.op) || (filterForm.val ?? '') !== '') filters[c] = { op: filterForm.op, val: filterForm.val }
  else delete filters[c]
}
function clearFilterCol(c) { delete filters[c ?? filterCol.value]; if (c === undefined) filterForm.val = '' }
function clearAllFilters() { for (const k of Object.keys(filters)) delete filters[k]; filterForm.val = '' }

// ------------------------------------------------------------------ find & replace
const findOpen = ref(false)
const findText = ref('')
const replaceText = ref('')
const findCase = ref(false)
const matchIndex = ref(-1)
const matches = computed(() => {
  void renderKey.value
  const q = findText.value
  if (!findOpen.value || !q) return []
  const needle = findCase.value ? q : q.toLowerCase()
  const out = []
  for (const [k, cell] of cells.value) {
    const raw = cell.v
    if (raw == null) continue
    const hay = findCase.value ? raw : raw.toLowerCase()
    if (hay.includes(needle)) {
      const [r, c] = k.split(',').map(Number)
      out.push({ r, c })
    }
  }
  out.sort((a, b) => a.r - b.r || a.c - b.c)
  return out
})
function openFind() {
  filterOpen.value = false
  findOpen.value = true
  nextTick(() => findInput.value?.focus?.())
}
function closeFind() {
  findOpen.value = false
  matchIndex.value = -1
  grid.value?.focus()
}
function gotoMatch(i) {
  const list = matches.value
  if (!list.length) { matchIndex.value = -1; return ElMessage.info(tr("No matches")) }
  matchIndex.value = ((i % list.length) + list.length) % list.length
  const m = list[matchIndex.value]
  grid.value?.select(m.r, m.c)
}
const findNext = () => gotoMatch(matchIndex.value + 1)
const findPrev = () => gotoMatch(matchIndex.value - 1)
function replaceCurrent() {
  const list = matches.value
  if (!list.length) return ElMessage.info(tr("No matches"))
  let i = matchIndex.value
  if (i < 0 || i >= list.length) i = 0
  const m = list[i]
  if (canEdit(m.r, m.c)) { ElMessage.warning(tr("Read-only cell")); return findNext() }
  const raw = getRaw(m.r, m.c) ?? ''
  const next = replaceAll(raw, findText.value, replaceText.value, findCase.value)
  if (next !== raw) applyEdits([{ r: m.r, c: m.c, v: next, s: getStyle(m.r, m.c) }])
  nextTick(() => gotoMatch(matchIndex.value)) // matches recompute; stay at the same spot
}
function replaceAllMatches() {
  const list = matches.value
  if (!list.length) return ElMessage.info(tr("No matches"))
  const edits = []
  let blocked = 0
  for (const m of list) {
    if (canEdit(m.r, m.c)) { blocked++; continue }
    const raw = getRaw(m.r, m.c) ?? ''
    const next = replaceAll(raw, findText.value, replaceText.value, findCase.value)
    if (next !== raw) edits.push({ r: m.r, c: m.c, v: next, s: getStyle(m.r, m.c) })
  }
  if (blocked) ElMessage.warning(tr("{0} read-only cell(s) skipped", [blocked]))
  if (edits.length) { applyEdits(edits); ElMessage.success(tr("Replaced in {0} cell(s)", [edits.length])) }
  else ElMessage.info(tr("Nothing to replace"))
}
function replaceAll(text, find, repl, caseSensitive) {
  if (!find) return text
  if (caseSensitive) return text.split(find).join(repl)
  const re = new RegExp(find.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi')
  return text.replace(re, repl)
}

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

// ------------------------------------------------------------------ AutoSum (Σ)
// Excel/Sheets-style: a single selected cell sums the contiguous numbers directly
// above it (or to its left when there are none above); a multi-cell selection writes a
// per-column =SUM() into the row just below the selection, giving a totals row.
function autoSum() {
  const s = sheet.value
  if (!s) return
  const isNum = (r, c) => typeof evaluator.value(r, c) === 'number'
  const edits = []
  let target = null

  if (selection.r1 === selection.r2 && selection.c1 === selection.c2) {
    const r = selection.r, c = selection.c
    if (canEdit(r, c)) return ElMessage.warning(tr("Can't AutoSum: this cell is read-only"))
    let top = r
    while (top - 1 >= 0 && isNum(top - 1, c)) top--
    let range = null
    if (top < r) range = cellRef(top, c) + ':' + cellRef(r - 1, c)
    else {
      let left = c
      while (left - 1 >= 0 && isNum(r, left - 1)) left--
      if (left < c) range = cellRef(r, left) + ':' + cellRef(r, c - 1)
    }
    if (!range) return ElMessage.info(tr("No adjacent numbers to sum"))
    edits.push({ r, c, v: `=SUM(${range})`, s: getStyle(r, c) })
    target = { r, c }
  } else {
    const totalRow = selection.r2 + 1 // just below the selection
    let blocked = 0
    for (let c = selection.c1; c <= selection.c2; c++) {
      if (canEdit(totalRow, c)) { blocked++; continue }
      const range = cellRef(selection.r1, c) + ':' + cellRef(selection.r2, c)
      edits.push({ r: totalRow, c, v: `=SUM(${range})`, s: getStyle(totalRow, c) })
    }
    if (blocked) ElMessage.warning(tr("{0} read-only cell(s) skipped", [blocked]))
    if (!edits.length) return
    target = { r: totalRow, c: selection.c1 }
  }
  applyEdits(edits)
  if (target) grid.value?.select(target.r, target.c)
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
  const prevRows = sheet.value.rowCount
  const detail = await api.post(`/sheets/${sheet.value.id}/structure`, { op, index, count, clientId: CLIENT_ID })
  // keep new rows visible under an active filter (they're blank, so they'd be hidden at once)
  shiftPinned(op, index, count)
  if (op === 'INSERT_ROWS') pinRows(Array.from({ length: count }, (_, i) => index + i))
  else if (op === 'APPEND_ROWS') pinRows(Array.from({ length: count }, (_, i) => prevRows + i))
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
    case 'merge': return mergeSelection(false)
    case 'merge-center': return mergeSelection(true)
    case 'unmerge': return unmergeSelection()
    case 'clear-format': return clearFormatting()
    case 'find': return openFind()
    case 'sort-asc': return sortRange(false)
    case 'sort-desc': return sortRange(true)
    case 'filter': return openFilter()
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

// ------------------------------------------------------------------ worksheet tabs
const canManageTabs = computed(() => isOwner.value && auth.canCreate)
const displayTabs = computed(() => {
  const t = sheet.value?.tabs ?? []
  if (t.length) return t
  return sheet.value ? [{ id: sheet.value.id, name: sheet.value.tabName || tr('Sheet 1'), index: 0 }] : []
})
const showTabBar = computed(() => (sheet.value?.tabs?.length > 1) || canManageTabs.value)
async function addTab() {
  const detail = await api.post(`/sheets/${sheet.value.id}/tabs`, { clientId: CLIENT_ID })
  ElMessage.success(tr("Worksheet added"))
  router.push({ name: 'sheet', params: { id: String(detail.id) } })
}
async function renameTab(tab) {
  if (!canManageTabs.value) return
  try {
    const { value } = await ElMessageBox.prompt(tr("Worksheet name"), tr("Rename worksheet"),
      { inputValue: tab.name || '', inputValidator: (v) => (v && v.trim().length <= 100) || tr("Enter 1–100 characters") })
    await api.put(`/sheets/${tab.id}/tab`, { name: value.trim(), clientId: CLIENT_ID })
    if (tab.id === sheet.value.id) await reload(); else refreshTabs()
  } catch { /* cancelled */ }
}
async function deleteTab(tab) {
  if (!canManageTabs.value) return
  const tabs = sheet.value?.tabs ?? []
  if (tabs.length <= 1) return ElMessage.info(tr("A workbook needs at least one worksheet"))
  try {
    await ElMessageBox.confirm(tr("Delete worksheet \"{0}\" and its history? This cannot be undone.", [tab.name]),
      tr("Delete worksheet"), { type: 'warning', confirmButtonText: tr("Delete") })
    const res = await api.delete(`/sheets/${tab.id}/tab`)
    if (tab.id === sheet.value.id) router.push({ name: 'sheet', params: { id: String(res.id) } })
    else refreshTabs()
    ElMessage.success(tr("Worksheet deleted"))
  } catch { /* cancelled */ }
}
async function refreshTabs() {
  // another tab changed; refresh just the tab list without disturbing the open grid
  try {
    const d = await api.get(`/sheets/${sheet.value.id}`, { silent: true })
    sheet.value.tabs = d.tabs
  } catch { /* ignore */ }
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
      pinnedRows.value = new Set() // row indices shifted under us
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

function onGlobalKey(e) {
  const mod = e.ctrlKey || e.metaKey
  if (mod && e.key.toLowerCase() === 'f') { e.preventDefault(); openFind() }
  else if (e.key === 'Escape' && findOpen.value) closeFind()
  else if (e.key === 'Escape' && filterOpen.value) closeFilter()
}
onMounted(() => window.addEventListener('keydown', onGlobalKey))

onBeforeUnmount(() => {
  unsubscribeSheet?.()
  stopConn()
  clearTimeout(reloadTimer)
  window.removeEventListener('keydown', onGlobalKey)
})
</script>

<style scoped>
.editor { position: relative; display: flex; flex-direction: column; height: 100%; min-height: 0; background: var(--panel); }
.title-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 16px 6px; gap: 12px; flex-wrap: wrap; }
.title-left { display: flex; align-items: center; gap: 6px; min-width: 0; }
.title-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sheet-name { font-size: 17px; font-weight: 600; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sheet-sub { color: var(--text-2); font-size: 12px; margin-top: 2px; }
.access { cursor: help; }
.toolbar { display: flex; align-items: center; gap: 6px; padding: 6px 16px; border-top: 1px solid var(--border); flex-wrap: wrap; }
.toolbar .sep { width: 1px; height: 20px; background: var(--border); margin: 0 4px; }
.toolbar .sigma { font-weight: 700; font-size: 15px; line-height: 1; }
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
.tab-bar { display: flex; align-items: stretch; gap: 2px; padding: 0 16px; border-top: 1px solid var(--border); background: var(--grid-head); overflow-x: auto; scrollbar-width: thin; }
.tab-bar .tab { display: inline-flex; align-items: center; gap: 4px; flex: none; max-width: 220px; white-space: nowrap; padding: 5px 12px; font: inherit; font-size: 12px; color: var(--text-2); background: transparent; border: 1px solid transparent; border-top: none; border-radius: 0 0 6px 6px; cursor: pointer; }
.tab-bar .tab .tab-label { overflow: hidden; text-overflow: ellipsis; }
.tab-bar .tab:hover { background: var(--hover); color: var(--text-1); }
.tab-bar .tab.active { background: var(--panel); color: var(--text-1); font-weight: 600; border-color: var(--border); box-shadow: inset 0 2px 0 var(--el-color-primary); }
.tab-bar .tab-close { font-size: 12px; border-radius: 3px; padding: 1px; color: var(--text-3); }
.tab-bar .tab-close:hover { background: var(--danger); color: #fff; }
.tab-bar .tab-add { flex: none; padding: 4px 8px; background: transparent; border: none; color: var(--text-2); cursor: pointer; border-radius: 0 0 6px 6px; }
.tab-bar .tab-add:hover { background: var(--hover); color: var(--text-1); }
.toolbar .dec { font-variant-numeric: tabular-nums; font-size: 12px; }
.find-panel { position: absolute; top: 96px; right: 24px; z-index: 30; width: 340px; display: flex; flex-direction: column; gap: 8px; padding: 12px; background: var(--panel); border: 1px solid var(--border); border-radius: 10px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2); }
.find-panel .find-row { display: flex; align-items: center; gap: 6px; }
.find-panel .find-row .el-input { flex: 1; }
.find-panel .find-count { font-size: 12px; color: var(--text-2); white-space: nowrap; min-width: 48px; text-align: center; }
.filter-panel { position: absolute; top: 96px; right: 24px; z-index: 30; width: 320px; display: flex; flex-direction: column; gap: 8px; padding: 12px; background: var(--panel); border: 1px solid var(--border); border-radius: 10px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2); }
.filter-panel .filter-head { display: flex; align-items: center; justify-content: space-between; }
.filter-panel .filter-title { font-size: 13px; font-weight: 600; display: flex; align-items: baseline; gap: 6px; }
.filter-panel .filter-hdr { font-weight: 400; font-size: 12px; color: var(--text-2); overflow: hidden; text-overflow: ellipsis; max-width: 180px; white-space: nowrap; }
.filter-panel .filter-row { display: flex; align-items: center; gap: 6px; }
.filter-panel .filter-op { flex: 1; }
.filter-panel .filter-row .el-input { flex: 1; }
.filter-panel .filter-active { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; padding-top: 4px; border-top: 1px solid var(--border); }
.filter-panel .filter-active .el-tag { max-width: 100%; }
.status-bar { display: flex; gap: 16px; padding: 4px 16px; font-size: 12px; color: var(--text-2); border-top: 1px solid var(--border); background: var(--grid-head); }
.status-bar .remote { color: var(--warn-text); }
.live { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-2); margin-right: 4px; }
.live i { width: 8px; height: 8px; border-radius: 50%; background: var(--text-3); }
.live.on i { background: var(--ok); box-shadow: 0 0 0 3px var(--ok-soft); }
</style>
