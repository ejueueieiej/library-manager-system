<template>
  <div class="my-files">
    <!-- ================= 工具条 ================= -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-h">我的书库</h2>
        <span class="page-sub">上传你自己的 txt，随时接着上次读</span>
      </div>

      <div class="toolbar-right">
        <el-upload
          :show-file-list="false"
          :http-request="doUpload"
          accept=".txt"
          :before-upload="beforeUpload"
        >
          <el-button type="primary" :loading="uploading">上传 txt</el-button>
        </el-upload>
      </div>
    </div>

    <!-- ================= 表格 ================= -->
    <div class="table-area">
      <el-skeleton v-if="loading && !list.length" :rows="6" animated class="table-skeleton" />
      <el-table v-else v-loading="loading" :data="list" row-key="id" @row-click="openReader">
        <el-table-column label="文件名" min-width="260">
          <template #default="{ row }">
            <a class="file-name is-link">{{ row.title }}</a>
            <div class="file-meta">
              {{ row.chapterCount }} 章 · {{ formatWords(row.wordCount) }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="上传时间" width="150">
          <template #default="{ row }">
            <date-time-cell class="muted" :value="row.createTime" />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="160" align="right">
          <template #default="{ row }">
            <el-button link @click.stop="openReader(row)">阅读</el-button>
            <!-- 重命名和删除都收进「管理」，免得操作列越堆越多、也免得误点删除 -->
            <el-button link @click.stop="openManage(row)">管理</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="还没有上传过 txt，点右上角「上传 txt」开始" />
        </template>
      </el-table>
    </div>

    <!-- ================= 分页 ================= -->
    <div v-if="total > 0" class="pager">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="loadList"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- ================= 管理文件：重命名 + 删除 ================= -->
    <el-dialog v-model="manageVisible" title="管理文件" width="440px">
      <div v-if="currentFile" class="manage">
        <div class="manage-row">
          <div class="manage-k">文件名</div>
          <el-input
            v-model="editTitle"
            maxlength="200"
            show-word-limit
            placeholder="给这个文件起个名字"
            @keyup.enter="saveTitle"
          />
        </div>

        <div class="manage-row">
          <div class="manage-k">内容</div>
          <div class="manage-v">
            {{ currentFile.chapterCount }} 章 · {{ formatWords(currentFile.wordCount) }}
          </div>
        </div>
      </div>

      <template #footer>
        <!-- 删除挪到这儿：收在「管理」里面，列表上不会误点 -->
        <el-button type="danger" plain class="manage-del" @click="handleDelete">
          删除文件
        </el-button>
        <el-button @click="manageVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTitle">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteReaderFile, pageReaderFiles, updateReaderFile, uploadReaderFile } from '@/api/reader'
import DateTimeCell from '@/components/DateTimeCell.vue'

const router = useRouter()

const list = ref([])
const total = ref(0)
const loading = ref(false)
const uploading = ref(false)

const query = reactive({
  pageNum: 1,
  pageSize: 10
})

/* ---------------- 加载 ---------------- */
async function loadList() {
  loading.value = true
  try {
    const data = await pageReaderFiles({ ...query })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  query.pageNum = 1
  loadList()
}

/* ---------------- 上传 ----------------
   ★ 这里只做"能不能传"的检查，绝不读文件内容。
     内容交给后端按字节检测编码 —— 前端一 readAsText，GBK 文件就烂了。 */
const MAX_MB = 10

function beforeUpload(file) {
  const isTxt = file.name.toLowerCase().endsWith('.txt')
  if (!isTxt) {
    ElMessage.error('只支持 .txt 文件')
    return false
  }
  if (file.size > MAX_MB * 1024 * 1024) {
    ElMessage.error(`文件不能超过 ${MAX_MB}MB`)
    return false
  }
  return true
}

async function doUpload(options) {
  uploading.value = true
  try {
    const data = await uploadReaderFile(options.file)
    ElMessage.success('上传成功')

    // 后端如果返回了 id，传完直接进阅读页 —— 少一步点击
    if (data?.id) {
      router.push(`/read/file/${data.id}`)
    } else {
      loadList()
    }
  } catch (e) {
    // 提示语拦截器已经弹过
  } finally {
    uploading.value = false
  }
}

/* ---------------- 阅读 / 管理 ---------------- */
function openReader(row) {
  router.push(`/read/file/${row.id}`)
}

/* 管理弹窗：重命名和删除都收在这里 */
const manageVisible = ref(false)
const currentFile = ref(null)
const editTitle = ref('')
const saving = ref(false)

function openManage(row) {
  currentFile.value = row
  editTitle.value = row.title
  manageVisible.value = true
}

async function saveTitle() {
  const value = editTitle.value.trim()
  if (!value) {
    ElMessage.error('文件名不能为空')
    return
  }

  saving.value = true
  try {
    const data = await updateReaderFile(currentFile.value.id, value)
    const next = data?.title || value

    // 只改这一行，不整表刷新 —— 刷新会让列表跳回顶部
    const hit = list.value.find((x) => x.id === currentFile.value.id)
    if (hit) hit.title = next
    currentFile.value.title = next

    ElMessage.success('已重命名')
    manageVisible.value = false
  } catch (e) {
    // 提示语拦截器已经弹过
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  const row = currentFile.value
  if (!row) return

  try {
    await ElMessageBox.confirm(
      `确定删除《${row.title}》吗？阅读进度也会一起清掉。`,
      '删除文件',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteReaderFile(row.id)
    ElMessage.success('已删除')
    manageVisible.value = false
    if (list.value.length === 1 && query.pageNum > 1) {
      query.pageNum--
    }
    loadList()
  } catch (e) {
    // 已提示
  }
}

/* ---------------- 展示 ---------------- */
function formatWords(n) {
  const v = Number(n) || 0
  return v >= 10000 ? `${(v / 10000).toFixed(1)} 万字` : `${v} 字`
}

onMounted(loadList)
</script>

<style scoped>
.my-files {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px 14px;
  border-bottom: 1px solid var(--line);
}

.page-h {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -.01em;
}

.page-sub {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: var(--ink-4);
}

.table-area {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 24px;
}

.file-name {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--ink-1);
}

.file-name.is-link {
  cursor: pointer;
}

.file-name.is-link:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.file-meta {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-4);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 24px 16px;
  border-top: 1px solid var(--line);
}

/* ---------------- 管理弹窗 ---------------- */
.manage-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.manage-row:last-child {
  margin-bottom: 0;
}

.manage-k {
  flex: none;
  width: 56px;
  padding-top: 6px;
  font-size: 12.5px;
  color: var(--ink-4);
}

.manage-v {
  flex: 1;
  padding-top: 6px;
  font-size: 13px;
  color: var(--ink-2);
}

/* 「删除文件」推到最左边，和「取消 / 保存」拉开，避免手滑 */
.manage-del {
  margin-right: auto;
}
</style>
