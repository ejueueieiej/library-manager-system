<template>
  <div class="page-root">
    <div class="profile">
      <!-- ================= 账号信息（只读） ================= -->
      <section class="card">
        <div class="card-head">
          <h2 class="card-title">账号信息</h2>
          <span class="role-tag">{{ roleLabel }}</span>
        </div>

        <dl class="fields">
          <div class="row">
            <dt>用户名</dt>
            <dd class="mono">{{ user?.username || '—' }}</dd>
          </div>
          <div class="row">
            <dt>姓名</dt>
            <dd>{{ user?.realName || '—' }}</dd>
          </div>
          <div class="row">
            <dt>角色</dt>
            <dd>
              {{ roleLabel }}
              <span class="hint">
                {{ isAdmin ? '（可以增删改查图书、管理用户）' : '（只能查询图书、查看详情）' }}
              </span>
            </dd>
          </div>
          <div class="row">
            <dt>手机号</dt>
            <dd class="mono">{{ user?.phone || '—' }}</dd>
          </div>
          <div class="row">
            <dt>邮箱</dt>
            <dd>{{ user?.email || '—' }}</dd>
          </div>
        </dl>
      </section>

      <!-- ================= 修改密码 ================= -->
      <section class="card">
        <div class="card-head">
          <h2 class="card-title">修改密码</h2>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="84px"
          class="pwd-form"
        >
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="form.oldPassword"
              type="password"
              placeholder="请输入当前密码"
              show-password
            />
          </el-form-item>

          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="form.newPassword"
              type="password"
              placeholder="6~20 位"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再输一次新密码"
              show-password
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              保存新密码
            </el-button>
            <el-button @click="resetForm">清空</el-button>
          </el-form-item>
        </el-form>

        <p class="note">
          改完密码后当前登录仍然有效（JWT 已经签发，不会立刻失效）；
          想彻底退出请用右上角的「退出登录」。
        </p>
      </section>

      <!-- ================= 注销账号（危险区，占满一整行） ================= -->
      <section class="card danger-card">
        <div class="card-head">
          <h2 class="card-title">注销账号</h2>
        </div>

        <p class="danger-note">
          注销后账号会被<strong>永久删除</strong>，无法恢复，也无法再用它登录。<br />
          如果只是想暂时不用，请改用右上角的「退出登录」，
          或者联系管理员在「用户管理」里把它改成「禁用」—— 禁用是可以恢复的。
        </p>

        <el-button type="danger" plain :loading="canceling" @click="handleDeleteAccount">
          注销我的账号
        </el-button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePassword, deleteAccount } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const { state: userState, isAdmin, roleLabel, clear } = useUserStore()
const user = computed(() => userState.user)

const formRef = ref(null)
const submitting = ref(false)
/** 注销账号请求进行中（防止连点两次） */
const canceling = ref(false)

function createEmptyForm() {
  return { oldPassword: '', newPassword: '', confirmPassword: '' }
}

const form = reactive(createEmptyForm())

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '新密码长度 6~20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再输一次新密码', trigger: 'blur' },
    {
      // 自定义校验：两次输入必须一致
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function resetForm() {
  Object.assign(form, createEmptyForm())
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
    // 只传原密码和新密码；当前用户是谁由 token 决定，不传 userId（防水平越权）
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    ElMessage.success('密码修改成功')
    resetForm()
  } catch (e) {
    // 「原密码不正确」等提示由拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

/* ---------------- 注销账号（危险操作） ---------------- */
async function handleDeleteAccount() {
  const name = userState.user?.username || '当前账号'

  // ① 先让用户输密码 —— 注销不可逆，必须有一步"证明是你本人"
  let password
  try {
    const res = await ElMessageBox.prompt(
      `注销后「${name}」会被永久删除，无法恢复，也无法再用它登录。` +
        `请输入登录密码确认这是你本人的操作。`,
      '确认注销账号',
      {
        type: 'warning',
        confirmButtonText: '确认注销',
        cancelButtonText: '我再想想',
        confirmButtonClass: 'el-button--danger',
        inputType: 'password',
        inputPlaceholder: '请输入当前登录密码',
        // 返回 true 表示通过；返回字符串会被当成错误提示显示
        inputValidator: (value) => (value ? true : '请输入密码'),
        // 这个弹窗太危险，别让用户手滑点到遮罩层就关掉
        closeOnClickModal: false
      }
    )
    password = res.value
  } catch {
    return // 用户点了「我再想想」
  }

  // ② 真正发请求
  canceling.value = true
  try {
    await deleteAccount({ password })

    ElMessage.success('账号已注销，期待下次再见')
    // ⚠️ 必须【主动清掉本地登录态】，不能只跳登录页：
    //    否则路由守卫还以为你登录着，会拿这个刚被删掉的账号去调 /auth/me，
    //    拿到 401 再弹一次「登录已过期」，页面白白多闪一下。
    clear()
    router.replace('/login')
  } catch (e) {
    // 「密码不正确」「不能注销最后一个管理员」等提示由拦截器统一弹出
  } finally {
    canceling.value = false
  }
}
</script>

<style scoped>
.page-root {
  padding: 20px 24px 32px;
}

.profile {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1000px) {
  .profile {
    grid-template-columns: 1fr;
  }
}

.card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: 20px 22px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--line);
}

.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.role-tag {
  font-size: 11px;
  line-height: 1;
  padding: 3px 7px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  color: var(--ink-3);
}

.fields {
  margin: 0;
}

.row {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--row-line);
  font-size: 13.5px;
}

.row dt {
  width: 76px;
  flex: none;
  color: var(--ink-4);
}

.row dd {
  margin: 0;
  min-width: 0;
  word-break: break-all;
}

.hint {
  color: var(--ink-4);
  font-size: 12.5px;
}

.pwd-form {
  margin-top: 18px;
}

/* ---------------- 注销账号（危险区） ---------------- */
/* 占满一整行（两栏布局下也是），视觉上和上面两张卡分开，像个"危险区" */
.danger-card {
  grid-column: 1 / -1;
}

.danger-note {
  margin: 16px 0 16px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-2);
}

.danger-note strong {
  color: var(--alert);
  font-weight: 600;
}
</style>
