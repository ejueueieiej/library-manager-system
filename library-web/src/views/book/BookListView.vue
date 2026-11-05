<template>
  <div class="page-root">
    <!-- ================= 工具条 ================= -->
    <div class="toolbar">
      <!-- 状态筛选：直接用 el-button，与右侧「新增图书」同一套组件与主题，
           选中项 = 主按钮（纯黑填充），未选中 = 默认按钮（白底细描边） -->
      <div class="segmented">
        <el-button
          v-for="opt in statusOptions"
          :key="String(opt.value)"
          :type="queryParams.status === opt.value ? 'primary' : 'default'"
          @click="handleStatusFilter(opt.value)"
        >
          {{ opt.label }}
        </el-button>
      </div>

      <!-- 4.0：是否可读，和上面「状态」同一套分段控件 -->
      <div class="segmented">
        <el-button
          v-for="opt in readableOptions"
          :key="String(opt.value)"
          :type="queryParams.readable === opt.value ? 'primary' : 'default'"
          @click="handleReadableFilter(opt.value)"
        >
          {{ opt.label }}
        </el-button>
      </div>

      <div class="filter-set">
        <!-- 书号：和其他条件一样，走 GET /books 的分页查询，精确匹配 -->
        <el-input
          v-model="queryParams.isbn"
          placeholder="书号（精确）"
          clearable
          class="w-170"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="queryParams.name"
          placeholder="书名"
          clearable
          class="w-150"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="queryParams.author"
          placeholder="作者"
          clearable
          class="w-130"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="queryParams.categoryId"
          placeholder="全部分类"
          clearable
          class="w-130"
        >
          <el-option
            v-for="item in categories"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>

        <el-button @click="handleSearch">查询</el-button>
        <el-button link class="reset-btn" @click="handleReset">重置</el-button>
      </div>

      <!-- 新增图书：只有管理员有（普通用户这里什么都不显示） -->
      <div v-if="isAdmin" class="toolbar-right">
        <el-button type="primary" @click="handleAdd">新增图书</el-button>
      </div>
    </div>

    <!-- ================= 批量操作条（管理员、且有勾选时才出现） ================= -->
    <div v-if="isAdmin && selectedIds.length" class="bulkbar">
      <span class="bulk-count">已选 <b class="tnum">{{ selectedIds.length }}</b> 项</span>
      <el-button link type="danger" @click="handleBatchDelete">批量删除</el-button>
      <el-button link @click="clearSelection">取消选择</el-button>
    </div>

    <!-- ================= 表格 ================= -->
    <div class="table-area">
      <!-- 首次加载用骨架屏：比转圈稳，也不会有"表格先撑开再收缩"的跳动 -->
      <el-skeleton v-if="loading && !list.length" :rows="8" animated class="table-skeleton" />
      <el-table
        v-else
        ref="tableRef"
        v-loading="loading"
        :data="list"
        row-key="id"
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
      >
        <!-- 多选列只有管理员需要（他才有"批量删除"）；普通用户根本没有勾选这回事 -->
        <el-table-column v-if="isAdmin" type="selection" width="44" align="center" />

        <el-table-column label="封面" width="60" align="center">
          <template #default="{ row }">
            <img v-if="row.coverUrl" :src="row.coverUrl" class="cover-img" alt="" />
            <span v-else class="cover-mark">{{ (row.name || '?').trim().charAt(0) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="书名 / ISBN" min-width="220">
          <template #default="{ row }">
            <!-- 书名可点：管理员进编辑弹窗，普通用户开详情抽屉 -->
            <a class="book-name is-link" @click="handleNameClick(row)">{{ row.name }}</a>
            <!-- 4.0：这本书有电子版可在线读 -->
            <span v-if="row.readable === 1" class="read-tag">可读</span>
            <div class="book-isbn mono">{{ row.isbn }}</div>
          </template>
        </el-table-column>

        <el-table-column prop="author" label="作者" width="140" show-overflow-tooltip />
        <el-table-column prop="publisher" label="出版社" width="160" show-overflow-tooltip />

        <el-table-column label="分类" width="88">
          <template #default="{ row }">
            <span :class="{ muted: !row.categoryName }">{{ row.categoryName || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="定价" width="92" align="right">
          <template #default="{ row }">
            <span v-if="row.price !== null && row.price !== undefined" class="tnum">
              {{ Number(row.price).toFixed(2) }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="可借 / 总" width="100" align="center">
          <template #default="{ row }">
            <span class="stock tnum" :class="{ 'is-zero': row.availableNum === 0 }">
              <b>{{ row.availableNum }}</b><span class="of">/ {{ row.totalNum }}</span>
            </span>
          </template>
        </el-table-column>

        <!-- 状态：圆点 + 文字标注。开关只有管理员有（普通用户只读，看不到开关） -->
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-dot" :class="row.status === 1 ? 'is-on' : 'is-off'"></span>
              <span class="status-text">{{ row.status === 1 ? '在架' : '下架' }}</span>
              <el-switch
                v-if="isAdmin"
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                size="small"
                :loading="row.statusLoading"
                @change="handleStatusChange(row)"
              />
            </div>
          </template>
        </el-table-column>

        <!-- 操作列：管理员能编辑/删除；普通用户有详情。
             3.0 起两种角色都能「借阅」（管理员也是读者，
             这样你自己联调时不用来回换账号登） -->
        <el-table-column label="操作" :width="isAdmin ? 280 : 184" align="right">
          <template #default="{ row }">
            <!-- 4.0：只有开启了在线阅读的书才显示「阅读」，直接跳阅读页 -->
            <el-button v-if="row.readable === 1" link @click="handleRead(row)">阅读</el-button>
            <el-button link @click="handleBorrow(row)">借阅</el-button>
            <template v-if="isAdmin">
              <el-button link @click="handleEdit(row)">编辑</el-button>
              <!-- 4.0：电子书（上传正文 + 开启阅读） -->
              <el-button link @click="handleContent(row)">电子书</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <el-button v-else link @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无图书数据" />
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
        :page-sizes="[10, 20, 50, 100]"
        layout="sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="loadBooks"
      />
    </div>

    <!-- ================= 新增/编辑弹窗 ================= -->
    <book-form-dialog
      v-model="dialogVisible"
      :book-id="currentBookId"
      :categories="categories"
      @success="handleDialogSuccess"
    />

    <!-- 图书详情抽屉：普通用户的核心功能；管理员也可以用它快速看一眼 -->
    <book-detail-drawer v-model="detailVisible" :book="detailBook" @edit="handleDetailEdit" />

    <!-- 4.0：电子书管理（仅管理员打开） -->
    <book-content-dialog v-model="contentVisible" :book="contentBook" @change="loadBooks" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import BookDetailDrawer from './components/BookDetailDrawer.vue'
import BookFormDialog from './components/BookFormDialog.vue'
import BookContentDialog from './components/BookContentDialog.vue'
import { borrowBook } from '@/api/borrow'
import {
  deleteBook,
  deleteBooksBatch,
  getCategoryList,
  pageBooks,
  updateBookStatus
} from '@/api/book'
import { useUserStore } from '@/store/user'

// 角色：决定界面上显示哪些按钮。
// ⚠️ 这只是"不显示用不上的东西"，真正的权限控制在后端（@RequireAdmin 注解）——
//    前端藏按钮挡不住直接用 Apifox 调接口的人。
const { isAdmin } = useUserStore()
const router = useRouter()

/** 4.0：直接从列表进阅读页，省掉"先开详情抽屉再点在线阅读"那一步 */
function handleRead(row) {
  if (row?.id) router.push(`/read/book/${row.id}`)
}

/* ---------------- 查询条件（与接口 2 的参数一一对应） ---------------- */
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  isbn: '', // 书号，精确匹配（前端会去掉用户粘贴时带的横杠和空格）
  name: '',
  author: '',
  categoryId: null,
  status: null,
  // 4.0：是否可在线读（null=全部 / 1=可读 / 0=不可读）
  readable: null
})

// 状态分段控件：只放接口真正支持的三种（不造后端没有的筛选项）
const statusOptions = [
  { label: '全部', value: null },
  { label: '在架', value: 1 },
  { label: '下架', value: 0 }
]

// 4.0：是否可读分段控件（和状态同一套样式）
const readableOptions = [
  { label: '全部', value: null },
  { label: '可读', value: 1 },
  { label: '不可读', value: 0 }
]

/* ---------------- 列表数据 ---------------- */
const list = ref([])
const total = ref(0)
const loading = ref(false)
const categories = ref([])
const selectedIds = ref([])
const tableRef = ref(null)

/* ---------------- 弹窗状态 ---------------- */
const dialogVisible = ref(false)
const currentBookId = ref(null)

/* ---------------- 详情抽屉状态 ---------------- */
const detailVisible = ref(false)
const detailBook = ref(null)

// 4.0 电子书管理（仅管理员会打开）
const contentVisible = ref(false)
const contentBook = ref(null)

function handleContent(row) {
  contentBook.value = row
  contentVisible.value = true
}

/* ---------------- 查询分类（接口 1） ---------------- */
async function loadCategories() {
  try {
    categories.value = await getCategoryList()
  } catch (e) {
    // 分类接口没写好的时候，页面依然能用（下拉框为空），不阻塞图书列表
    categories.value = []
  }
}

/* ---------------- 查询图书列表（接口 2） ---------------- */
async function loadBooks() {
  loading.value = true
  try {
    const data = await pageBooks({ ...queryParams })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (e) {
    // 接口报错时清空列表，避免显示上一次的旧数据
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/* ---------------- 筛选交互 ---------------- */
function handleStatusFilter(value) {
  queryParams.status = value
  handleSearch()
}

// 4.0：是否可读
function handleReadableFilter(value) {
  queryParams.readable = value
  handleSearch()
}

function handleSearch() {
  queryParams.pageNum = 1 // 换条件后必须回到第一页
  loadBooks()
}

function handleReset() {
  queryParams.isbn = ''
  queryParams.name = ''
  queryParams.author = ''
  queryParams.categoryId = null
  queryParams.status = null
  queryParams.readable = null
  handleSearch()
}

function handleSizeChange() {
  queryParams.pageNum = 1
  loadBooks()
}

/* ---------------- 多选 ---------------- */
function handleSelectionChange(rows) {
  selectedIds.value = rows.map((row) => row.id)
}

function clearSelection() {
  tableRef.value?.clearSelection()
  selectedIds.value = []
}

/* ---------------- 新增 / 编辑 ---------------- */
function handleAdd() {
  currentBookId.value = null
  dialogVisible.value = true
}

function handleEdit(row) {
  currentBookId.value = row.id
  dialogVisible.value = true
}

/* ---------------- 详情抽屉 ---------------- */

/** 打开详情抽屉（两种角色都能用） */
function handleDetail(row) {
  detailBook.value = row
  detailVisible.value = true
}

/**
 * 整行点击 → 打开详情抽屉
 *
 * <p>动机：原来只有「操作」列里那个小小的「详情」按钮能点，
 * 鼠标要横跨半个屏幕去够它 —— 表格类界面里「点行」才是用户的本能动作。
 *
 * <p>⚠️ 行内自带交互的控件（多选框 / 状态开关 / 按钮 / 书名链接）点到时要【跳过】，
 * 否则会出现"点一下删除，详情抽屉也一起弹出来"。
 * 这里用 event.target.closest() 统一判断，比给每个控件都挂 @click.stop 更保险 ——
 * 以后行里再加新控件也不会漏。
 *
 * @param row    这一行的数据
 * @param column 哪一列（这里用不到）
 * @param event  原生 MouseEvent，用来判断点到了什么元素
 */
function handleRowClick(row, column, event) {
  const target = event?.target
  if (target?.closest?.('.el-checkbox, .el-switch, .el-button, button, a, input')) {
    return
  }
  handleDetail(row)
}

/** 列表里点书名：管理员进编辑弹窗，普通用户开详情抽屉 */
function handleNameClick(row) {
  if (isAdmin.value) {
    handleEdit(row)
  } else {
    handleDetail(row)
  }
}

/** 详情抽屉里点「编辑这本」—— 那个按钮只有管理员看得到 */
function handleDetailEdit(book) {
  detailVisible.value = false
  if (book) {
    handleEdit(book)
  }
}

function handleDialogSuccess() {
  // 新增后回到第一页看最新的数据，编辑后留在当前页
  if (currentBookId.value === null) {
    queryParams.pageNum = 1
  }
  loadBooks()
}

/* ---------------- 删除单条（接口 6） ---------------- */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除《${row.name}》吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return // 用户点了取消
  }

  await deleteBook(row.id)
  ElMessage.success('删除成功')
  // 如果当前页删空了，往前翻一页（第 1 页就不用翻了）
  if (list.value.length === 1 && queryParams.pageNum > 1) {
    queryParams.pageNum--
  }
  loadBooks()
}

/* ---------------- 批量删除（接口 7） ---------------- */
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedIds.value.length} 本图书吗？`,
      '批量删除确认',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  await deleteBooksBatch(selectedIds.value)
  ElMessage.success('批量删除成功')
  selectedIds.value = []
  queryParams.pageNum = 1
  loadBooks()
}

/* ---------------- 上架 / 下架（接口 8） ---------------- */
async function handleStatusChange(row) {
  // el-switch 已经把 row.status 改了，失败时要改回去
  const targetStatus = row.status
  row.statusLoading = true
  try {
    await updateBookStatus(row.id, targetStatus)
    ElMessage.success(targetStatus === 1 ? '已上架' : '已下架')
  } catch (e) {
    row.status = targetStatus === 1 ? 0 : 1 // 回滚开关状态
  } finally {
    row.statusLoading = false
  }
}

/* ---------------- 借阅（3.0） ----------------
 *
 * 为什么先在前端挡一道（下架 / 无库存），而不是直接发请求看后端怎么说？
 * 因为这两种情况【一眼就能看出来】，让用户白等一个来回再收到报错，体验很差。
 * 但要注意：前端这一道只是体验，后端必须自己再校验一遍 ——
 * 库存是随时在变的，前端手里的 availableNum 可能是几秒前的旧数据。
 */
async function handleBorrow(row) {
  if (row.status !== 1) {
    ElMessage.warning('该图书已下架，暂不可借阅')
    return
  }
  if (!row.availableNum) {
    ElMessage.warning('该图书暂无可借库存')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要借阅《${row.name}》吗？借出后请留意「我的借阅」里的应还时间。`,
      '借阅确认',
      { confirmButtonText: '确认借阅', cancelButtonText: '再想想' }
    )
  } catch {
    return // 点了"再想想"
  }

  try {
    await borrowBook(row.id)
  } catch (e) {
    // 失败提示由拦截器统一弹出（如"你已借阅该书且尚未归还"）
    return
  }

  ElMessage.success('借阅成功')
  // ★ 必须重新查询：可借册数变了，右上角那列「可借 / 总」要跟着更新
  loadBooks()
}

/* ---------------- 初始化 ---------------- */
onMounted(() => {
  loadCategories()
  loadBooks()
})
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

/* 状态筛选按钮组：样式全部交给 el-button + 主题变量，这里只管间距 */
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
.w-130 { width: 130px; }
.w-170 { width: 170px; }

.reset-btn {
  color: var(--ink-4);
}

.reset-btn:hover {
  color: var(--ink-1);
}

.toolbar-right {
  margin-left: auto;
}

/* ---------------- 批量条 ---------------- */
.bulkbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 24px;
  border-bottom: 1px solid var(--line);
  background: var(--subtle);
}

.bulk-count {
  font-size: 13px;
  color: var(--ink-3);
}

.bulk-count b {
  color: #000;
  font-weight: 600;
}

/* ---------------- 表格 ---------------- */
.table-area {
  flex: 1;
  padding: 0 24px;
}

/* 整行可点（打开详情）：给个手型光标，让用户知道"这里能点" */
:deep(.el-table__body tr) {
  cursor: pointer;
}

.cover-img {
  width: 26px;
  height: 34px;
  object-fit: cover;
  border-radius: 3px;
  border: 1px solid var(--line);
  display: block;
  margin: 0 auto;
}

.cover-mark {
  width: 26px;
  height: 34px;
  border-radius: 3px;
  display: grid;
  place-items: center;
  margin: 0 auto;
  background: var(--selected);
  border: 1px solid var(--line);
  color: var(--ink-3);
  font-size: 12px;
}

/* 4.0「可读」小标签：描边样式，不用实心黑块 —— 它只是状态提示，不该抢注意力 */
.read-tag {
  margin-left: 6px;
  font-size: 10.5px;
  line-height: 1;
  padding: 2px 5px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  color: var(--ink-4);
  white-space: nowrap;
}

.book-name {
  color: #000;
  font-weight: 500;
  line-height: 1.4;
}

/* 书名可点：hover 出现下划线，提示"这里能点进去" */
.book-name.is-link {
  cursor: pointer;
}

.book-name.is-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.book-isbn {
  font-size: 11.5px;
  color: var(--ink-4);
  line-height: 1.4;
}

.muted {
  color: var(--ink-4);
}

.stock b {
  font-weight: 600;
}

.stock .of {
  color: var(--ink-4);
  font-size: 12px;
  margin-left: 3px;
}

/* 可借为 0 时才出现颜色，这是全站极少数允许有颜色的地方 */
.stock.is-zero b {
  color: var(--alert);
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex: none;
}

.status-dot.is-on { background: #000; }
.status-dot.is-off { background: var(--ink-5); }

.status-text {
  font-size: 13px;
  color: var(--ink-2);
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
