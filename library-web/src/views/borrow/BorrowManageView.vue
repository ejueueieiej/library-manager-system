<template>
  <div class="page-root">
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

      <div class="filter-set">
        <el-input
          v-model="queryParams.username"
          placeholder="借阅人用户名"
          clearable
          class="w-150"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="queryParams.bookName"
          placeholder="书名"
          clearable
          class="w-160"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="w-120">
          <el-option label="借出中" :value="1" />
          <el-option label="已归还" :value="2" />
          <el-option label="已取消" :value="3" />
        </el-select>

        <el-button @click="handleSearch">查询</el-button>
        <el-button link class="reset-btn" @click="handleReset">重置</el-button>
      </div>

      <div class="toolbar-right">
        <el-button @click="loadBorrows">刷新</el-button>
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
        <el-table-column label="借阅人" width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-name">{{ row.userName || row.username || '—' }}</div>
              <div class="user-account mono">{{ row.username || '—' }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="图书" min-width="220">
          <template #default="{ row }">
            <div class="book-name" :class="{ muted: !row.bookName }">
              {{ row.bookName || '（图书已删除）' }}
            </div>
            <div class="book-isbn mono">{{ row.bookIsbn || '—' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="借出时间" width="158">
          <template #default="{ row }">
            <date-time-cell class="muted" :value="row.borrowTime" />
          </template>
        </el-table-column>

        <el-table-column label="应还时间" width="158">
          <template #default="{ row }">
            <date-time-cell
              :value="row.dueTime"
              :class="{ 'is-alert': row.status === 1 && row.overdueDays > 0 }"
            />
          </template>
        </el-table-column>

        <el-table-column label="归还时间" width="158">
          <template #default="{ row }">
            <date-time-cell class="muted" :value="row.returnTime" />
          </template>
        </el-table-column>

        <el-table-column label="状态" width="150">
          <template #default="{ row }">
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

        <el-table-column label="续借" width="72" align="center">
          <template #default="{ row }">
            <span class="tnum muted">{{ row.renewCount }} 次</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="206" align="right">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <el-button link @click="openReturn(row)">确认归还</el-button>
              <el-button link type="danger" @click="handleCancel(row)">撤销借阅</el-button>
            </template>

            <!-- 借出中的记录不能删（删了库存会永远少一本），所以按钮置灰并说明原因 -->
            <el-tooltip
              :disabled="row.status !== 1"
              content="借出中的记录不能删除，请先「确认归还」或「撤销借阅」"
              placement="top"
            >
              <span>
                <el-button
                  link
                  type="danger"
                  :disabled="row.status === 1"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无借阅记录" />
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
        layout="sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="loadBorrows"
      />
    </div>

    <!-- ================= 确认归还 ================= -->
    <el-dialog v-model="returnVisible" title="确认归还" width="460px" :close-on-click-modal="false">
      <div class="return-line">
        借阅人：<b>{{ returnRow?.userName || returnRow?.username }}</b><br />
        图书：《{{ returnRow?.bookName || '（图书已删除）' }}》
      </div>

      <el-form label-width="96px" class="return-form">
        <el-form-item label="实际归还日期">
          <el-date-picker
            v-model="returnDate"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <div class="return-warn">
        书是前几天还的、今天才录入的话，请把日期改成实际归还那天 —— 它决定这笔借阅算不算逾期。
      </div>

      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="returning" @click="doReturn">确认归还</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DateTimeCell from '@/components/DateTimeCell.vue'
import { cancelBorrow, deleteBorrow, pageAllBorrows, returnBook } from '@/api/borrow'

/* ---------------- 筛选 ---------------- */
const filterOptions = [
  { label: '全部', status: null, overdue: null },
  { label: '借出中', status: 1, overdue: null },
  { label: '已归还', status: 2, overdue: null },
  { label: '已取消', status: 3, overdue: null },
  { label: '只看逾期', status: 1, overdue: 1 }
]
const activeFilter = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  bookName: '',
  status: null,
  overdue: null
})

/* ---------------- 数据 ---------------- */
const list = ref([])
const total = ref(0)
const loading = ref(false)

/* ---------------- 查询 ---------------- */
async function loadBorrows() {
  loading.value = true
  try {
    // ★ 注意是 pageAllBorrows（管理员接口），不是 pageMyBorrows
    const data = await pageAllBorrows({ ...queryParams })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleFilter(index) {
  activeFilter.value = index
  const opt = filterOptions[index]
  queryParams.status = opt.status
  queryParams.overdue = opt.overdue
  queryParams.pageNum = 1
  loadBorrows()
}

function handleSearch() {
  queryParams.pageNum = 1
  loadBorrows()
}

function handleReset() {
  activeFilter.value = 0
  queryParams.username = ''
  queryParams.bookName = ''
  queryParams.status = null
  queryParams.overdue = null
  handleSearch()
}

function handleSizeChange() {
  queryParams.pageNum = 1
  loadBorrows()
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

/* ---------------- 确认归还（接口 4，仅管理员） ---------------- */
const returnVisible = ref(false)
const returnRow = ref(null)
const returnDate = ref('')
const returning = ref(false)

function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function openReturn(row) {
  returnRow.value = row
  // 默认今天；书是前几天还的就手动改，改完直接决定这笔算不算逾期
  returnDate.value = todayStr()
  returnVisible.value = true
}

async function doReturn() {
  returning.value = true
  try {
    await returnBook(returnRow.value.id, returnDate.value || undefined)
  } catch (e) {
    return // 失败提示由拦截器统一弹出
  } finally {
    returning.value = false
  }

  ElMessage.success('归还成功')
  returnVisible.value = false
  loadBorrows()
}

/* ---------------- 撤销借阅（接口 7，仅管理员） ---------------- */
async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤销「${row.userName || row.username}」对《${row.bookName || '该图书'}》的借阅吗？撤销后这本书会重新回到可借库存。`,
      '撤销借阅',
      {
        type: 'warning',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return // 点了取消
  }

  try {
    await cancelBorrow(row.id)
  } catch (e) {
    return
  }

  ElMessage.success('已撤销该笔借阅')
  loadBorrows()
}

/* ---------------- 删除记录（接口 8，仅管理员，备用通道） ---------------- */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认永久删除「${row.userName || row.username}」对《${row.bookName || '该图书'}》的这条借阅记录吗？\n\n删除后无法恢复，只建议用于清理测试数据或订正误操作。`,
      '删除借阅记录',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return // 点了取消
  }

  try {
    await deleteBorrow(row.id)
  } catch (e) {
    return // 失败提示由拦截器统一弹出
  }

  ElMessage.success('删除成功')
  // 当前页删空了就往前翻一页
  if (list.value.length === 1 && queryParams.pageNum > 1) {
    queryParams.pageNum--
  }
  loadBorrows()
}

onMounted(loadBorrows)
</script>

<style scoped>
.page-root {
  display: flex;
  flex-direction: column;
  min-height: 100%;
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

.filter-set {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.w-150 { width: 150px; }
.w-160 { width: 160px; }
.w-120 { width: 120px; }

.reset-btn {
  color: var(--ink-4);
}

.reset-btn:hover {
  color: var(--ink-1);
}

.toolbar-right {
  margin-left: auto;
}

/* ---------------- 表格 ---------------- */
.table-area {
  flex: 1;
  padding: 0 24px;
}

.user-cell {
  line-height: 1.4;
}

.user-name {
  color: #000;
  font-weight: 500;
}

.user-account {
  font-size: 11.5px;
  color: var(--ink-4);
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

/* ---------------- 确认归还弹窗 ---------------- */
.return-line {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--ink-2);
}

.return-line b {
  color: #000;
  font-weight: 600;
}

.return-form {
  margin-top: 16px;
}

.return-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.return-warn {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-4);
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
