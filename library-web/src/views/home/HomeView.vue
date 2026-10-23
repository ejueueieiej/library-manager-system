<template>
  <div v-loading="loading" class="home">
    <!-- ---------------- 问候 ---------------- -->
    <header class="hero">
      <h2 class="hero-title">{{ greeting }}，{{ displayName }}</h2>
      <p class="hero-sub">{{ todayText }}</p>
    </header>

    <!-- ---------------- 管理员：四个数字 ---------------- -->
    <section v-if="isAdmin" class="stats">
      <div class="stat">
        <div class="stat-k"><el-icon><Reading /></el-icon>图书</div>
        <div class="stat-v tnum">{{ stats.books }}</div>
      </div>
      <div class="stat">
        <div class="stat-k"><el-icon><User /></el-icon>注册读者</div>
        <div class="stat-v tnum">{{ stats.users }}</div>
      </div>
      <div class="stat">
        <div class="stat-k"><el-icon><Tickets /></el-icon>借出中</div>
        <div class="stat-v tnum">{{ stats.borrowing }}</div>
      </div>
      <div class="stat" :class="{ 'is-alert': stats.overdue > 0 }">
        <div class="stat-k"><el-icon><Warning /></el-icon>逾期未还</div>
        <div class="stat-v tnum">{{ stats.overdue }}</div>
      </div>
    </section>

    <div class="grid">
      <!-- ---------------- 我的借阅 ---------------- -->
      <section class="card">
        <div class="card-head">
          <h3 class="card-title"><el-icon><Tickets /></el-icon>我的借阅</h3>
          <router-link to="/my-borrows" class="card-more">查看全部 →</router-link>
        </div>

        <div class="mini-stats">
          <div class="mini">
            <div class="mini-k">当前在借</div>
            <div class="mini-v tnum">{{ my.borrowingCount }}<span class="unit">本</span></div>
          </div>
          <div class="mini" :class="{ 'is-alert': my.overdueCount > 0 }">
            <div class="mini-k">已逾期</div>
            <div class="mini-v tnum">{{ my.overdueCount }}<span class="unit">本</span></div>
          </div>
          <div class="mini">
            <div class="mini-k">累计借阅</div>
            <div class="mini-v tnum">{{ my.totalCount }}<span class="unit">本</span></div>
          </div>
        </div>

        <p v-if="my.overdueCount > 0" class="alert-line">
          <el-icon><Warning /></el-icon>
          有 {{ my.overdueCount }} 本已逾期，请尽快到图书馆归还
        </p>
        <p v-else class="muted-line">归还请到图书馆办理，由管理员确认后状态才会更新。</p>
      </section>

      <!-- ---------------- 通知公告 ---------------- -->
      <section class="card">
        <div class="card-head">
          <h3 class="card-title"><el-icon><Bell /></el-icon>通知公告</h3>
          <router-link to="/notices" class="card-more">
            <!-- 管理员不记已读，未读数恒为 0，显示出来没意义 -->
            <template v-if="isAdmin">查看全部 →</template>
            <template v-else>未读 {{ unread }} 条 →</template>
          </router-link>
        </div>

        <ul v-if="notices.length" class="notice-list">
          <li v-for="item in notices" :key="item.id" class="notice-item">
            <span v-if="item.isTop === 1" class="pin">置顶</span>
            <span class="notice-text">{{ item.title }}</span>
            <span class="notice-date tnum">{{ shortDate(item.publishTime) }}</span>
          </li>
        </ul>
        <el-empty v-else description="还没有通知" />
      </section>

      <!-- ---------------- 管理员：逾期名单 ---------------- -->
      <section v-if="isAdmin" class="card is-wide">
        <div class="card-head">
          <h3 class="card-title"><el-icon><AlarmClock /></el-icon>待处理 · 逾期未还</h3>
          <router-link to="/borrows" class="card-more">去借阅管理 →</router-link>
        </div>

        <ul v-if="overdueList.length" class="overdue-list">
          <li v-for="row in overdueList" :key="row.id" class="overdue-item">
            <span class="ov-user">{{ row.userName || row.username }}</span>
            <span class="ov-book">《{{ row.bookName || '图书已删除' }}》</span>
            <span class="ov-days tnum">逾期 {{ row.overdueDays }} 天</span>
          </li>
        </ul>
        <el-empty v-else description="没有逾期的借阅，很好" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { pageBooks } from '@/api/book'
import { getMyBorrowSummary, pageAllBorrows } from '@/api/borrow'
import { getUnreadCount, pageNotices } from '@/api/notice'
import { pageUsers } from '@/api/user'
import { useUserStore } from '@/store/user'
import { splitDateTime } from '@/utils/datetime'

const { isAdmin, displayName } = useUserStore()

const loading = ref(false)

/* ---------------- 数据 ---------------- */
const my = reactive({
  borrowingCount: 0,
  overdueCount: 0,
  totalCount: 0
})

const unread = ref(0)
const notices = ref([])
const overdueList = ref([])

const stats = reactive({
  books: 0,
  users: 0,
  borrowing: 0,
  overdue: 0
})

/* ---------------- 文案 ---------------- */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

/** 通知列表里只显示 月-日 */
function shortDate(value) {
  return splitDateTime(value).date.slice(5)
}

/* ---------------- 加载 ----------------
   每个请求各自 catch：某一块挂了不影响其他块（首页最忌讳"一个接口 500 整页空白"） */
async function loadAll() {
  loading.value = true

  const tasks = [
    getMyBorrowSummary()
      .then((d) => Object.assign(my, d || {}))
      .catch(() => {}),
    getUnreadCount()
      .then((n) => {
        unread.value = Number(n) || 0
      })
      .catch(() => {}),
    pageNotices({ pageNum: 1, pageSize: 3 })
      .then((d) => {
        notices.value = d?.list ?? []
      })
      .catch(() => {})
  ]

  if (isAdmin.value) {
    tasks.push(
      pageBooks({ pageNum: 1, pageSize: 1 })
        .then((d) => {
          stats.books = d?.total ?? 0
        })
        .catch(() => {}),
      pageUsers({ pageNum: 1, pageSize: 1 })
        .then((d) => {
          stats.users = d?.total ?? 0
        })
        .catch(() => {}),
      pageAllBorrows({ status: 1, pageNum: 1, pageSize: 1 })
        .then((d) => {
          stats.borrowing = d?.total ?? 0
        })
        .catch(() => {}),
      // 逾期名单的 total 就是逾期总数，不用再单独查一次
      pageAllBorrows({ overdue: 1, pageNum: 1, pageSize: 5 })
        .then((d) => {
          overdueList.value = d?.list ?? []
          stats.overdue = d?.total ?? 0
        })
        .catch(() => {})
    )
  }

  await Promise.allSettled(tasks)
  loading.value = false
}

onMounted(loadAll)
</script>

<style scoped>
.home {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ---------------- 问候 ---------------- */
.hero-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -.02em;
}

.hero-sub {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--ink-4);
}

/* ---------------- 管理员四个数字 ---------------- */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.stat {
  background: #fff;
  padding: 15px 18px;
}

.stat-k {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--ink-4);
  margin-bottom: 6px;
}

.stat-v {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -.02em;
  line-height: 1.1;
}

/* 逾期那个数字才允许变红 */
.stat.is-alert .stat-v {
  color: var(--alert);
}

/* ---------------- 卡片网格 ---------------- */
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: 18px 20px 16px;
}

.card.is-wide {
  grid-column: 1 / -1;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 14px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -.01em;
}

.card-title .el-icon {
  font-size: 15px;
  color: var(--ink-3);
}

.card-more {
  flex: none;
  font-size: 12.5px;
  color: var(--ink-4);
  text-decoration: none;
  transition: color var(--ease);
}

.card-more:hover {
  color: var(--ink-1);
}

/* ---------------- 我的借阅：三个小数字 ---------------- */
.mini-stats {
  display: flex;
  gap: 32px;
}

.mini-k {
  font-size: 11.5px;
  color: var(--ink-4);
}

.mini-v {
  margin-top: 3px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -.02em;
}

.mini-v .unit {
  margin-left: 2px;
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-4);
}

.mini.is-alert .mini-v {
  color: var(--alert);
}

.alert-line,
.muted-line {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 14px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--row-line);
  font-size: 12.5px;
  line-height: 1.6;
}

.alert-line {
  color: var(--alert);
}

.muted-line {
  color: var(--ink-4);
}

/* ---------------- 通知列表 ---------------- */
.notice-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notice-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid var(--row-line);
}

.notice-item:last-child {
  border-bottom: none;
}

.pin {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 3px 5px;
  background: #000;
  color: #fff;
  border-radius: var(--r-sm);
}

.notice-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-date {
  flex: none;
  font-size: 11.5px;
  color: var(--ink-4);
}

/* ---------------- 逾期名单 ---------------- */
.overdue-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.overdue-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--row-line);
  font-size: 13px;
}

.overdue-item:last-child {
  border-bottom: none;
}

.ov-user {
  flex: none;
  width: 90px;
  color: #000;
  font-weight: 500;
}

.ov-book {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-2);
}

.ov-days {
  flex: none;
  color: var(--alert);
  font-size: 12.5px;
}

/* ---------------- 窄屏 ---------------- */
@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
