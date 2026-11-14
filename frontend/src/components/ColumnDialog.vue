<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="`Column ${letter} rules`" width="520px" @open="init">
    <el-form label-position="top">
      <el-form-item label="Display name (used in notifications and history)">
        <el-input v-model="form.title" :placeholder="header || 'e.g. Material Deadline'" maxlength="120" />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="form.watched">
          <b>Watched column</b> — any change notifies <i>every</i> department that can see this sheet as an
          IMPORTANT alert they must acknowledge (use for deadlines, quantities, statuses).
        </el-checkbox>
      </el-form-item>
      <el-form-item label="Only these departments may edit this column">
        <el-select v-model="form.editDepartments" multiple placeholder="Anyone with edit access" style="width: 100%">
          <el-option v-for="d in auth.departments" :key="d.code" :label="d.name" :value="d.code" />
        </el-select>
        <div class="hint">
          Leave empty to use the sheet's normal access. A department listed here can edit this column even if it only
          has <b>view</b> access to the sheet — e.g. let PMC fill “Required By” in Purchase's PO sheet.
        </div>
      </el-form-item>
      <el-form-item label="Width (px)">
        <el-input-number v-model="form.width" :min="30" :max="1000" :step="10" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="primary" :loading="saving" @click="save">Save rules</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api.js'
import { useAuth } from '../stores/auth.js'
import { colName } from '../formula/engine.js'

const props = defineProps({ modelValue: Boolean, sheetId: Number, col: Number, column: Object, header: String })
const emit = defineEmits(['update:modelValue', 'saved'])
const auth = useAuth()
const form = reactive({ title: '', watched: false, editDepartments: [], width: 110 })
const saving = ref(false)
const letter = computed(() => colName(props.col ?? 0))

function init() {
  Object.assign(form, {
    title: props.column?.title ?? '',
    watched: props.column?.watched ?? false,
    editDepartments: [...(props.column?.editDepartments ?? [])],
    width: props.column?.width ?? 110
  })
}
async function save() {
  saving.value = true
  try {
    await api.put(`/sheets/${props.sheetId}/columns/${props.col}`, { ...form })
    ElMessage.success('Column rules saved')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.hint { color: var(--text-2); font-size: 12px; line-height: 1.5; margin-top: 4px; }
</style>
