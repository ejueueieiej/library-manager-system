import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import MainLayout from '@/layout/MainLayout.vue'
import { setUnauthorizedHandler } from '@/utils/request'
import { useNoticeStore } from '@/store/notice'
import { useUserStore } from '@/store/user'

/**
 * 路由表（2.0）
 *
 * meta 里的两个约定：
 *   public: true   —— 不需要登录（只有登录页和 404 兜底页）
 *   roles: [...]   —— 允许访问的角色；不写就是「登录了就能进」
 *
 * 后续版本继续往 MainLayout 的 children 里加：
 *   4.0 -> /reader/:id（在线阅读器，独立页面，不套布局）
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    // 注册（2.1）：和登录页一样是公开页面，不需要 token
    path: '/register',
    name: 'register',
    component: () => import('@/views/register/RegisterView.vue'),
    meta: { title: '注册', public: true }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/home',
    children: [
      {
        // 首页工作台：读者看自己的借阅 + 通知，管理员多看系统概况和逾期名单
        path: 'home',
        name: 'home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'books',
        name: 'bookList',
        component: () => import('@/views/book/BookListView.vue'),
        // 注意：这里写的是管理员的叫法，普通用户会看到「图书查询」
        // （标题按角色在 MainLayout 里动态换，见那里的 pageTitle）
        meta: { title: '图书管理' }
      },
      {
        // 3.0：我的借阅（所有登录用户都能看自己的借阅记录）
        path: 'my-borrows',
        name: 'myBorrows',
        component: () => import('@/views/borrow/MyBorrowView.vue'),
        meta: { title: '我的借阅' }
      },
      {
        // 3.0：借阅管理（管理员看所有人的记录）
        // ★ 注意它和上面是【两条完全独立的路由】，不是一个页面靠参数区分：
        //   权限判断越显眼越好，藏在一个 if 里早晚出事
        path: 'borrows',
        name: 'borrowManage',
        component: () => import('@/views/borrow/BorrowManageView.vue'),
        meta: { title: '借阅管理', roles: ['ADMIN'] }
      },
      {
        // 3.0：通知公告（两种角色都能看，只是能干的按钮不一样）
        path: 'notices',
        name: 'noticeList',
        component: () => import('@/views/notice/NoticeListView.vue'),
        meta: { title: '通知公告' }
      },
      {
        path: 'users',
        name: 'userList',
        component: () => import('@/views/user/UserListView.vue'),
        // ★ 只有管理员能进。普通用户手动在地址栏输 /users 也会被下面的守卫拦下
        meta: { title: '用户管理', roles: ['ADMIN'] }
      },
      {
        // 4.0：我的书库（用户自己上传的 txt）
        path: 'my-files',
        name: 'myFiles',
        component: () => import('@/views/reader/MyFilesView.vue'),
        meta: { title: '我的书库' }
      },
      {
        // 5.0：AI 馆员（当前为单轮对话，不存历史）
        path: 'ai',
        name: 'aiChat',
        component: () => import('@/views/ai/AiChatView.vue'),
        meta: { title: 'AI 馆员' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心' }
      }
    ]
  },
  {
    // 4.0 阅读页：★ 故意做成【平级路由】，不套 MainLayout
    // 沉浸感是这个页面的全部价值 —— 留着 220px 侧边栏就没有"在读书"的感觉了。
    // type = 'book'（书籍正文）| 'file'（用户上传的 txt）
    path: '/read/:type/:id',
    name: 'reader',
    component: () => import('@/views/reader/ReaderView.vue'),
    meta: { title: '阅读' }
  },
  {
    // 404 兜底：必须放最后
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  // history 模式：地址栏是干净的 /books 而不是 /#/books
  // ⚠️ 部署到 nginx 时必须配 try_files $uri $uri/ /index.html，否则刷新页面会 404
  history: createWebHistory(),
  routes
})

/* ============================================================
   路由守卫：2.0 的权限总闸
   ============================================================ */
router.beforeEach(async (to) => {
  const store = useUserStore()

  // ⚠️⚠️ 血的教训：store.isLogin 是一个 ComputedRef 【对象】，不是 boolean！
  //
  // Vue 只在【模板】里自动解包 ref；在普通的 .js 代码里必须自己写 .value。
  // 不写 .value 的后果极其隐蔽：
  //     const x = ref(false)
  //     if (!x) { ... }    // x 是对象 → 恒为真 → !x 恒为 false → 这个分支永远进不去
  //
  // 我之前这里就是漏了 .value，导致"未登录就跳登录页"这条分支【从来没执行过】，
  // 表现就是：怎么都进不去登录页，打开页面直接看到图书列表。
  const loggedIn = store.isLogin.value

  // ---------- 1. 公开页面直接放行 ----------
  if (to.meta.public) {
    // 已经登录的人不该再看到登录页 / 注册页，直接送进主界面
    if ((to.name === 'login' || to.name === 'register') && loggedIn) {
      return { path: '/home' }
    }
    return true
  }

  // ---------- 2. 没登录 → 去登录页，并记住原本想去哪 ----------
  if (!loggedIn) {
    return {
      path: '/login',
      query: to.fullPath === '/' ? {} : { redirect: to.fullPath }
    }
  }

  // ---------- 3. 有 token 但还没拉过用户信息（刷新页面的情况）----------
  // 必须先拉回来，否则 isAdmin 是错的，菜单和按钮会闪一下再变
  if (!store.state.loaded) {
    try {
      await store.fetchMe()
    } catch (e) {
      // ⚠️ 这里必须【主动清掉登录态】，不能只 return 登录页！
      //
      // 原因：上面 /login 的守卫里有一条"已登录的人不该再看登录页 → 弹回 /books"。
      // 如果这里不清 token，就会变成：
      //     /books → fetchMe 失败 → /login → 守卫发现 isLogin 还是 true → 弹回 /books → ...
      // 来回死循环，用户的表现就是「怎么都进不去登录页」。
      //
      // 这个边界是实机联调时才暴露的：当 /auth/me 还没实现时，
      // token 校验必然失败，而它又清不掉，就把登录页彻底锁死了。
      //
      // 注意：401 的情况 request.js 已经清过一次了，这里再清一次是幂等的，无害。
      store.clear()
      return { path: '/login' }
    }
  }

  // ---------- 4. 角色校验 ----------
  const roles = to.meta.roles
  if (roles && !roles.includes(store.state.user?.role)) {
    ElMessage.error('没有访问该页面的权限')
    return { path: '/home' }
  }

  return true
})

/* ============================================================
   401 统一处理：由 request.js 在收到 401 时回调进来
   （放在这里注册，是为了避开 request ↔ router 的循环依赖）
   ============================================================ */
setUnauthorizedHandler(() => {
  const store = useUserStore()
  store.clear()

  // 登录态都没了，通知红点也该跟着清掉（否则退回登录页时还挂着一个数字）
  useNoticeStore().clearUnread()

  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({
      path: '/login',
      query: current.fullPath === '/' ? {} : { redirect: current.fullPath }
    })
  }
})

// 统一维护浏览器标签页标题
router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - 图书管理系统` : '图书管理系统'
})

export default router
