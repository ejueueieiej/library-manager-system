import request from '@/utils/request'

/**
 * 文件上传相关接口（图书封面图）
 *
 * 后端路径：POST /files/upload
 * 请求格式：multipart/form-data，文件字段名固定为 file
 * 返回：{ url: "/uploads/2026/10/xxxx.jpg" }
 *
 * 说明：后端返回的是【相对地址】（以 /uploads 开头），不是完整 URL。
 * 这样开发环境（Vite 代理）和生产环境（nginx 反代）都能正常显示，
 * 不用改数据库里的数据。
 */
export function uploadCover(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 传图片比传 JSON 慢，超时给宽一点（默认是 10 秒）
    timeout: 30000
  })
}
