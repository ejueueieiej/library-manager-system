<template>
  <div class="page-root">
    <!-- ================= 页头统计 =================
         三个数字来自接口 3（/borrows/my/summary）。
         为什么不让前端按列表算？因为列表只有当前一页 —— 跨页就算错了。 -->
    <div class="summary-bar">
      <div class="stats">
        <div class="stat">
          <div class="stat-k">当前在借</div>
          <div class="stat-v tnum"><b>{{ summary.borrowingCount }}</b><span class="unit">本</span></div>
        </div>
        <div class="stat">
          <div class="stat-k">已逾期</div>
          <div class="stat-v tnum" :class="{ 'is-alert': summary.overdueCount > 0 }">
            <b>{{ summary.overdueCount }}</b><span class="unit">本</span>
          </div>
        </div>
        <div class="stat">
          <div class="stat-k">累计借阅</div>
          <div class="stat-v tnum"><b>{{ summary.totalCount }}</b><span class="unit">本</span></div>
        </div>
      </div>

      <div class="rule-tip">
        <div>借期 <b>30</b> 天 · 可续借 <b>1</b> 次（每次 +30 天）· 同时最多借 <b>5</b> 本</div>
        <div class="return-tip">归还请到图书馆办理，由管理员确认后状态才会更新</div>
      </div>
    </div>

    <!-- ================= 工具条 ================= -->
    <div class="toolbar">
      <div class="segmented">
        <el-button
          v-for="(opt, index) in filterOptions"
          :key="opt.label"
          :type="activeFilter === index ? 'primary' : 'default'"
          @click="handleFilter(index)"
        >
          {{ opt.label }}
        </el-button>
      </div>

      <div class="toolbar-right">
        <el-button @click="loadAll">刷新</el-button>
        <el-button type="primary" @click="goBooks">去借书</el-button>
      </div>
    </div>

    <!-- ================= 表格 ================= -->
    <div class="table-area">
      <!-- 首次加载用骨架屏 -->
      <el-skeleton v-if="loading && !list.length" :rows="8" animated class="table-skeleton" />
      <el-table
        v-else
        v-loading="loading"
        :data="list"
        row-key="id"
        :row-class-name="rowClassName"
      >
        <el-table-column label="图书" min-width="240">
          <template #default="{ row }">
            <div class="book-cell">
              <img v-if="row.bookCoverUrl" :src="row.bookCoverUrl" class="cover-img" alt="" />
              <span v-else class="cover-mark">{{ coverChar(row) }}</span>
              <div class="book-text">
                <!-- 书被管理员删掉了：记录还在，书名会是 null -->
                <div class="book-name" :class="{ muted: !row.bookName }">
                  {{ row.bookName || '（图书已删除）' }}
                </div>
                <div class="book-isbn mono">{{ row.bookIsbn || '—' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="借出时间" width="160">
          <template #default="{ row }">
            <date-time-cell class="muted" :value="row.borrowTime" />
          </template>
        </el-table-column>

        <el-table-column label="应还时间" width="160">
          <template #default="{ row }">
            <date-time-cell
              :value="row.dueTime"
              :class="{ 'is-alert': row.status === 1 && row.overdueDays > 0 }"
            />
          </template>
        </el-table-column>

        <el-table-column label="状态" width="152">
          <template #default="{ row }">
            <!-- 逾期不是 status 里的一个值，而是"借出中 + 过期了"，
                 由后端算好 overdueDays 给前端 -->
            <div v-if="row.status === 1 && row.overdueDays > 0" class="status-cell is-alert">
              <span class="status-dot"></span>
              <span>已逾期 <b class="tnum">{{ row.overdueDays }}</b> 天</span>
            </div>
            <div v-else class="status-cell">
              <span class="status-dot" :class="row.status === 1 ? 'is-on' : 'is-off'"></span>
              <span>{{ statusText(row) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="归还时间" width="160">
          <template #default="{ row }">
            <date-time-cell class="muted" :value="row.returnTime" />
          </template>
        </el-table-column>

        <!-- 用户没有还书权限：归还由管理员在「借阅管理」里确认 -->
        <el-table-column label="操作" width="88" align="right">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <!-- 按钮禁用时不会触发鼠标事件，所以外面要套一层 span 才能出 tooltip -->
              <el-tooltip
                :disabled="row.canRenew === 1"
                :content="renewTip(row)"
                placement="top"
              >
                <span>
                  <el-button link :disabled="row.canRenew !== 1" @click="handleRenew(row)">
                    续借
                  </el-button>
                </span>
              </el-tooltip>
            </template>
            <span v-else class="muted">{{ row.status === 3 ? '已取消' : '已完成' }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="还没有借阅记录" />
        </template>
      </el-table>
    </div>

    <!-- ================= 分页 ================= -->
    <div class="table-foot">
      <span class="foot-total">共 <b class="tnum">{{ total }}</b> 条</span>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="loadBorrows"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DateTimeCell from '@/components/DateTimeCell.vue'
import { getMyBorrowSummary, pageMyBorrows, renewBook } from '@/api/borrow'

const router = useRouter()

/* ---------------- 筛选（4 个预设，避免两个参数各选一半的诡异组合） ---------------- */
const filterOptions = [
  { label: '全部', status: null, overdue: null },
  { label: '借出中', status: 1, overdue: null },
  { label: '已归还', status: 2, overdue: null },
  { label: '已取消', status: 3, overdue: null },
  { label: '已逾期', status: 1, overdue: 1 }
]
const activeFilter = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  status: null,
  overdue: null
})

/* ---------------- 数据 ---------------- */
const list = ref([])
const total = ref(0)
const loading = ref(false)
const summary = reactive({
  borrowingCount: 0,
  overdueCount: 0,
  totalCount: 0
})

function coverChar(row) {
  return row.bookName ? row.bookName.trim().charAt(0) : '—'
}

/** 已归还的还区分"按期归还"和"逾期 N 天后归还"（overdueDays 由后端按 returnTime 算） */
function statusText(row) {
  if (row.status === 1) return '借出中'
  if (row.status === 3) return '已取消'
  return row.overdueDays > 0 ? `逾期 ${row.overdueDays} 天后归还` : '已归还'
}

/** 逾期行加个 class，用来在行首画红点 + 铺一层浅红底 */
function rowClassName({ row }) {
  return row.status === 1 && row.overdueDays > 0 ? 'row-overdue' : ''
}

/** 续借按钮为什么不能点（canRenew = 0 时给个说法，别让用户干瞪眼） */
function renewTip(row) {
  if (row.overdueDays > 0) return '图书已逾期，请先归还后再重新借阅'
  if (row.renewCount >= 1) return '每本图书只能续借 1 次'
  return ''
}

/* ---------------- 查询 ---------------- */
async function loadBorrows() {
  loading.value = true
  try {
    const data = await pageMyBorrows({ ...queryParams })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    const data = await getMyBorrowSummary()
    summary.borrowingCount = data?.borrowingCount ?? 0
    summary.overdueCount = data?.overdueCount ?? 0
    summary.totalCount = data?.totalCount ?? 0
  } catch (e) {
    // 统计拿不到不影响列表，保持 0 就好
  }
}

/** 列表 + 统计一起刷（还书/续借之后两个都会变） */
function loadAll() {
  loadBorrows()
  loadSummary()
}

function handleFilter(index) {
  activeFilter.value = index
  const opt = filterOptions[index]
  queryParams.status = opt.status
  queryParams.overdue = opt.overdue
  queryParams.pageNum = 1
  loadBorrows()
}

function handleSizeChange() {
  queryParams.pageNum = 1
  loadBorrows()
}

function goBooks() {
  router.push('/books')
}

/* ---------------- 续借（接口 5） ---------------- */
async function handleRenew(row) {
  // 不做二次确认：续借不是破坏性操作，而且"新的应还时间"后端会告诉我们
  try {
    await renewBook(row.id)
  } catch (e) {
    return
  }

  ElMessage.success('续借成功')
  loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.page-root {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* ---------------- 页头统计 ---------------- */
.summary-bar {
  display: flex;
  align-items: flex-end;
  gap: 24px;
  flex-wrap: wrap;
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
}

.stats {
  display: flex;
  gap: 40px;
}

.stat-k {
  font-size: 11.5px;
  color: var(--ink-4);
  letter-spacing: .02em;
}

.stat-v {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ink-3);
}

.stat-v b {
  font-size: 22px;
  font-weight: 600;
  color: #000;
  letter-spacing: -.02em;
}

.stat-v .unit {
  margin-left: 3px;
  font-size: 12px;
  color: var(--ink-4);
}

/* 逾期那个数字才允许变红 —— 全站唯一强调色的用法之一 */
.stat-v.is-alert b {
  color: var(--alert);
}

.rule-tip {
  margin-left: auto;
  font-size: 12px;
  color: var(--ink-4);
}

.rule-tip b {
  color: var(--ink-2);
  font-weight: 500;
}

.rule-tip .return-tip {
  margin-top: 2px;
  color: var(--ink-3);
}

/* ---------------- 工具条 ---------------- */
.toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 14px 24px;
  border-bottom: 1px solid var(--line);
}

.segmented {
  display: inline-flex;
  gap: 8px;
}

.toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

/* ---------------- 表格 ---------------- */
.table-area {
  flex: 1;
  padding: 0 24px;
}

.book-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cover-img {
  width: 26px;
  height: 34px;
  object-fit: cover;
  border-radius: 3px;
  border: 1px solid var(--line);
  display: block;
  flex: none;
}

.cover-mark {
  width: 26px;
  height: 34px;
  border-radius: 3px;
  display: grid;
  place-items: center;
  flex: none;
  background: var(--selected);
  border: 1px solid var(--line);
  color: var(--ink-3);
  font-size: 12px;
}

.book-text {
  min-width: 0;
}

.book-name {
  color: #000;
  font-weight: 500;
  line-height: 1.4;
}

.book-isbn {
  font-size: 11.5px;
  color: var(--ink-4);
  line-height: 1.4;
}

.muted {
  color: var(--ink-4);
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ink-2);
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex: none;
  background: #000;
}

.status-dot.is-off {
  background: var(--ink-5);
}

.status-cell.is-alert {
  color: var(--alert);
}

.status-cell.is-alert .status-dot {
  background: var(--alert);
}

.is-alert {
  color: var(--alert);
}

/* ---------------- 逾期行提示 ----------------
   状态列的红字在宽表格里容易被忽略，所以再给整行一个提示：
   行首一颗红点 + 极浅的红底（红色只留给"必须被一眼看到"的事） */
:deep(.el-table__body tr.row-overdue > td.el-table__cell) {
  background: var(--alert-soft);
}

:deep(.el-table__body tr.row-overdue:hover > td.el-table__cell) {
  background: #fbe9e6;
}

:deep(.row-overdue) td.el-table__cell:first-child {
  position: relative;
}

:deep(.row-overdue) td.el-table__cell:first-child::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 50%;
  width: 6px;
  height: 6px;
  margin-top: -3px;
  border-radius: 50%;
  background: var(--alert);
}

/* ---------------- 分页 ---------------- */
.table-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px 18px;
  border-top: 1px solid var(--line);
  margin-top: 4px;
}

.foot-total {
  font-size: 13px;
  color: var(--ink-3);
}

.foot-total b {
  color: #000;
  font-weight: 600;
}

.table-foot :deep(.el-pagination) {
  margin-left: auto;
}
</style>
