<template>
  <div class="register-page">
    <div class="register-card">
      <!-- 品牌区：和登录页 / 主界面侧栏完全一致 -->
      <div class="brand">
        <img class="brand-mark" src="/brand/logo.svg" alt="星河图书" width="30" height="30" />
        <div class="brand-text">
          <div class="brand-name">星河图书</div>
          <div class="brand-sub mono">LIBRARY · v4.0</div>
        </div>
      </div>

      <h1 class="title">注册</h1>
      <p class="subtitle">
        注册后默认为「读者」角色，可查询图书、查看详情。<br />
        需要管理权限请联系管理员到「用户管理」里调整。
      </p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3~50 位，登录时使用"
            maxlength="50"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" maxlength="50" />
        </el-form-item>

        <!-- 手机号 + 邮箱并排，省一点纵向空间 -->
        <div class="grid-2">
          <el-form-item label="手机号（选填）" prop="phone">
            <el-input v-model="form.phone" placeholder="方便管理员联系你" maxlength="20" />
          </el-form-item>

          <el-form-item label="邮箱（选填）" prop="email">
            <el-input v-model="form.email" placeholder="忘记密码时可联系管理员" maxlength="100" />
          </el-form-item>
        </div>

        <!-- 密码 + 确认密码并排 -->
        <div class="grid-2">
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="6~20 位"
              show-password
              maxlength="20"
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再输一次"
              show-password
              maxlength="20"
              autocomplete="new-password"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
        </div>

        <el-button
          type="primary"
          size="large"
          class="submit"
          :loading="submitting"
          @click="handleSubmit"
        >
          注 册
        </el-button>

        <div class="login-row">
          <span class="login-tip">已经有账号了？</span>
          <button class="login-link" type="button" @click="goLogin">返回登录</button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'

/**
 * 注册页（2.1 新增）
 *
 * ⚠️ 后端接口【还没实现】—— 契约见 docs/api/03-v2.1-注册接口文档.md
 *    现在点「注册」会返回 404「接口不存在，请检查请求路径」，
 *    这是预期内的：页面和接口约定先落地，后端明天补。
 *    后端写好后，这个文件一行都不用改。
 *
 * 两个刻意的设计：
 *   1. 页面上【没有角色选择】—— 谁都能注册成管理员是不可接受的。
 *      角色由后端强制设成 USER，前端传了也不该被信任。
 *   2. confirmPassword 只用于前端校验，【不会发给后端】。
 */

const router = useRouter()

const formRef = ref(null)
const submitting = ref(false)

function createEmptyForm() {
  return {
    username: '',
    realName: '',
    phone: '',
    email: '',
    password: '',
    confirmPassword: ''
  }
}

const form = reactive(createEmptyForm())

/** 手机号可以不填；填了就得是像样的手机号 */
const PHONE_RE = /^1[3-9]\d{9}$/
/** 邮箱够用的宽松校验，故意不写复杂正则（严格校验交给后端） */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3~50 位', trigger: 'blur' },
    // 只允许字母/数字/下划线：空格和特殊字符容易在 URL、日志、SQL 里惹麻烦
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能用字母、数字、下划线', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名最多 50 个字符', trigger: 'blur' }
  ],
  phone: [
    {
      validator: (rule, value, callback) => {
        if (value && !PHONE_RE.test(value)) {
          callback(new Error('手机号格式不正确'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  email: [
    {
      validator: (rule, value, callback) => {
        if (value && !EMAIL_RE.test(value)) {
          callback(new Error('邮箱格式不正确'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6~20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再输一次密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function goLogin() {
  router.push('/login')
}

async function handleSubmit() {
  // 先做前端校验，通过了再发请求
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    // 只发后端认的 5 个字段：
    //   confirmPassword 是纯前端校验用的，不往上传
    //   role / status 由后端强制决定，前端【不传】，传了后端也不该信
    await register({
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      phone: form.phone.trim() || null,
      email: form.email.trim() || null
    })

    ElMessage.success('注册成功，请用新账号登录')
    // 带用户名跳回登录页，省得用户再打一遍
    router.replace({ path: '/login', query: { username: form.username.trim() } })
  } catch (e) {
    // 失败提示（"用户名已存在" / 404「接口不存在」）由 request.js 的拦截器统一弹出
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 40px 20px;
  background: var(--canvas);
}

.register-card {
  width: 100%;
  max-width: 520px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: 34px 36px 28px;
}

/* ---------------- 品牌 ---------------- */
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
}

.brand-mark {
  width: 30px;
  height: 30px;
  flex: none;
  display: block;
}

.brand-text {
  line-height: 1.25;
}

.brand-name {
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.brand-sub {
  font-size: 10.5px;
  color: var(--ink-4);
  letter-spacing: 0.04em;
}

/* ---------------- 标题 ---------------- */
.title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.03em;
}

.subtitle {
  margin: 0 0 20px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* ---------------- 两列布局 ---------------- */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 14px;
}

@media (max-width: 520px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}

/* 表单 label 默认偏浅，压深一点，和登录页的观感一致 */
:deep(.el-form-item__label) {
  color: var(--ink-2);
  font-size: 13px;
  padding-bottom: 2px;
}

.submit {
  width: 100%;
  margin-top: 6px;
}

/* ---------------- 返回登录 ---------------- */
.login-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 14px;
  font-size: 13px;
}

.login-tip {
  color: var(--ink-4);
}

/* 原生 button：这里只要一条文字链接的观感，引 el-button 反而要跟它自带的样式打架 */
.login-link {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: #000;
  font-weight: 500;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.login-link:hover {
  color: var(--ink-3);
}
</style>
