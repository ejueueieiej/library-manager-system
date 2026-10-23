import request from '@/utils/request'
import { removeEmpty } from '@/utils/params'

/**
 * 通知公告接口（3.0 模块一）
 * 详见 docs/api/05-v3.0-通知公告接口文档.md —— 7 个方法与 7 个接口一一对应
 *
 * 后端写好一个，把对应方法一调就能联调。
 * 老规矩：request.js 的拦截器已经解包过一层，await 拿到的直接就是后端的 data。
 */

/* ============================================================
   类型映射：后端只给数字 1/2/3，中文文案放在前端
   ------------------------------------------------------------
   为什么不建一张 notice_category 表？
   因为通知的类型是【固定的三档】，不像图书分类那样会由用户不断增加。
   固定枚举放前端，少一张表、少一个接口、少一次请求。
   ============================================================ */
export const NOTICE_TYPES = [
  { value: 1, label: '公告' },
  { value: 2, label: '通知' },
  { value: 3, label: '活动' }
]

/** 数字 → 中文；后端给了没见过的值也不会显示成 undefined */
export function noticeTypeLabel(type) {
  return NOTICE_TYPES.find((item) => item.value === type)?.label ?? '通知'
}

/* ========== 1. 通知分页列表 ========== */
// 后端路径：GET /notices
// params 示例：{ pageNum: 1, pageSize: 10, keyword: '国庆', type: 1, onlyTop: 1 }
// 返回：{ total, list: [{ id, title, summary, type, isTop, status, publisherName, publishTime, isRead }] }
//
// ⚠️ status 传了也只有管理员生效，普通读者的请求会被后端强制成 1。
//    前端不传它，别给自己找麻烦。
export function pageNotices(params) {
  return request.get('/notices', { params: removeEmpty(params) })
}

/* ========== 2. 通知详情 ========== */
// 后端路径：GET /notices/{id}
// ★ 副作用：普通读者调它时，后端会顺手把这条标记成"已读"（管理员不会）
export function getNoticeById(id) {
  return request.get(`/notices/${id}`)
}

/* ========== 3. 我的未读数量 ========== */
// 后端路径：GET /notices/unread-count
// 返回的 data 是【一个裸数字】，例如 3（不是 { unreadCount: 3 }）
//
// silent: true —— 这是个"后台悄悄刷一下"的请求：
//   后端还没实现时不弹红字（否则每次进主界面都弹一次"接口不存在"），
//   401 的跳登录页逻辑照常执行，不受影响。
export function getUnreadCount() {
  return request.get('/notices/unread-count', { silent: true })
}

/* ========== 4. 发布通知（管理员） ========== */
// 后端路径：POST /notices
// 请求体：{ title, content, type, isTop, status }
// ⚠️ 没有 publisherId / publishTime —— 谁发的、什么时候发的由后端定
export function addNotice(data) {
  return request.post('/notices', data)
}

/* ========== 5. 编辑通知（管理员） ========== */
// 后端路径：PUT /notices/{id}
// 请求体和新增一样；后端会忽略 publisherId / publishTime，也不会清空已读记录
export function updateNotice(id, data) {
  return request.put(`/notices/${id}`, data)
}

/* ========== 6. 置顶 / 取消置顶（管理员） ========== */
// 后端路径：PATCH /notices/{id}/top?isTop=1
// 第二个参数是请求体，这里不需要所以传 null（和图书上下架一个写法）
export function updateNoticeTop(id, isTop) {
  return request.patch(`/notices/${id}/top`, null, { params: { isTop } })
}

/* ========== 7. 删除通知（管理员） ========== */
// 后端路径：DELETE /notices/{id}
// ★ 后端会连带删掉 notice_read 里指向它的记录（走事务），前端不用管
export function deleteNotice(id) {
  return request.delete(`/notices/${id}`)
}
