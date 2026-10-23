import request from '@/utils/request'
import { removeEmpty } from '@/utils/params'

/**
 * 借阅归还接口（3.0 模块二）
 * 详见 docs/api/06-v3.0-借阅归还接口文档.md —— 8 个方法与 8 个接口一一对应
 */

/** 状态：1借出中 / 2已归还 / 3已取消（逾期不是状态，由后端算 overdueDays） */
export const BORROW_STATUS = {
  BORROWING: 1,
  RETURNED: 2,
  CANCELED: 3
}

/* ========== 1. 借书（用户自助） ========== */
// POST /borrows   body: { bookId }
export function borrowBook(bookId) {
  return request.post('/borrows', { bookId })
}

/* ========== 2. 我的借阅 ========== */
// GET /borrows/my   params: { pageNum, pageSize, status, overdue }
export function pageMyBorrows(params) {
  return request.get('/borrows/my', { params: removeEmpty(params) })
}

/* ========== 3. 我的借阅统计 ========== */
// GET /borrows/my/summary → { borrowingCount, overdueCount, totalCount }
export function getMyBorrowSummary() {
  return request.get('/borrows/my/summary')
}

/* ========== 4. 确认归还（仅管理员） ========== */
// PATCH /borrows/{id}/return?returnTime=2026-10-15
//
// ⚠️ id 是【借阅记录的 id】，不是图书 id —— 同一人可能借同一本书两次
// ⚠️ returnTime 可选，是"实际归还日期"：管理员隔天才录入时必须能填，
//    否则读者会被算成逾期（这个日期直接决定 overdueDays）
// ⚠️ 普通读者没有这个能力，调了会 403
export function returnBook(id, returnTime) {
  return request.patch(`/borrows/${id}/return`, null, {
    params: removeEmpty({ returnTime })
  })
}

/* ========== 5. 续借（本人或管理员） ========== */
// PATCH /borrows/{id}/renew
// 规则全在后端：只能续 1 次、每次 +30 天、已逾期不让续
export function renewBook(id) {
  return request.patch(`/borrows/${id}/renew`)
}

/* ========== 6. 全部借阅记录（仅管理员） ========== */
// GET /borrows   params: { pageNum, pageSize, username, bookName, status, overdue }
export function pageAllBorrows(params) {
  return request.get('/borrows', { params: removeEmpty(params) })
}

/* ========== 7. 撤销借阅（仅管理员） ========== */
// PATCH /borrows/{id}/cancel
// 场景：用户线上借了却没来取书 → 书回库存，记录置为"已取消"（不删记录）
export function cancelBorrow(id) {
  return request.patch(`/borrows/${id}/cancel`)
}

/* ========== 8. 删除借阅记录（仅管理员，备用通道） ========== */
// DELETE /borrows/{id}
//
// 只用于清理测试数据 / 订正误操作，不要拿它代替"确认归还"或"撤销借阅"。
// ⚠️ 借出中（status=1）的记录后端会拒绝删除 —— 那是"书在谁手上"的唯一凭证，
//    删了库存会永远少一本。要删就先「撤销借阅」，再删。
export function deleteBorrow(id) {
  return request.delete(`/borrows/${id}`)
}
