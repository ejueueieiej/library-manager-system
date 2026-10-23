import request from '@/utils/request'
import { removeEmpty } from '@/utils/params'

/**
 * 用户管理接口（2.0，全部仅管理员可用）
 * 详见 docs/api/02-v2.0-用户与登录接口文档.md 第 6~11 节
 *
 * 普通用户调这些接口会被后端返回 403，前端也会用路由守卫与按钮隐藏双重挡掉，
 * 但"前端藏起来"只是体验，真正的权限控制必须在后端（后端已经有了）。
 */

/* ========== 6. 用户列表（分页 + 多条件） ========== */
// 后端路径：GET /users?pageNum=&pageSize=&username=&realName=&role=&status=
export function pageUsers(params) {
  return request.get('/users', { params: removeEmpty(params) })
}

/* ========== 7. 新增用户 ========== */
// 后端路径：POST /users
export function addUser(data) {
  return request.post('/users', data)
}

/* ========== 8. 修改用户 ========== */
// 后端路径：PUT /users/{id}
// 注意：不改 username，也不在这儿改密码
export function updateUser(id, data) {
  return request.put(`/users/${id}`, data)
}

/* ========== 9. 启用 / 禁用 ========== */
// 后端路径：PATCH /users/{id}/status?status=0
export function updateUserStatus(id, status) {
  return request.patch(`/users/${id}/status`, null, { params: { status } })
}

/* ========== 10. 重置密码（管理员不需要知道旧密码） ========== */
// 后端路径：PUT /users/{id}/password
export function resetUserPassword(id, newPassword) {
  return request.put(`/users/${id}/password`, { newPassword })
}

/* ========== 11. 删除用户 ========== */
// 后端路径：DELETE /users/{id}
export function deleteUser(id) {
  return request.delete(`/users/${id}`)
}
