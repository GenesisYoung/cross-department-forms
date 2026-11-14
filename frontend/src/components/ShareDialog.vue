<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" title="Share with departments" width="560px" @open="init">
    <p class="hint">
      <b>{{ sheet.ownerDepartment.name }}</b> owns this sheet. Give other departments view or edit access.
      Column rules can additionally let a department edit specific columns even with view access.
    </p>
    <el-table :data="rows" size="small">
      <el-table-column label="Department">
        <template #default="{ row }">
          <span class="dept-pill" :style="deptStyle(row.code)">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Access" width="260">
        <template #default="{ row }">
          <el-radio-group v-model="row.level" size="small">
            <el-radio-button value="NONE">None</el-radio-button>
            <el-radio-button value="VIEW">View</el-radio-button>
            <el-radio-button value="EDIT">Edit</el-radio-button>
          </el-radio-group>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="primary" :loading="saving" @click="save">Save sharing</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api.js'
import { useAuth } from '../stores/auth.js'
import { deptStyle } from '../utils.js'

const props = defineProps({ modelValue: Boolean, sheet: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue', 'saved'])
const auth = useAuth()
const rows = ref([])
const saving = ref(false)

function init() {
  const current = Object.fromEntries(props.sheet.sharedWith.map((p) => [p.departmentId, p.level]))
  rows.value = auth.departments
    .filter((d) => d.id !== props.sheet.ownerDepartment.id)
    .map((d) => ({ id: d.id, code: d.code, name: d.name, level: current[d.id] ?? 'NONE' }))
}
async function save() {
  saving.value = true
  try {
    await api.put(`/sheets/${props.sheet.id}/permissions`, {
      permissions: rows.value.map((r) => ({ departmentId: r.id, level: r.level }))
    })
    ElMessage.success('Sharing updated — newly added departments were notified')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.hint { color: var(--text-2); font-size: 13px; margin: 0 0 12px; line-height: 1.5; }
</style>
