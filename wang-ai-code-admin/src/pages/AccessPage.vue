<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, type TableColumnsType } from 'ant-design-vue'
import { data } from '@/request'

type Account = { id: string; username: string; displayName: string; roleId: string; enabled: boolean }
type Role = { id: string; name: string; permissions: string[]; systemRole: boolean }
type Permission = { code: string; label: string }
const accounts = ref<Account[]>([])
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const loading = ref(false)
const loadError = ref('')
const formError = ref('')
const busy = ref(false)
const editing = ref<'account' | 'role'>()
const editId = ref<string>()
const accountForm = reactive({ username: '', displayName: '', password: '', roleId: undefined as string | undefined, enabled: true })
const roleForm = reactive({ name: '', permissions: [] as string[] })
const isRoot = computed(() => roles.value.some((role) => role.id === accountForm.roleId && role.systemRole))
const roleChoices = computed(() => roles.value.filter((role) => !role.systemRole || isRoot.value)
  .map((role) => ({ value: role.id, label: role.name })))
const permissionChoices = computed(() => permissions.value.map((permission) => ({ value: permission.code, label: permission.label })))

const accountColumns: TableColumnsType<Account> = [
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '角色', key: 'role' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'actions', width: 100 },
]
const roleColumns: TableColumnsType<Role> = [
  { title: '角色', dataIndex: 'name', key: 'name', width: 160 },
  { title: '权限', key: 'permissions' },
  { title: '操作', key: 'actions', width: 134 },
]

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [accountList, roleList, permissionList] = await Promise.all([
      data<Account[]>('/admin/accounts'), data<Role[]>('/admin/roles'), data<Permission[]>('/admin/permissions'),
    ])
    accounts.value = accountList
    roles.value = roleList
    permissions.value = permissionList
  } catch (error) { loadError.value = error instanceof Error ? error.message : '读取权限配置失败' }
  finally { loading.value = false }
}

function editAccount(account?: Account) {
  editId.value = account?.id
  Object.assign(accountForm, {
    username: account?.username ?? '', displayName: account?.displayName ?? '', password: '',
    roleId: account?.roleId, enabled: account?.enabled ?? true,
  })
  formError.value = ''
  editing.value = 'account'
}

function editRole(role?: Role) {
  editId.value = role?.id
  roleForm.name = role?.name ?? ''
  roleForm.permissions = [...(role?.permissions ?? [])]
  formError.value = ''
  editing.value = 'role'
}

async function confirm() {
  if (busy.value || !editing.value) return
  busy.value = true
  formError.value = ''
  const resource = editing.value === 'account' ? '/admin/accounts' : '/admin/roles'
  const input = editing.value === 'account' ? accountForm : roleForm
  try {
    await data(editId.value ? `${resource}/${editId.value}` : resource, editId.value ? 'PUT' : 'POST', input)
    accountForm.password = ''
    editing.value = undefined
    message.success('已保存')
    await load()
  } catch (error) { formError.value = error instanceof Error ? error.message : '保存失败' }
  finally { busy.value = false }
}

async function removeRole(id: string) {
  try { await data(`/admin/roles/${id}`, 'DELETE'); await load() }
  catch (error) { message.error(error instanceof Error ? error.message : '无法删除角色') }
}

onMounted(load)
</script>

<template>
  <div>
    <header class="page-head"><div><h1>管理员与角色</h1><p>给管理员分配角色，按业务操作授权。</p></div></header>
    <a-alert v-if="loadError" type="error" show-icon :message="loadError" class="form-error">
      <template #action><a-button size="small" @click="load">重试</a-button></template>
    </a-alert>
    <section class="paper-panel">
      <a-tabs>
        <a-tab-pane key="accounts" tab="管理员账号">
          <div class="table-toolbar"><a-button type="primary" @click="editAccount()">添加管理员</a-button></div>
          <a-table :data-source="accounts" :columns="accountColumns" :loading="loading" :scroll="{ x: 600 }" row-key="id">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'role'">{{ roles.find((role) => role.id === record.roleId)?.name }}</template>
              <a-tag v-else-if="column.key === 'status'" :color="record.enabled ? 'success' : undefined">{{ record.enabled ? '启用' : '停用' }}</a-tag>
              <a-button v-else-if="column.key === 'actions'" type="link" @click="editAccount(record as Account)">编辑</a-button>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="roles" tab="角色权限">
          <div class="table-toolbar"><a-button type="primary" @click="editRole()">添加角色</a-button></div>
          <a-table :data-source="roles" :columns="roleColumns" :loading="loading" row-key="id" :scroll="{ x: 530 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'permissions'">
                <span v-if="record.systemRole">全部权限</span>
                <span v-else>{{ permissions.filter((item) => record.permissions.includes(item.code)).map((item) => item.label).join('、') || '未授权' }}</span>
              </template>
              <div v-else-if="column.key === 'actions' && !record.systemRole" class="compact-actions">
                <a-button size="small" type="link" @click="editRole(record as Role)">编辑</a-button>
                <a-popconfirm title="删除这个角色？" @confirm="removeRole(record.id)">
                  <a-button size="small" type="link" danger>删除</a-button>
                </a-popconfirm>
              </div>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </section>
    <a-modal :open="!!editing" :title="`${editId ? '编辑' : '添加'}${editing === 'account' ? '管理员' : '角色'}`"
      ok-text="保存" :confirm-loading="busy" :mask-closable="!busy" @ok="confirm"
      @cancel="!busy && (editing = undefined)" @after-close="accountForm.password = ''">
      <a-form v-if="editing === 'account'" name="admin-account" :model="accountForm" layout="vertical" @finish="confirm">
        <a-form-item label="管理员账号" name="username" required><a-input v-model:value="accountForm.username" autocomplete="username" :maxlength="64" /></a-form-item>
        <a-form-item label="显示名称" name="displayName" required><a-input v-model:value="accountForm.displayName" :maxlength="80" /></a-form-item>
        <a-form-item label="密码" name="password" :required="!editId" :extra="editId ? '留空继续使用当前密码。' : '至少 8 个字符。'">
          <a-input-password v-model:value="accountForm.password" autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="角色" name="roleId" required><a-select v-model:value="accountForm.roleId" :options="roleChoices" :disabled="isRoot" /></a-form-item>
        <a-form-item label="账号状态" name="enabled"><a-switch v-model:checked="accountForm.enabled" :disabled="isRoot" checked-children="启用" un-checked-children="停用" /></a-form-item>
      </a-form>
      <a-form v-else-if="editing === 'role'" name="admin-role" :model="roleForm" layout="vertical" @finish="confirm">
        <a-form-item label="角色名称" name="name" required><a-input v-model:value="roleForm.name" :maxlength="80" /></a-form-item>
        <a-form-item label="权限" name="permissions">
          <a-checkbox-group v-model:value="roleForm.permissions" :options="permissionChoices" class="permission-grid" />
        </a-form-item>
      </a-form>
      <a-alert v-if="formError" type="error" show-icon :message="formError" role="alert" />
    </a-modal>
  </div>
</template>

<style scoped>
.permission-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
</style>
