import request from '@/utils/request'
import { normalizeIsbn, removeEmpty } from '@/utils/params'

/**
 * 图书管理相关接口
 * 这里的方法和《01-v1.0-图书管理接口文档.md》里的 8 个接口一一对应，
 * 后端写好一个，你把对应方法一调就能联调。
 *
 * 注意：request.js 的响应拦截器已经帮我们解包过一层，
 * 所以下面的 await 拿到的直接就是后端的 data 字段，
 * 不需要再写 res.data.data 这种套娃写法。
 */

/* ========== 1. 查询全部分类 ========== */
// 后端路径：GET /categories
export function getCategoryList() {
  return request.get('/categories')
}

/* ========== 2. 图书分页 + 多条件查询 ========== */
// 后端路径：GET /books?pageNum=1&pageSize=10&isbn=&name=&author=&categoryId=&status=
// params 示例：{ pageNum: 1, pageSize: 10, isbn: '9787020002207', name: 'Java', categoryId: 2 }
// 返回：{ total: 总条数, list: [图书数组] }
export function pageBooks(params) {
  const clean = removeEmpty(params)

  // 书号特殊处理：用户从豆瓣/淘宝复制的 ISBN 常带连字符或空格
  // （978-7-02-000220-7），而数据库里存的是纯数字，这里统一去掉
  if (clean.isbn) {
    clean.isbn = normalizeIsbn(clean.isbn)
  }

  return request.get('/books', { params: clean })
}

/* ========== 3. 查询图书详情 ========== */
// 后端路径：GET /books/{id}
export function getBookById(id) {
  return request.get(`/books/${id}`)
}

/* ========== 4. 新增图书 ========== */
// 后端路径：POST /books  （请求体是 JSON）
export function addBook(data) {
  return request.post('/books', data)
}

/* ========== 5. 修改图书 ========== */
// 后端路径：PUT /books/{id}
export function updateBook(id, data) {
  return request.put(`/books/${id}`, data)
}

/* ========== 6. 删除图书（物理删除，数据真的从表里消失） ========== */
// 后端路径：DELETE /books/{id}
export function deleteBook(id) {
  return request.delete(`/books/${id}`)
}

/* ========== 7. 批量删除 ========== */
// 后端路径：DELETE /books/batch?ids=1,2,3
// 传进来的是数组 [1,2,3]，这里拼成后端要的逗号分隔字符串
export function deleteBooksBatch(ids) {
  return request.delete('/books/batch', { params: { ids: ids.join(',') } })
}

/* ========== 8. 上架 / 下架 ========== */
// 后端路径：PATCH /books/{id}/status?status=0
// 第二个参数是请求体，这里不需要所以传 null
export function updateBookStatus(id, status) {
  return request.patch(`/books/${id}/status`, null, { params: { status } })
}
