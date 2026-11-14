<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>Administration</h2>
        <p>Users, roles and departments. Managers own their department's sheets; members edit data.</p>
      </div>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="Users" name="users">
        <div class="bar"><el-button type="primary" :icon="Plus" @click="editUser()">Add user</el-button></div>
        <el-table :data="users" class="table">
          <el-table-column prop="username" label="Username" width="160" />
          <el-table-column prop="displayName" label="Name" min-width="180" />
          <el-table-column label="Department" width="190">
            <template #default="{ row }"><span class="dept-pill" :style="deptStyle(row.department.code)">{{ row.department.name }}</span></template>
          </el-table-column>
          <el-table-column prop="role" label="Role" width="110" />
          <el-table-column label="Status" width="100">
            <template #default="{ row }"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? 'Active' : 'Disabled' }}</el-tag></template>
          </el-table-column>
          <el-table-column width="90"><template #default="{ row }"><el-button link type="primary" @click="editUser(row)">Edit</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="Departments" name="departments">
        <div class="bar"><el-button type="primary" :icon="Plus" @click="editDept()">Add department</el-button></div>
        <el-table :data="auth.departments" class="table">
          <el-table-column prop="code" label="Code" width="160" />
          <el-table-column label="Name" width="220">
            <template #default="{ row }"><span class="dept-pill" :style="deptStyle(row.code)">{{ row.name }}</span></template>
          </el-table-column>
          <el-table-column prop="description" label="Description" min-width="260" />
          <el-table-column width="90"><template #default="{ row }"><el-button link type="primary" @click="editDept(row)">Edit</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="userOpen" :title="userForm.id ? 'Edit user' : 'Add user'" width="460px">
      <el-form label-position="top">
        <el-form-item label="Username"><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="Display name"><el-input v-model="userForm.displayName" /></el-form-item>
        <el-form-item :label="userForm.id ? 'New password (leave empty to keep)' : 'Password (min 6)'"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="Department">
          <el-select v-model="userForm.departmentId" style="width: 100%">
            <el-option v-for="d in auth.departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Role">
          <el-radio-group v-model="userForm.role">
            <el-radio-button value="MEMBER">Member</el-radio-button>
            <el-radio-button value="MANAGER">Manager</el-radio-button>
            <el-radio-button value="ADMIN">Admin</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item><el-checkbox v-model="userForm.enabled">Account enabled</el-checkbox></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userOpen = false">Cancel</el-button>
        <el-button type="primary" @click="saveUser">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deptOpen" :title="deptForm.id ? 'Edit department' : 'Add department'" width="460px">
      <el-form label-position="top">
        <el-form-item label="Code (A–Z, 0–9, _; cannot be changed later)"><el-input v-model="deptForm.code" :disabled="!!deptForm.id" @input="deptForm.code = deptForm.code.toUpperCase()" /></el-form-item>
        <el-form-item label="Name"><el-input v-model="deptForm.name" /></el-form-item>
        <el-form-item label="Description"><el-input v-model="deptForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptOpen = false">Cancel</el-button>
        <el-button type="primary" @click="saveDept">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '../api.js'
import { useAuth } from '../stores/auth.js'
import { deptStyle } from '../utils.js'

const auth = useAuth()
const tab = ref('users')
const users = ref([])
const userOpen = ref(false)
const deptOpen = ref(false)
const userForm = reactive({})
const deptForm = reactive({})

const loadUsers = async () => { users.value = await api.get('/admin/users') }
loadUsers()

function editUser(u = null) {
  Object.assign(userForm, u
    ? { id: u.id, username: u.username, displayName: u.displayName, password: '', departmentId: u.department.id, role: u.role, enabled: u.enabled }
    : { id: null, username: '', displayName: '', password: '', departmentId: auth.departments[0]?.id, role: 'MEMBER', enabled: true })
  userOpen.value = true
}
async function saveUser() {
  const body = { ...userForm, password: userForm.password || null }
  if (userForm.id) await api.put(`/admin/users/${userForm.id}`, body)
  else await api.post('/admin/users', body)
  userOpen.value = false
  ElMessage.success('User saved')
  loadUsers()
}
function editDept(d = null) {
  Object.assign(deptForm, d ? { ...d } : { id: null, code: '', name: '', description: '' })
  deptOpen.value = true
}
async function saveDept() {
  const body = { code: deptForm.code, name: deptForm.name, description: deptForm.description }
  if (deptForm.id) await api.put(`/admin/departments/${deptForm.id}`, body)
  else await api.post('/admin/departments', body)
  deptOpen.value = false
  auth.departments = await api.get('/departments')
  ElMessage.success('Department saved')
}
</script>

<style scoped>
.page { max-width: 1200px; margin: 0 auto; }
.page-head h2 { margin: 0; font-size: 22px; color: var(--text-1); }
.page-head p { margin: 4px 0 14px; color: var(--text-2); }
.bar { margin-bottom: 10px; }
.table { border: 1px solid var(--border); border-radius: 10px; }
</style>
