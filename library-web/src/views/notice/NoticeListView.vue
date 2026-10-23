<template>
  <div class="page-root">
    <!-- ================= 工具条 ================= -->
    <div class="toolbar">
      <!-- 置顶筛选：和图书列表的「全部/在架/下架」同一个交互套路 -->
      <div class="segmented">
        <el-button
          v-for="opt in topOptions"
          :key="opt.label"
          :type="queryParams.onlyTop === opt.value ? 'primary' : 'default'"
          @click="handleTopFilter(opt.value)"
        >
          {{ opt.label }}
        </el-button>
      </div>

      <div class="filter-set">
        <el-input
          v-model="queryParams.keyword"
          placeholder="标题 / 正文关键字"
          clearable
          class="w-200"
          @keyup.enter="handleSearch"
        />

        <el-select v-model="queryParams.type" placeholder="全部类型" clearable class="w-120">
          <el-option
            v-for="item in NOTICE_TYPES"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <!-- 状态筛选只给管理员：普通读者传了也会被后端强制成"已发布" -->
        <el-select
          v-if="isAdmin"
          v-model="queryParams.status"
          placeholder="全部状态"
          clearable
          class="w-120"
        >
          <el-option label="已发布" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>

        <el-button @click="handleSearch">查询</el-button>
        <el-button link class="reset-btn" @click="handleReset">重置</el-button>
      </div>

      <!-- 发布通知：只有管理员有 -->
      <div v-if="isAdmin" class="toolbar-right">
        <el-button type="primary" @click="handleAdd">发布通知</el-button>
      </div>
    </div>

    <!-- ================= 表格 ================= -->
    <div class="table-area">
      <!-- 首次加载用骨架屏 -->
      <el-skeleton v-if="loading && !list.length" :rows="8" animated class="table-skeleton" />
      <el-table v-else v-loading="loading" :data="list" row-key="id" @row-click="handleRowClick">
        <!-- 未读小圆点：只有普通读者需要（管理员不参与已读统计，后端给他的恒为 1） -->
        <el-table-column v-if="!isAdmin" label="" width="34" align="center">
          <template #default="{ row }">
            <span class="unread-dot" :class="{ 'is-read': row.isRead === 1 }"></span>
          </template>
        </el-table-column>

        <el-table-column label="标题" min-width="330">
          <template #default="{ row }">
            <div class="title-line">
              <span v-if="row.isTop === 1" class="pin-tag">置顶</span>
              <!-- 未读的标题加重，扫一眼就知道哪几条是新的 -->
              <a
                class="notice-title is-link"
                :class="{ 'is-unread': !isAdmin && row.isRead === 0 }"
                @click="handleDetail(row)"
              >
                {{ row.title }}
              </a>
              <span v-if="row.status === 0" class="off-tag">已下架</span>
            </div>
            <div class="notice-summary">{{ row.summary || '—' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="84">
          <template #default="{ row }">
            <span class="type-chip">{{ noticeTypeLabel(row.type) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="发布人" width="110">
          <template #default="{ row }">
            <span :class="{ muted: !row.publisherName }">{{ row.publisherName || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">
            <date-time-cell class="muted" :value="row.publishTime" />
          </template>
        </el-table-column>

        <el-table-column label="操作" :width="isAdmin ? 182 : 76" align="right">
          <template #default="{ row }">
            <template v-if="isAdmin">
              <el-button link @click="handleTop(row)">
                {{ row.isTop === 1 ? '取消置顶' : '置顶' }}
              </el-button>
              <el-button link @click="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <el-button v-else link @click="handleDetail(row)">查看</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无通知" />
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
        @current-change="loadNotices"
      />
    </div>

    <!-- ================= 发布 / 编辑 ================= -->
    <notice-form-dialog
      v-model="formVisible"
      :notice-id="currentNoticeId"
      @success="handleFormSuccess"
    />

    <!-- ================= 详情 ================= -->
    <notice-detail-drawer
      v-model="detailVisible"
      :notice-id="detailId"
      @loaded="handleDetailLoaded"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import DateTimeCell from '@/components/DateTimeCell.vue'
import NoticeDetailDrawer from './components/NoticeDetailDrawer.vue'
import NoticeFormDialog from './components/NoticeFormDialog.vue'
import {
  NOTICE_TYPES,
  deleteNotice,
  noticeTypeLabel,
  pageNotices,
  updateNoticeTop
} from '@/api/notice'
import { useNoticeStore } from '@/store/notice'
import { useUserStore } from '@/store/user'

// 角色决定界面上出现哪些按钮。
// ⚠️ 这只是"不显示用不上的东西"，真正的权限控制在后端（@RequireAdmin）——
//    前端藏按钮挡不住用 Apifox 直接打接口的人。
const { isAdmin } = useUserStore()
const noticeStore = useNoticeStore()

/* ---------------- 查询条件（和接口 1 的参数一一对应） ---------------- */
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  type: null,
  onlyTop: null,
  status: null // 普通读者不发它；发了后端也不认
})

const topOptions = [
  { label: '全部', value: null },
  { label: '只看置顶', value: 1 }
]

/* ---------------- 列表数据 ---------------- */
const list = ref([])
const total = ref(0)
const loading = ref(false)

/* ---------------- 弹窗 / 抽屉 ---------------- */
const formVisible = ref(false)
const currentNoticeId = ref(null)
const detailVisible = ref(false)
const detailId = ref(null)

/* ---------------- 查询 ---------------- */
async function loadNotices() {
  loading.value = true
  try {
    const data = await pageNotices({ ...queryParams })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (e) {
    // 接口报错（含"后端还没实现"的 404）时清空列表，避免显示上一次的旧数据
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleTopFilter(value) {
  queryParams.onlyTop = value
  handleSearch()
}

function handleSearch() {
  queryParams.pageNum = 1 // 换条件后必须回到第一页
  loadNotices()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.type = null
  queryParams.onlyTop = null
  queryParams.status = null
  handleSearch()
}

function handleSizeChange() {
  queryParams.pageNum = 1
  loadNotices()
}

/* ---------------- 详情 ---------------- */
function handleDetail(row) {
  detailId.value = row.id
  detailVisible.value = true
}

/**
 * 详情拉取成功后的回调
 *
 * 普通读者打开详情时，后端已经顺手把这条标成"已读"了 ——
 * 所以这里要把本地这行也改成已读，并让侧边栏的红点跟着降下去，
 * 否则会出现"看完了、回到列表，圆点还在"的尴尬。
 *
 * 红点为什么重新去后端拉一次，而不是本地减一？
 * 因为"到底少了几条"以服务端为准最省心（本地减一多读几条容易算错）。
 * 接口 3 就一句 SQL，多这一次请求完全不心疼。
 */
function handleDetailLoaded() {
  const row = list.value.find((item) => item.id === detailId.value)
  if (row && row.isRead === 0) {
    row.isRead = 1
    noticeStore.refreshUnread()
  }
}

/**
 * 整行点击 → 打开详情
 *
 * 行内自带交互的控件（按钮 / 链接 / 输入框）点到时要跳过，
 * 否则会出现"点一下删除，详情抽屉也一起弹出来"。
 */
function handleRowClick(row, column, event) {
  const target = event?.target
  if (target?.closest?.('.el-checkbox, .el-switch, .el-button, button, a, input')) {
    return
  }
  handleDetail(row)
}

/* ---------------- 新增 / 编辑 ---------------- */
function handleAdd() {
  currentNoticeId.value = null
  formVisible.value = true
}

function handleEdit(row) {
  currentNoticeId.value = row.id
  formVisible.value = true
}

function handleFormSuccess() {
  // 新增的排在最前面（按发布时间倒序），回第一页才看得到
  if (currentNoticeId.value === null) {
    queryParams.pageNum = 1
  }
  loadNotices()
}

/* ---------------- 置顶 / 取消置顶（接口 6） ---------------- */
async function handleTop(row) {
  const target = row.isTop === 1 ? 0 : 1
  try {
    await updateNoticeTop(row.id, target)
  } catch (e) {
    return // 失败提示由拦截器统一弹出
  }

  ElMessage.success(target === 1 ? '已置顶' : '已取消置顶')
  // ★ 必须重新查询：置顶会改变排序（置顶的永远排最前）
  loadNotices()
}

/* ---------------- 删除（接口 7） ---------------- */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除通知「${row.title}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return // 点了取消
  }

  await deleteNotice(row.id)
  ElMessage.success('删除成功')
  // 当前页删空了就往前翻一页（第 1 页不用翻）
  if (list.value.length === 1 && queryParams.pageNum > 1) {
    queryParams.pageNum--
  }
  loadNotices()
  // 删掉的通知如果还没读过，未读数也会变，顺手刷一下
  noticeStore.refreshUnread()
}

onMounted(() => {
  loadNotices()
  // 直接落在这一页时也把红点刷准（主界面挂载时已经刷过一次，这里是兜底）
  noticeStore.refreshUnread()
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

.w-200 { width: 200px; }
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

:deep(.el-table__body tr) {
  cursor: pointer;
}

/* 未读圆点：实心黑点；已读留一个占位，保证标题左边对齐 */
.unread-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #000;
  vertical-align: middle;
}

.unread-dot.is-read {
  background: transparent;
}

.title-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.notice-title {
  color: var(--ink-2);
  line-height: 1.4;
}

/* 未读的标题用纯黑加粗，和已读的灰字形成对比 */
.notice-title.is-unread {
  color: #000;
  font-weight: 600;
}

.notice-title.is-link {
  cursor: pointer;
}

.notice-title.is-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 置顶：黑白系统里的"实心"用法，和用户管理页的角色标签一致 */
.pin-tag {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 3px 5px;
  background: #000;
  color: #fff;
  border-radius: var(--r-sm);
}

.off-tag {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 3px 5px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  color: var(--ink-3);
}

.notice-summary {
  margin-top: 3px;
  font-size: 12px;
  color: var(--ink-4);
  line-height: 1.5;
  /* 摘要最多显示一行，超出省略 —— 不然有的通知能撑高好几行，表格就花了 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-chip {
  font-size: 12px;
  padding: 2px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 99px;
  color: var(--ink-2);
}

.muted {
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
