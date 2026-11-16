<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>{{ tr("Sheets") }}</h2>
        <p>{{ tr("Every spreadsheet you can see, stored in the database and editable in real time.") }}</p>
      </div>
      <div class="actions">
        <el-input v-model="q" :placeholder="tr('Search sheets')" :prefix-icon="Search" clearable class="search" />
        <el-select v-model="dept" :placeholder="tr('All departments')" clearable class="dept">
          <el-option v-for="d in auth.departments" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-button v-if="auth.canCreate" :icon="Upload" @click="importOpen = true">{{ tr("Import") }}</el-button>
        <el-button v-if="auth.canCreate" type="primary" :icon="Plus" @click="openNew()">{{ tr("New sheet") }}</el-button>
      </div>
    </div>

    <el-table :data="filtered" v-loading="loading" class="table" @row-click="(row) => $router.push({ name: 'sheet', params: { id: row.id } })" row-class-name="clickable">
      <el-table-column :label="tr('Name')" min-width="240">
        <template #default="{ row }">
          <div class="name">{{ row.name }}<span v-if="row.tabCount > 1" class="tabs-badge">{{ tr("{0} tabs", [row.tabCount]) }}</span></div>
          <div class="desc">{{ row.description }}</div>
        </template>
      </el-table-column>
      <el-table-column :label="tr('Owner')" width="170">
        <template #default="{ row }"><span class="dept-pill" :style="deptStyle(row.ownerDepartment.code)">{{ row.ownerDepartment.name }}</span></template>
      </el-table-column>
      <el-table-column :label="tr('Shared with')" min-width="200">
        <template #default="{ row }">
          <span v-for="p in row.sharedWith" :key="p.departmentId" class="share">
            {{ p.departmentName }} <small>{{ p.level === 'EDIT' ? tr('edit') : tr('view') }}</small>
          </span>
          <span v-if="!row.sharedWith.length" class="muted">{{ tr("Not shared") }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="tr('My access')" width="110">
        <template #default="{ row }"><el-tag size="small" effect="plain" :type="{ OWNER: 'success', EDIT: 'primary', VIEW: 'info' }[row.access]">{{ tr(row.access) }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="tr('Size')" width="110"><template #default="{ row }">{{ row.rowCount }} × {{ row.colCount }}</template></el-table-column>
      <el-table-column :label="tr('Updated')" width="150"><template #default="{ row }">{{ timeAgo(row.updatedAt) }}<div v-if="row.changesLast24h" class="hot">{{ row.changesLast24h }} {{ tr("edits / 24 h") }}</div></template></el-table-column>
    </el-table>

    <el-dialog v-model="newOpen" :title="tr('New sheet')" width="520px">
      <el-form label-position="top">
        <el-form-item :label="tr('Name')"><el-input v-model="form.name" maxlength="160" :placeholder="tr('e.g. Material Shortage Requests')" /></el-form-item>
        <el-form-item :label="tr('Description')"><el-input v-model="form.description" maxlength="500" /></el-form-item>
        <el-form-item v-if="auth.isAdmin" :label="tr('Owner department')">
          <el-select v-model="form.departmentId" style="width: 100%">
            <el-option v-for="d in auth.departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="tr('Start from')">
          <el-select v-model="form.templateId" :placeholder="tr('Blank sheet')" clearable style="width: 100%">
            <el-option v-for="t in templates" :key="t.id" :label="t.name + (t.department ? ' — ' + t.department.name : '')" :value="t.id" />
          </el-select>
        </el-form-item>
        <div v-if="!form.templateId" class="row2">
          <el-form-item :label="tr('Rows')"><el-input-number v-model="form.rows" :min="1" :max="5000" /></el-form-item>
          <el-form-item :label="tr('Columns')"><el-input-number v-model="form.cols" :min="1" :max="200" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="newOpen = false">{{ tr("Cancel") }}</el-button>
        <el-button type="primary" :disabled="!form.name" :loading="busy" @click="create">{{ tr("Create") }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importOpen" :title="tr('Import from Excel / CSV')" width="520px" @closed="file = null">
      <el-form label-position="top">
        <el-form-item :label="tr('File (.xlsx, .xls, .csv)')">
          <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls,.xlsm,.csv" :on-change="(f) => (file = f.raw)" :on-remove="() => (file = null)" class="up">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">{{ tr("Drop a file here or") }} <em>{{ tr("click to choose") }}</em></div>
            <template #tip><div class="tip">{{ tr("Worksheets stay together in one sheet, as tabs you can switch between. Formulas, bold text, fills and column widths are kept. Max 20 MB.") }}</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item :label="tr('Sheet name (optional)')"><el-input v-model="importName" :placeholder="tr('Defaults to the file name')" /></el-form-item>
        <el-form-item v-if="auth.isAdmin" :label="tr('Owner department')">
          <el-select v-model="importDept" style="width: 100%" clearable :placeholder="tr('My department')">
            <el-option v-for="d in auth.departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importOpen = false">{{ tr("Cancel") }}</el-button>
        <el-button type="primary" :disabled="!file" :loading="busy" @click="doImport">{{ tr("Import") }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { tr } from '../i18n.js'

import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search, Upload } from '@element-plus/icons-vue'
import api from '../api.js'
import { subscribe } from '../realtime.js'
import { useAuth } from '../stores/auth.js'
import { deptStyle, timeAgo } from '../utils.js'

const auth = useAuth()
const route = useRoute()
const router = useRouter()
const sheets = ref([])
const templates = ref([])
const loading = ref(true)
const q = ref('')
const dept = ref(null)
const newOpen = ref(false)
const importOpen = ref(false)
const busy = ref(false)
const form = reactive({ name: '', description: '', departmentId: null, templateId: null, rows: 100, cols: 12 })
const file = ref(null)
const importName = ref('')
const importDept = ref(null)

async function load() {
  loading.value = true
  try { sheets.value = await api.get('/sheets') } finally { loading.value = false }
}
load()
let t = null
const stop = subscribe('/topic/activity', (e) => {
  if (['SHEET_CREATED', 'SHEET_DELETED', 'META_CHANGED', 'SHEET_SHARED'].includes(e.type)) { clearTimeout(t); t = setTimeout(load, 400) }
})
onBeforeUnmount(() => { stop(); clearTimeout(t) })

const filtered = computed(() => sheets.value.filter((s) =>
  (!dept.value || s.ownerDepartment.id === dept.value) &&
  (!q.value || (s.name + ' ' + (s.description ?? '')).toLowerCase().includes(q.value.toLowerCase()))))

async function openNew(templateId = null) {
  templates.value = await api.get('/templates')
  Object.assign(form, { name: '', description: '', departmentId: auth.user.department.id, templateId, rows: 100, cols: 12 })
  newOpen.value = true
}
async function create() {
  busy.value = true
  try {
    const s = await api.post('/sheets', form)
    newOpen.value = false
    router.push({ name: 'sheet', params: { id: s.id } })
  } finally { busy.value = false }
}
async function doImport() {
  const fd = new FormData()
  fd.append('file', file.value)
  if (importName.value) fd.append('name', importName.value)
  if (importDept.value) fd.append('departmentId', importDept.value)
  busy.value = true
  try {
    const created = await api.post('/sheets/import', fd, { timeout: 120000 })
    importOpen.value = false
    const book = created[0]
    ElMessage.success(book.tabCount > 1 ? tr("Imported \"{0}\" with {1} tabs", [book.name, book.tabCount]) : tr("Imported {0} sheet(s)", [created.length]))
    router.push({ name: 'sheet', params: { id: book.id } })
  } finally { busy.value = false }
}

if (route.query.new) openNew(route.query.template ? Number(route.query.template) : null)
if (route.query.import) importOpen.value = true
</script>

<style scoped>
.page { max-width: 1400px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.page-head h2 { margin: 0; font-size: 22px; color: var(--text-1); }
.page-head p { margin: 4px 0 0; color: var(--text-2); }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.search { width: 220px; }
.dept { width: 180px; }
.table { border-radius: 12px; border: 1px solid var(--border); }
.table :deep(.clickable) { cursor: pointer; }
.name { font-weight: 600; color: var(--text-1); }
.tabs-badge { margin-left: 8px; font-size: 11px; font-weight: 500; color: var(--text-2); padding: 1px 6px; border-radius: 4px; background: var(--hover); }
.desc { font-size: 12px; color: var(--text-2); }
.share { display: inline-block; margin: 0 6px 2px 0; font-size: 12px; padding: 1px 6px; border-radius: 4px; background: var(--hover); }
.share small { color: var(--text-3); }
.muted { color: var(--text-3); font-size: 12px; }
.hot { font-size: 11px; color: var(--warn-text); }
.row2 { display: flex; gap: 16px; }
.up { width: 100%; }
.tip { font-size: 12px; color: var(--text-2); line-height: 1.5; }
@media (max-width: 640px) { .search, .dept { width: 100%; } }
</style>
