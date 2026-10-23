<template>
  <div class="page-root">
    <!-- ================= 工具条 ================= -->
    <div class="toolbar">
      <div class="filter-set">
        <el-input
          v-model="queryParams.username"
          placeholder="用户名"
          clearable
          class="w-140"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="queryParams.realName"
          placeholder="姓名"
          clearable
          class="w-130"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="queryParams.role" placeholder="全部角色" clearable class="w-120">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="读者" value="USER" />
        </el-select>
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="w-120">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>

        <el-button class="search-btn" @click="handleSearch">查询</el-button>
        <el-button link class="reset-btn" @click="handleReset">重置</el-button>
      </div>

      <div class="toolbar-right">
        <el-button type="primary" @click="handleAdd">新增用户</el-button>
      </div>
    </div>

    <!-- ================= 表格 ================= -->
    <div class="table-area">
      <!-- 首次加载用骨架屏 -->
      <el-skeleton v-if="loading && !list.length" :rows="8" animated class="table-skeleton" />
      <el-table v-else v-loading="loading" :data="list" row-key="id">
        <el-table-column label="用户名" min-width="140">
          <template #default="{ row }">
            <span class="mono">{{ row.username }}</span>
            <!-- 自己那一行打个标记，避免误操作把自己禁了/删了 -->
            <span v-if="row.id === currentUserId" class="self-tag">我</span>
          </template>
        </el-table-column>

        <el-table-column prop="realName" label="姓名" width="120" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="140">
          <template #default="{ row }">
            <span class="mono">{{ row.phone || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>

        <el-table-column label="角色" width="96">
          <template #default="{ row }">
            <span class="role-chip" :class="{ 'is-admin': row.role === 'ADMIN' }">
              {{ row.role === 'ADMIN' ? '管理员' : '读者' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="118">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-dot" :class="row.status === 1 ? 'is-on' : 'is-off'"></span>
              <span class="status-text">{{ row.status === 1 ? '正常' : '禁用' }}</span>
              <!-- 不能禁用自己：开关直接禁掉（后端也会挡） -->
              <el-switch
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                size="small"
                :disabled="row.id === currentUserId"
                :loading="row.statusLoading"
                @change="handleStatusChange(row)"
              />
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">
            <date-time-cell class="muted" :value="row.createTime" />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="196" align="right">
          <template #default="{ row }">
            <el-button link @click="handleEdit(row)">编辑</el-button>
            <el-button link @click="handleResetPassword(row)">重置密码</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.id === currentUserId"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无用户数据" />
        </template>
      </el-table>
    </div>

    <!-- ================= 分页 ================= -->
    <div class="table-foot">
      <span class="foot-total">共 <b class="tnum">{{ total }}</b> 条</span>
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="loadUsers"
      />
    </div>

    <!-- ================= 新增 / 编辑用户 ================= -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="84px">
        <!-- 用户名只在新增时可填：登录名是身份，改了历史记录就对不上了 -->
        <el-form-item v-if="!isEdit" label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3~50 字符，不可重复" maxlength="50" />
        </el-form-item>

        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="6~20 位，后端会 BCrypt 加密后存储"
            show-password
            maxlength="20"
          />
        </el-form-item>

        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="必填" maxlength="50" />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" maxlength="100" />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="form.role" :disabled="isEdit && form.id === currentUserId">
            <el-radio value="USER">读者</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
          <div v-if="isEdit && form.id === currentUserId" class="field-tip">
            不能修改自己的角色（会把自己锁在外面）
          </div>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status" :disabled="isEdit && form.id === currentUserId">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存修改' : '确定新增' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addUser,
  deleteUser,
  pageUsers,
  resetUserPassword,
  updateUser,
  updateUserStatus
} from '@/api/user'
import DateTimeCell from '@/components/DateTimeCell.vue'
import { useUserStore } from '@/store/user'

const { state: userState } = useUserStore()
// 当前登录用户 id：用来挡住"改自己/禁自己/删自己"
const currentUserId = computed(() => userState.user?.id)

/* ---------------- 查询条件 ---------------- */
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  realName: '',
  role: null,
  status: null
})

/* ---------------- 列表 ---------------- */
const list = ref([])
const total = ref(0)
const loading = ref(false)

/* ---------------- 弹窗 ---------------- */
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const isEdit = ref(false)

function createEmptyForm() {
  return {
    id: null,
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    role: 'USER',
    status: 1
  }
}

const form = reactive(createEmptyForm())

const rules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3~50 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6~20 位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

/* ---------------- 查询 ---------------- */
async function loadUsers() {
  loading.value = true
  try {
    const data = await pageUsers({ ...queryParams })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadUsers()
}

function handleReset() {
  queryParams.username = ''
  queryParams.realName = ''
  queryParams.role = null
  queryParams.status = null
  handleSearch()
}

function handleSizeChange() {
  queryParams.pageNum = 1
  loadUsers()
}

/* ---------------- 新增 / 编辑 ---------------- */
function handleAdd() {
  isEdit.value = false
  Object.assign(form, createEmptyForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    realName: row.realName ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    role: row.role ?? 'USER',
    status: row.status ?? 1
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      // 修改：不带 username / password（接口约定里它们不参与修改）
      const { username, password, id, ...rest } = form
      await updateUser(id, rest)
      ElMessage.success('修改成功')
    } else {
      const { id, ...payload } = form
      await addUser(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch (e) {
    // 失败提示由拦截器统一弹出（如"用户名已存在"）
  } finally {
    submitting.value = false
  }
}

/* ---------------- 启用 / 禁用 ---------------- */
async function handleStatusChange(row) {
  const target = row.status
  // 提示语带上这个人的名字，管理员一眼就知道刚才改的是谁（不用回头再看表格）
  const name = row.realName || row.username
  row.statusLoading = true
  try {
    await updateUserStatus(row.id, target)
    ElMessage.success(target === 1 ? `“${name}”已被开启` : `“${name}”已被禁用`)
  } catch (e) {
    row.status = target === 1 ? 0 : 1 // 失败回滚开关
  } finally {
    row.statusLoading = false
  }
}

/* ---------------- 重置密码 ---------------- */
async function handleResetPassword(row) {
  let value
  try {
    const res = await ElMessageBox.prompt(
      `给「${row.realName || row.username}」设置一个新密码`,
      '重置密码',
      {
        confirmButtonText: '确定重置',
        cancelButtonText: '取消',
        inputPlaceholder: '6~20 位新密码',
        inputPattern: /^.{6,20}$/,
        inputErrorMessage: '密码长度 6~20 位'
      }
    )
    value = res.value
  } catch {
    return // 点了取消
  }

  await resetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

/* ---------------- 删除 ---------------- */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.realName || row.username}」吗？`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  await deleteUser(row.id)
  ElMessage.success('删除成功')
  if (list.value.length === 1 && queryParams.pageNum > 1) {
    queryParams.pageNum--
  }
  loadUsers()
}

onMounted(loadUsers)
</script>

<style scoped>
.page-root {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  /* 上下留白比原来多一点，条件区和表格之间不至于贴在一起 */
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
}

.filter-set {
  display: flex;
  align-items: center;
  /* 控件之间：12px（原来 8px，四个框挤成一坨，看着很糊） */
  gap: 12px;
  /* 窗口变窄换行时，行与行之间也别挨太近 */
  row-gap: 12px;
  flex-wrap: wrap;
}

/*
 * 让「条件组」和「动作组」之间多一道视觉缝隙：
 * 查询按钮左边额外留 6px，读起来就是「[4个筛选框]  (空隙)  [查询] [重置]」
 */
.search-btn {
  margin-left: 6px;
}

.w-140 { width: 140px; }
.w-130 { width: 130px; }
.w-120 { width: 120px; }

.reset-btn {
  color: var(--ink-4);
}

.reset-btn:hover {
  color: var(--ink-1);
}

.toolbar-right {
  margin-left: auto;
}

.table-area {
  flex: 1;
  padding: 0 24px;
}

/* 自己那一行的标记 */
.self-tag {
  margin-left: 6px;
  font-size: 11px;
  padding: 1px 5px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  color: var(--ink-3);
}

.role-chip {
  font-size: 12px;
  padding: 2px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 99px;
  color: var(--ink-2);
}

/* 管理员用实心黑标记区分（黑白系统里唯一的"实心"用法） */
.role-chip.is-admin {
  background: #000;
  border-color: #000;
  color: #fff;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex: none;
}

.status-dot.is-on { background: #000; }
.status-dot.is-off { background: var(--ink-5); }

.status-text {
  font-size: 13px;
  color: var(--ink-2);
}

.muted {
  color: var(--ink-4);
}

.field-tip {
  font-size: 12px;
  color: var(--ink-4);
  line-height: 1.6;
}

.table-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px 18px;
  border-top: 1px solid var(--line);
  margin-top: 4px;
}

.foot-total {
  font-size: 13px;
  color: var(--ink-3);
}

.foot-total b {
  color: #000;
  font-weight: 600;
}

.table-foot :deep(.el-pagination) {
  margin-left: auto;
}
</style>
