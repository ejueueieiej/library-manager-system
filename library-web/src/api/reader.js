import request from '@/utils/request'
import { removeEmpty } from '@/utils/params'

/**
 * 在线阅读接口（4.0）
 * 详见 docs/api/07-v4.0-在线阅读接口文档.md
 *
 * 书籍和用户文件是两套路径，但结构完全对称：
 *   /reader/book/{id}/...   管理员上传的书籍正文
 *   /reader/file/{id}/...   用户自己上传的 txt
 * 所以下面用 type = 'book' | 'file' 统一三个阅读接口。
 */

/* ========== 0. 开关某本书的在线阅读（仅管理员） ========== */
// PUT /books/{id}/readable   body: { readable: 1 | 0 }
export function setBookReadable(id, readable) {
  return request.put(`/books/${id}/readable`, { readable })
}

/* ========== 1. 上传 / 替换书籍正文（仅管理员） ========== */
//
// ★ 必须用 FormData 传【原始字节】。
//   如果先用 FileReader.readAsText 读成字符串再发 JSON，
//   GBK 文件在浏览器这一层就已经解码错了，后端再怎么检测编码都救不回来。
//
// ★ 单独把超时调大：全局默认 10 秒，10MB 的文件可能传不完。
export function uploadBookContent(bookId, file) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/reader/book/${bookId}/content`, form, { timeout: 60000 })
}

// DELETE /reader/book/{bookId}/content  会连带清掉所有人对这本书的阅读进度
export function deleteBookContent(bookId) {
  return request.delete(`/reader/book/${bookId}/content`)
}

/* ========== 2. 阅读（书籍 / 文件通用） ========== */

/** 目录 + 能不能读。注意：不能读时也是 code=1，靠 data.readable 判断 */
export function getReaderMeta(type, id) {
  return request.get(`/reader/${type}/${id}/meta`)
}

/** 单章正文。index 从 0 开始 */
export function getReaderChapter(type, id, index) {
  return request.get(`/reader/${type}/${id}/chapter/${index}`)
}

/** 保存进度：{ chapterIndex, chapterTitle, percent } */
export function saveReaderProgress(type, id, data) {
  return request.put(`/reader/${type}/${id}/progress`, data)
}

/* ========== 3. 我的 txt 文件 ========== */
export function uploadReaderFile(file, title) {
  const form = new FormData()
  form.append('file', file)
  if (title) form.append('title', title)
  return request.post('/reader/file', form, { timeout: 60000 })
}

/** 我的文件列表（只返回自己的） */
export function pageReaderFiles(params) {
  return request.get('/reader/file', { params: removeEmpty(params) })
}

export function deleteReaderFile(id) {
  return request.delete(`/reader/file/${id}`)
}

/** 重命名我的文件（只改标题，正文不动）；返回更新后的文件信息 */
export function updateReaderFile(id, title) {
  return request.put(`/reader/file/${id}`, { title })
}

/** 单个文件的基本信息（标题/字数/章节数） */
export function getReaderFile(id) {
  return request.get(`/reader/file/${id}`)
}
