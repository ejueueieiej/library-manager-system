import request from '@/utils/request'

/**
 * 登录认证相关接口（2.0）
 * 详见 docs/api/02-v2.0-用户与登录接口文档.md
 */

/* ========== 1. 登录 ========== */
// 后端路径：POST /auth/login   （唯一不需要 token 的接口）
// 返回：{ token, user: { id, username, realName, role, avatar } }
export function login(data) {
  return request.post('/auth/login', data)
}

/* ========== 2. 取当前登录用户 ========== */
// 后端路径：GET /auth/me
// 用途：刷新页面后，localStorage 里只有 token，靠它把用户信息取回来
export function getMe() {
  return request.get('/auth/me')
}

/* ========== 3. 退出登录 ========== */
// 后端路径：POST /auth/logout
// 说明：JWT 是无状态的，真正的"退出"是前端删掉本地 token；这个接口是留给以后做黑名单的
export function logout() {
  return request.post('/auth/logout')
}

/* ========== 4. 修改自己的密码 ========== */
// 后端路径：PUT /auth/password
// 注意：请求体里【不带 userId】，改的是谁由 token 决定（防水平越权）
export function changePassword(data) {
  return request.put('/auth/password', data)
}

/* ========== 5. 注册（2.1 新增） ========== */
// 后端路径：POST /auth/register
//
// ⚠️ 后端【还没实现】—— 详见 docs/api/03-v2.1-注册接口文档.md
//    现在点「注册」会返回 404「接口不存在，请检查请求路径」，这是预期内的。
//    后端按文档实现之后，这个函数一行都不用改。
//
// 请求体：{ username, password, realName, phone, email }
// 角色由后端强制设成 USER、status 设成 1（前端不传，也不该能传）
export function register(data) {
  return request.post('/auth/register', data)
}

/* ========== 6. 注销自己的账号（2.2 新增） ========== */
// 后端路径：DELETE /auth/account
//
// ⚠️⚠️ 路径里【没有 id】—— 注销的是谁完全由 token 决定。
//       绝对不要做成 DELETE /users/{id}：那样任何人改一下 URL 里的数字
//       就能把别人的账号删掉，是典型的【水平越权漏洞】。
//
// 请求体：{ password } —— 二次确认，防止别人趁你没锁屏就把号删了。
//   浏览器/axios 发 DELETE 带 body 是合法的，axios 里写在 config.data：
//       request.delete(url, { data })
//
// ⚠️ 后端还没实现 —— 详见 docs/api/04-v2.2-注销账号接口文档.md
//    现在点「注销我的账号」会返回 404「接口不存在」，这是预期内的。
//
// 如果哪天发现请求体在某个代理上被丢掉（极老的代理会丢 DELETE 的 body），
// 把路径换成 POST /auth/account/cancel 即可 —— 前端只需要改这一行。
export function deleteAccount(data) {
  return request.delete('/auth/account', { data })
}
