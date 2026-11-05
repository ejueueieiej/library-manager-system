import axios from 'axios'
import { ElMessage } from 'element-plus'

// export 出去是为了让阅读页"关标签前的 keepalive 保存"也用同一个 key，
// 避免在别处再硬编码一遍 'library_token'
export const TOKEN_KEY = 'library_token'

/**
 * 未登录时的回调（2.0 新增）
 *
 * 为什么不在这里直接 import router 或 store？
 * 因为 router 会 import 页面 → 页面 import api → api import request，
 * 而 request 再反过来 import router 就形成【循环依赖】，打包时会出各种怪问题。
 * 所以这里只留一个钩子，由 router/index.js 在启动时注册真正的跳转逻辑。
 */
let onUnauthorized = null
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

/**
 * axios 统一封装
 * 所有页面都不直接 import axios，而是 import 这个 request，
 * 好处是「统一带上 token」「统一处理错误」「统一解包 Result」只需要写一次。
 */
const request = axios.create({
  // 所有请求都以 /api 开头，由 vite 代理（开发）/ nginx（生产）转发到后端 8081
  baseURL: '/api',
  timeout: 10000
})

/* ---------------- 请求拦截器 ---------------- */
request.interceptors.request.use(
  (config) => {
    // 2.0：自动带上 token
    // 这里直接从 localStorage 读，而不是 import store —— 同样是为了避开循环依赖
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      // 注意格式：Bearer + 一个空格 + token，后端用 substring(7) 切掉前缀
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/* ---------------- 响应拦截器 ----------------
 *
 * 3.0 新增一个可选开关：silent
 *   请求时写成 request.get('/notices/unread-count', { silent: true })
 *   失败就【不弹红字】，Promise 照常 reject（业务代码仍然能 catch 到）。
 *
 * 为什么需要它？
 *   导航栏那个未读红点是在主界面挂载时"后台悄悄刷一下"的。
 *   后端还没实现 /notices/unread-count 时它是 404，
 *   不静默的话用户每进一次主界面就被弹一次"接口不存在"，纯属噪音。
 *
 * ⚠️ silent 只关掉"提示语"，不关掉逻辑：
 *   401 时该清登录态、该跳登录页，一样照做。
 */
request.interceptors.response.use(
  (response) => {
    // 后端统一返回 { code, msg, data }
    const res = response.data

    if (res.code === 1) {
      // 成功：直接把 data 交给业务代码，页面里就少写一层 res.data.data
      return res.data
    }

    // 业务失败（例如 ISBN 已存在、密码错误）：弹出后端给的提示语，并中断 Promise 链
    if (!response.config?.silent) {
      ElMessage.error(res.msg || '操作失败')
    }
    return Promise.reject(new Error(res.msg || '操作失败'))
  },
  (error) => {
    // 这里是 HTTP 层面的失败（网络断了、401、403、500 等）
    const status = error.response?.status
    const msg = error.response?.data?.msg

    const silent = error.config?.silent === true
    const toast = (text) => {
      if (!silent) {
        ElMessage.error(text)
      }
    }

    if (status === 401) {
      // 401 =「我不知道你是谁」→ 清掉登录态、回登录页
      // ★ 这一句不在 toast 里：silent 只影响提示语，该跳转还是要跳
      toast(msg || '登录已过期，请重新登录')
      onUnauthorized?.()
    } else if (status === 403) {
      // 403 =「我知道你是谁，但你不能干这个」
      // ⚠️ 这里【绝对不能】跳登录页 —— 用户已经登录了，跳过去只会一脸懵
      toast(msg || '没有操作权限')
    } else {
      toast(msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
