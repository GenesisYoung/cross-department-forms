<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="template ? tr('Edit template') : tr('New template')" width="860px" top="5vh" @open="init">
    <el-form label-position="top">
      <div class="row">
        <el-form-item :label="tr('Name')" class="grow"><el-input v-model="form.name" maxlength="120" /></el-form-item>
        <el-form-item v-if="auth.isAdmin" :label="tr('Department')">
          <el-select v-model="form.departmentId" style="width: 200px">
            <el-option v-for="d in auth.departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="tr('Rows')"><el-input-number v-model="form.rowCount" :min="2" :max="5000" /></el-form-item>
      </div>
      <el-form-item :label="tr('Description')"><el-input v-model="form.description" maxlength="500" /></el-form-item>
    </el-form>

    <div class="cols-head">
      <b>{{ tr("Columns") }}</b>
      <span class="muted">{{ tr("Header row is created from these names. Starter data rows from an existing template are kept.") }}</span>
    </div>
    <el-table :data="cols" size="small" max-height="380">
      <el-table-column label="#" width="44"><template #default="{ $index }">{{ colName($index) }}</template></el-table-column>
      <el-table-column :label="tr('Column name')" min-width="180">
        <template #default="{ row }"><el-input v-model="row.title" size="small" :placeholder="tr('e.g. Material Deadline')" /></template>
      </el-table-column>
      <el-table-column :label="tr('Width')" width="110">
        <template #default="{ row }"><el-input-number v-model="row.width" size="small" :min="40" :max="600" :step="10" controls-position="right" style="width: 96px" /></template>
      </el-table-column>
      <el-table-column :label="tr('Watched')" width="80" align="center">
        <template #default="{ row }"><el-checkbox v-model="row.watched" /></template>
      </el-table-column>
      <el-table-column :label="tr('Only editable by')" min-width="200">
        <template #default="{ row }">
          <el-select v-model="row.editDepartments" multiple size="small" :placeholder="tr('Anyone with edit access')" style="width: 100%">
            <el-option v-for="d in auth.departments" :key="d.code" :label="d.name" :value="d.code" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column width="90">
        <template #default="{ $index }">
          <el-button link :icon="Top" :disabled="$index === 0" @click="move($index, -1)" />
          <el-button link :icon="Delete" @click="cols.splice($index, 1)" />
        </template>
      </el-table-column>
    </el-table>
    <el-button class="add" :icon="Plus" @click="cols.push(blank())">{{ tr("Add column") }}</el-button>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ tr("Cancel") }}</el-button>
      <el-button type="primary" :disabled="!form.name || !cols.length" :loading="saving" @click="save">{{ tr("Save template") }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { tr } from '../i18n.js'

import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Top, Delete } from '@element-plus/icons-vue'
import api from '../api.js'
import { useAuth } from '../stores/auth.js'
import { colName } from '../formula/engine.js'

const props = defineProps({ modelValue: Boolean, template: Object })
const emit = defineEmits(['update:modelValue', 'saved'])
const auth = useAuth()
const HEADER_STYLE = '{"b":true,"bg":"#e8eef9"}'

const form = reactive({ name: '', description: '', departmentId: null, rowCount: 200 })
const cols = ref([])
const saving = ref(false)
let dataCells = []
const blank = () => ({ title: '', width: 110, watched: false, editDepartments: [] })

function init() {
  const t = props.template
  if (!t) {
    Object.assign(form, { name: '', description: '', departmentId: auth.user.department.id, rowCount: 200 })
    cols.value = [blank(), blank(), blank()]
    dataCells = []
    return
  }
  const d = t.definition
  Object.assign(form, { name: t.name, description: t.description ?? '', departmentId: t.department?.id ?? auth.user.department.id, rowCount: d.rowCount })
  const hr = Math.max(d.headerRows, 1)
  const header = Object.fromEntries(d.cells.filter((c) => c.r === hr - 1).map((c) => [c.c, c.v]))
  const meta = Object.fromEntries(d.columns.map((c) => [c.index, c]))
  cols.value = Array.from({ length: d.colCount }, (_, i) => ({
    title: meta[i]?.title || header[i] || '',
    width: meta[i]?.width ?? 110,
    watched: !!meta[i]?.watched,
    editDepartments: [...(meta[i]?.editDepartments ?? [])]
  }))
  dataCells = d.cells.filter((c) => c.r >= hr)
}
function move(i, d) {
  const [x] = cols.value.splice(i, 1)
  cols.value.splice(i + d, 0, x)
}
async function save() {
  const definition = {
    rowCount: form.rowCount,
    colCount: cols.value.length,
    headerRows: 1,
    columns: cols.value.map((c, i) => ({ index: i, title: c.title || null, width: c.width, watched: c.watched, editDepartments: c.editDepartments })),
    cells: [
      ...cols.value.map((c, i) => ({ r: 0, c: i, v: c.title || null, s: HEADER_STYLE })).filter((c) => c.v),
      ...dataCells.filter((c) => c.c < cols.value.length && c.r < form.rowCount)
    ]
  }
  const body = { name: form.name, description: form.description, departmentId: form.departmentId, definition }
  saving.value = true
  try {
    if (props.template) await api.put(`/templates/${props.template.id}`, body)
    else await api.post('/templates', body)
    ElMessage.success(tr("Template saved"))
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.row { display: flex; gap: 12px; flex-wrap: wrap; }
.grow { flex: 1; min-width: 200px; }
.cols-head { display: flex; gap: 10px; align-items: baseline; margin-bottom: 6px; }
.muted { color: var(--text-3); font-size: 12px; }
.add { margin-top: 8px; }
</style>
