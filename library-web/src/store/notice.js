import { computed, reactive } from 'vue'
import { getUnreadCount } from '@/api/notice'
import { useUserStore } from '@/store/user'

/**
 * 通知未读数（3.0）
 *
 * 为什么单独一个 store？
 * 因为它是【跨页面共享】的状态：侧边栏要显示红点，通知列表读完之后红点要立刻少一个。
 * 如果各自组件里各存一份，就会出现"读完通知回到别的页面，红点还在"。
 *
 * 和 user.js 一样：模块级 reactive 做单例，不引入 pinia。
 */

const state = reactive({
  /** 未读通知数；0 表示没有红点 */
  unreadCount: 0,
  /** 是否已经成功取过一次（避免每次切页面都请求；也方便调试时看出到底请求没请求） */
  loaded: false
})

const hasUnread = computed(() => state.unreadCount > 0)

/**
 * 去后端拉一次未读数
 *
 * - 没登录直接归零，不发请求
 * - 请求失败也归零（后端没实现时是 404）
 *   ★ 失败时清成 0 而不是保留旧值：宁可少一个红点，也不要显示一个永远点不掉的红点
 * - 请求带了 silent，失败不会弹红字
 */
async function refreshUnread() {
  const userStore = useUserStore()

  if (!userStore.isLogin.value) {
    state.unreadCount = 0
    return 0
  }

  try {
    // 后端返回的 data 是裸数字
    const count = await getUnreadCount()
    state.unreadCount = Number(count) || 0
  } catch (e) {
    state.unreadCount = 0
  } finally {
    state.loaded = true
  }

  return state.unreadCount
}

/** 本地减一（读完一条时用，省一次请求）；不会减成负数 */
function decreaseUnread() {
  if (state.unreadCount > 0) {
    state.unreadCount--
  }
}

/** 清空（退出登录时调用，否则换个账号登录还会看到上一个人的红点） */
function clearUnread() {
  state.unreadCount = 0
  state.loaded = false
}

export function useNoticeStore() {
  return {
    state,
    hasUnread,
    refreshUnread,
    decreaseUnread,
    clearUnread
  }
}
