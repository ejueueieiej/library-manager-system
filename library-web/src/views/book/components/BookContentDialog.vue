<template>
  <el-dialog v-model="visible" title="电子书管理" width="520px" @open="loadMeta">
    <div v-if="book" class="content-dialog">
      <div class="book-line">
        <span class="book-name">{{ book.name }}</span>
        <span class="book-isbn mono">{{ book.isbn }}</span>
      </div>

      <!-- ================= 1. 开关 ================= -->
      <div class="row">
        <div class="row-k">开启在线阅读</div>
        <div class="row-v">
          <el-switch
            :model-value="readable === 1"
            :loading="switchLoading"
            @change="toggleReadable"
          />
          <span class="row-hint">
            {{ readable === 1 ? '所有登录用户都能读' : '关闭后读者看不到「在线阅读」按钮' }}
          </span>
        </div>
      </div>

      <!-- ================= 2. 正文状态 ================= -->
      <div class="row">
        <div class="row-k">正文</div>
        <div class="row-v">
          <template v-if="meta?.hasContent">
            <span class="state-ok">已上传</span>
            <span class="row-hint tnum">{{ meta.chapterCount }} 章 · {{ formatWords(meta.wordCount) }}</span>
            <span v-if="meta.encoding" class="row-hint mono">{{ meta.encoding }}</span>
          </template>
          <span v-else class="state-none">还没有上传</span>
        </div>
      </div>

      <!-- ================= 3. 上传 / 删除 ================= -->
      <div class="row">
        <div class="row-k">操作</div>
        <div class="row-v">
          <el-upload
            :show-file-list="false"
            :http-request="doUpload"
            accept=".txt"
            :before-upload="beforeUpload"
          >
            <el-button size="small" :loading="uploading">
              {{ meta?.hasContent ? '替换正文' : '上传正文' }}
            </el-button>
          </el-upload>

          <el-button
            v-if="meta?.hasContent"
            size="small"
            link
            type="danger"
            @click="handleDelete"
          >
            删除正文
          </el-button>
        </div>
      </div>

      <!-- ================= 4. 提示 ================= -->
      <p class="hint">
        只支持 <b>.txt</b>，单个文件不超过 <b>10MB</b>。
        上传的是<strong>原始文件</strong>：编码（UTF-8 / GBK）由后端检测，
        所以 GBK 的老 txt 也能正常显示。
      </p>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        type="primary"
        :disabled="!meta?.hasContent || readable !== 1"
        @click="preview"
      >
        预览阅读页
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteBookContent,
  getReaderMeta,
  setBookReadable,
  uploadBookContent
} from '@/api/reader'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  book: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'change'])

const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const meta = ref(null)
const uploading = ref(false)
const switchLoading = ref(false)

// 以本地状态为准，开关点完立刻反馈，不等刷新
const readable = ref(props.book?.readable ?? 0)

const MAX_MB = 10

/* ---------------- 加载当前状态 ---------------- */
async function loadMeta() {
  if (!props.book?.id) return
  readable.value = props.book.readable ?? 0
  try {
    meta.value = await getReaderMeta('book', props.book.id)
  } catch (e) {
    meta.value = null
  }
}

/* ---------------- 开关 ---------------- */
async function toggleReadable(next) {
  const value = next ? 1 : 0
  switchLoading.value = true
  try {
    await setBookReadable(props.book.id, value)
    readable.value = value
    ElMessage.success(value === 1 ? '已开启在线阅读' : '已关闭在线阅读')
    emit('change')
  } catch (e) {
    // 失败不动本地状态，开关会弹回去
  } finally {
    switchLoading.value = false
  }
}

/* ---------------- 上传 ----------------
   ★ 用 el-upload 的 http-request 自己发 FormData：
     传的是【原始文件】，不是读出来的字符串。 */
function beforeUpload(file) {
  if (!file.name.toLowerCase().endsWith('.txt')) {
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
    await uploadBookContent(props.book.id, options.file)
    ElMessage.success('正文已上传')
    await loadMeta()
    emit('change')
  } catch (e) {
    // 拦截器已提示（编码识别失败、不是 txt、超大等）
  } finally {
    uploading.value = false
  }
}

/* ---------------- 删除正文 ---------------- */
async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      '删除后这本书的正文和所有读者的阅读进度都会清掉，确定吗？',
      '删除正文',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await deleteBookContent(props.book.id)
    ElMessage.success('正文已删除')
    await loadMeta()
    emit('change')
  } catch (e) {
    // 已提示
  }
}

function preview() {
  visible.value = false
  router.push(`/read/book/${props.book.id}`)
}

function formatWords(n) {
  const v = Number(n) || 0
  return v >= 10000 ? `${(v / 10000).toFixed(1)} 万字` : `${v} 字`
}
</script>

<style scoped>
.content-dialog {
  font-size: 13px;
}

.book-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding-bottom: 14px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--line);
}

.book-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -.01em;
}

.book-isbn {
  font-size: 12px;
  color: var(--ink-4);
}

.row {
  display: flex;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--row-line);
}

.row:last-of-type {
  border-bottom: none;
}

.row-k {
  flex: none;
  width: 92px;
  padding-top: 2px;
  font-size: 12.5px;
  color: var(--ink-4);
}

.row-v {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.row-hint {
  font-size: 12px;
  color: var(--ink-4);
}

.state-ok {
  font-size: 12px;
  padding: 2px 7px;
  border-radius: 99px;
  background: #000;
  color: #fff;
}

.state-none {
  font-size: 12.5px;
  color: var(--ink-3);
}

.hint {
  margin: 14px 0 0;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
  background: var(--subtle, #fafafa);
  border-radius: var(--r-sm);
}
</style>
