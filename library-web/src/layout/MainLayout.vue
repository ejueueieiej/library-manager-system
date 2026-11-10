<template>
  <!--
    主布局：左侧栏 + 顶栏 + 内容区
    设计要点（B「黑白强对比」）：
      · 没有彩色顶栏，通体白 + 发丝线分隔
      · 导航选中态是左侧一条 2px 黑线，不用彩色底块
      · 除弹窗外没有任何阴影，层级全靠 1px 线
  -->
  <div class="layout">
    <!-- ================= 左侧栏 ================= -->
    <aside class="sidebar">
      <div class="brand">
        <img class="brand-mark" src="/brand/logo.svg" alt="星河图书" width="28" height="28" />
        <span class="brand-text">
          <span class="brand-name">星河图书</span>
          <span class="brand-sub mono">LIBRARY · v4.0</span>
        </span>
      </div>

      <nav class="nav">
        <router-link to="/home" class="nav-item" active-class="is-active">
          <el-icon><House /></el-icon>
          <span>首页</span>
        </router-link>

        <div class="nav-label is-gap">浏览</div>

        <router-link to="/books" class="nav-item" active-class="is-active">
          <el-icon><Reading /></el-icon>
          <span>{{ isAdmin ? '图书管理' : '图书查询' }}</span>
        </router-link>

        <router-link to="/notices" class="nav-item" active-class="is-active">
          <el-icon><Bell /></el-icon>
          <span>通知公告</span>
          <!-- 未读红点（3.0）：数字从 /notices/unread-count 来。
               ⚠️ 它只是个提示，不是权限 —— 后端没实现时这里就是不显示，不影响别的功能 -->
          <span v-if="hasUnread" class="badge tnum">{{ badgeText }}</span>
        </router-link>

        <router-link to="/my-files" class="nav-item" active-class="is-active">
          <el-icon><Notebook /></el-icon>
          <span>我的书库</span>
        </router-link>

        <div class="nav-label is-gap">智能</div>

        <router-link to="/ai" class="nav-item" active-class="is-active">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 馆员</span>
        </router-link>

        <div class="nav-label is-gap">借阅</div>

        <router-link to="/my-borrows" class="nav-item" active-class="is-active">
          <el-icon><Tickets /></el-icon>
          <span>我的借阅</span>
        </router-link>

        <!-- 借阅管理：管理员看所有人的记录（普通用户连菜单项都不渲染） -->
        <router-link v-if="isAdmin" to="/borrows" class="nav-item" active-class="is-active">
          <el-icon><Management /></el-icon>
          <span>借阅管理</span>
        </router-link>

        <div class="nav-label is-gap">账号</div>

        <!-- 用户管理：只有管理员看得见 -->
        <router-link v-if="isAdmin" to="/users" class="nav-item" active-class="is-active">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </router-link>

        <router-link to="/profile" class="nav-item" active-class="is-active">
          <el-icon><Setting /></el-icon>
          <span>个人中心</span>
        </router-link>
      </nav>

      <div class="side-foot">
        <div class="side-foot-k">{{ roleLabel }}</div>
        <div class="side-foot-v">{{ displayName }}</div>
      </div>
    </aside>

    <!-- ================= 右侧 ================= -->
    <div class="content">
      <header class="topbar">
        <el-breadcrumb separator="/" class="crumb">
          <el-breadcrumb-item v-if="!isHome" :to="{ path: '/home' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="crumbGroup && !isHome">{{ crumbGroup }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
        </el-breadcrumb>

        <div class="topbar-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="user-chip">
              <span class="user-avatar">{{ avatarChar }}</span>
              <span class="user-name">{{ displayName }}</span>
              <span class="role-tag">{{ roleLabel }}</span>
              <span class="caret">▾</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="page">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useNoticeStore } from '@/store/notice'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()

// 登录态与角色（computed 在模板里会自动解包，脚本里要写 .value）
const { isAdmin, displayName, roleLabel, avatarChar, doLogout } = useUserStore()

/* ---------------- 通知未读红点（3.0） ----------------
 * 未读数放在 store 里（不是这个组件里），因为通知列表页读完一条也要让它降下来。
 * 进主界面就刷一次；失败了也不弹提示（请求带了 silent），只是不显示红点。
 */
const noticeStore = useNoticeStore()
const { hasUnread, state: noticeState } = noticeStore

// 超过 99 就显示 99+，否则宽度会被三位数撑开
const badgeText = computed(() =>
  noticeState.unreadCount > 99 ? '99+' : String(noticeState.unreadCount)
)

onMounted(() => {
  noticeStore.refreshUnread()
})

// 顶栏标题：图书列表这一页，两种角色叫法不同
const pageTitle = computed(() => {
  if (route.name === 'home') {
    return isAdmin.value ? '工作台' : '首页'
  }
  if (route.name === 'bookList') {
    return isAdmin.value ? '图书管理' : '图书查询'
  }
  return route.meta?.title || '星河图书'
})

const isHome = computed(() => route.name === 'home')

// 面包屑中间那一级，和侧边栏分组保持一致
const crumbGroup = computed(() => {
  switch (route.name) {
    case 'bookList':
    case 'noticeList':
      return '浏览'
    case 'myBorrows':
    case 'borrowManage':
      return '借阅'
    case 'userList':
    case 'profile':
      return '账号'
    case 'aiChat':
      return '智能'
    default:
      return ''
  }
})

// 顶栏用户菜单
async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }

  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return // 点了取消
    }

    await doLogout()
    // 换个人登录后，不该看到上一个人的通知红点
    noticeStore.clearUnread()
    router.replace('/login')
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100%;
  background: #fff;
}

/* ---------------- 左侧栏：深色磨砂玻璃 ----------------
   玻璃感不靠"背后有东西可模糊"（这块背后是纯白的 .layout，模糊纯白 = 没效果），
   而是靠三件事：半透明深色底 + 顶部微亮/底部更沉的渐变 + 边缘高光。 */
.sidebar {
  /* 深色作用域：把主题的"墨色"整体翻成浅色。
     color 是在 body 上继承下来的计算值，所以这里必须显式重设一次，
     否则 brand-name 这类没写 color 的元素会继续用黑色。 */
  --ink-1: #ffffff;
  --ink-2: rgba(255, 255, 255, .86);
  --ink-3: rgba(255, 255, 255, .62);
  --ink-4: rgba(255, 255, 255, .42);
  --ink-5: rgba(255, 255, 255, .26);
  --line: rgba(255, 255, 255, .12);
  --line-strong: rgba(255, 255, 255, .2);
  --hover: rgba(255, 255, 255, .08);
  --selected: rgba(255, 255, 255, .12);

  position: relative;
  width: 220px;
  flex: none;
  display: flex;
  flex-direction: column;
  padding: 18px 12px 14px;
  gap: 26px;
  color: var(--ink-1);
  background: linear-gradient(180deg, rgba(24, 24, 24, .92), rgba(8, 8, 8, .97));
  backdrop-filter: blur(22px) saturate(140%);
  -webkit-backdrop-filter: blur(22px) saturate(140%);
  border-right: 1px solid var(--line);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, .13),
    inset -1px 0 0 rgba(255, 255, 255, .04);
}

/* 不支持 backdrop-filter 时退回不透明深色 —— 否则半透明底会糊在白色上发灰 */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .sidebar {
    background: #141414;
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 6px;
}

/* 品牌标：logo.svg 是黑稿，深色玻璃底上反色成白 */
.brand-mark {
  width: 28px;
  height: 28px;
  flex: none;
  display: block;
  filter: invert(1);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}

.brand-name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -.01em;
}

.brand-sub {
  font-size: 10.5px;
  color: var(--ink-4);
  letter-spacing: .04em;
}

.nav {
  display: flex;
  flex-direction: column;
}

.nav-label {
  font-size: 11px;
  color: var(--ink-4);
  letter-spacing: .06em;
  padding: 0 8px 8px;
}

/* 第二、三组的小标题：和上一组拉开距离，形成分组 */
.nav-label.is-gap {
  margin-top: 18px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  font-size: 13.5px;
  color: var(--ink-3);
  text-decoration: none;
  border-left: 2px solid transparent;
  transition: color var(--ease), border-color var(--ease);
}

.nav-item:hover {
  color: var(--ink-1);
}

/* 选中态：左侧亮线 + 亮字（B 版不用彩色底块；深色底上就是反过来的"黑线黑字"） */
.nav-item.is-active {
  color: var(--ink-1);
  font-weight: 500;
  border-left-color: var(--ink-1);
}

/* 图标只做辅助识别：平时比文字淡一档，hover/选中时跟上文字 */
.nav-item .el-icon {
  font-size: 15px;
  color: var(--ink-4);
}

.nav-item:hover .el-icon {
  color: var(--ink-1);
}

.nav-item.is-active .el-icon {
  color: var(--ink-1);
}

/* 未读红点：黑白系统里用实心圆点，不用红色（红只留给"删除"和"逾期/无库存"）。
   深色底上要反过来：白底黑字 */
.badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 99px;
  background: var(--ink-1);
  color: #0a0a0a;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.side-foot {
  margin-top: auto;
  padding: 10px 8px;
  border-top: 1px solid var(--line);
}

.side-foot-k {
  font-size: 11px;
  color: var(--ink-4);
}

.side-foot-v {
  font-size: 12.5px;
  color: var(--ink-2);
  margin-top: 2px;
}

/* ---------------- 右侧 ---------------- */
.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  flex: none;
  display: flex;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--line);
}

/* ---------------- 面包屑（最后一项当标题用） ---------------- */
.crumb {
  min-width: 0;
  overflow: hidden;
}

.crumb :deep(.el-breadcrumb__inner) {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--ink-4);
  transition: color var(--ease);
}

.crumb :deep(.el-breadcrumb__inner.is-link:hover) {
  color: var(--ink-1);
}

/* 最后一项 = 当前页，当成原来的大标题 */
.crumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -.03em;
  color: var(--ink-1);
}

.crumb :deep(.el-breadcrumb__separator) {
  margin: 0 7px;
  font-weight: 400;
  color: var(--ink-5);
}

/* ---------------- 页面切换：只淡入淡出，不做位移 ----------------
   （设计系统明确排除位移/缩放，所以这里只动 opacity） */
.fade-enter-active {
  transition: opacity 140ms cubic-bezier(.4, 0, .2, 1);
}

.fade-leave-active {
  transition: opacity 90ms cubic-bezier(.4, 0, .2, 1);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.topbar-right {
  margin-left: auto;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  background: none;
  cursor: pointer;
  transition: border-color var(--ease), background var(--ease);
}

.user-chip:hover {
  border-color: var(--line-strong);
  background: var(--hover);
}

.user-avatar {
  width: 24px;
  height: 24px;
  border-radius: var(--r-sm);
  background: var(--selected);
  color: var(--ink-2);
  display: grid;
  place-items: center;
  font-size: 12px;
}

.user-name {
  font-size: 13px;
  color: var(--ink-2);
}

/* 角色标记：管理员/读者一眼可辨（黑白系统里用描边小标签，不加颜色） */
.role-tag {
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  color: var(--ink-3);
}

.caret {
  font-size: 10px;
  color: var(--ink-4);
}

.page {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
</style>
