/**
 * 后端返回的 LocalDateTime 是 ISO 格式（2026-10-16T22:22:30），
 * 统一在这里处理，别让那个 "T" 漏到页面上。
 *
 * 注意：这两个函数对两种格式都成立（带 T 的、带空格的），
 * 所以后端以后要是加了 @JsonFormat 改成 "yyyy-MM-dd HH:mm:ss"，前端不用动。
 */

/** 拆成 { date: '2026-10-16', time: '22:22:30' } */
export function splitDateTime(value) {
  if (!value) return { date: '', time: '' }
  const [date, time = ''] = String(value).replace('T', ' ').split(' ')
  return { date, time: time.slice(0, 8) }
}

/** 拼成一行：2026-10-16 22:22:30 */
export function formatDateTime(value) {
  const { date, time } = splitDateTime(value)
  if (!date) return ''
  return time ? `${date} ${time}` : date
}
