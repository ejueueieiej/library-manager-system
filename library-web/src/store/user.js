import { computed, reactive } from 'vue'
import { getMe, login as loginApi, logout as logoutApi } from '@/api/auth'

/**
 * 轻量用户状态（2.0）
 *
 * 为什么不用 pinia？—— 少一个依赖，而且这里要存的东西就那么点（token + 用户信息）。
 * 用模块级 reactive 做单例，所有组件共享同一份状态。
 *
 * localStorage 里只放两样东西：
 *   library_token  —— JWT，请求拦截器会自动带上
 *   library_user   —— 用户信息缓存（刷新页面时先拿它渲染，避免菜单闪一下再变）
 */

const TOKEN_KEY = 'library_token'
const USER_KEY = 'library_user'

function readCachedUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    // localStorage 里的内容被手动改坏了也不该让整个应用起不来
    return null
  }
}

const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readCachedUser(),
  // /auth/me 是否已经成功取过一次（路由守卫用它判断要不要去拉用户信息）
  loaded: false
})

/* ---------------- 派生状态（模块级只算一次，全局共用） ---------------- */
const isLogin = computed(() => !!state.token)
const isAdmin = computed(() => state.user?.role === 'ADMIN')
const displayName = computed(() => state.user?.realName || state.user?.username || '未登录')
const roleLabel = computed(() => (state.user?.role === 'ADMIN' ? '管理员' : '读者'))
/** 头像占位用的单字：取姓名第一个字 */
const avatarChar = computed(() => displayName.value.charAt(0))

/* ---------------- 动作 ---------------- */

/** 登录：成功后把 token 和用户信息落盘 */
async function doLogin(form) {
  const data = await loginApi(form)
  state.token = data.token
  state.user = data.user
  state.loaded = true
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(data.user))
  return data
}

/**
 * 拉取当前登录用户
 * 用途：刷新页面后 localStorage 里只有 token（可能还有过期的用户信息缓存），
 * 靠这个接口把真实的用户信息同步回来 —— 尤其是 role，它决定菜单和按钮
 */
async function fetchMe() {
  if (!state.token) return null
  const data = await getMe()
  state.user = data
  state.loaded = true
  localStorage.setItem(USER_KEY, JSON.stringify(data))
  return data
}

/** 退出：先告诉后端（失败也无所谓），然后一定要清本地 */
async function doLogout() {
  try {
    await logoutApi()
  } catch (e) {
    // 后端挂了 / token 已过期，都不该阻止用户退出
  }
  clear()
}

/** 清空登录态（401 时也会用到） */
function clear() {
  state.token = ''
  state.user = null
  state.loaded = false
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function useUserStore() {
  return {
    state,
    isLogin,
    isAdmin,
    displayName,
    roleLabel,
    avatarChar,
    doLogin,
    doLogout,
    fetchMe,
    clear
  }
}
