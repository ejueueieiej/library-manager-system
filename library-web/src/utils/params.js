/**
 * 查询参数处理小工具
 *
 * 为什么需要？点搜索框的「清空」后，el-input / el-select 会把值设成 ''
 * 请求就变成 /books?name=&status= 这种，后端用 Integer 接收 status 时
 * 空字符串转换失败会直接报 400，前端只能看到「网络异常」，很难排查。
 * 所以统一在发请求前把空值滤掉。
 */
export function removeEmpty(params = {}) {
  const result = {}
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) {
      result[key] = value
    }
  })
  return result
}

/** 书号规范化：用户从网页复制的 ISBN 常带连字符或空格，数据库里存的是纯数字 */
export function normalizeIsbn(isbn) {
  return String(isbn ?? '').replace(/[-\s]/g, '')
}
