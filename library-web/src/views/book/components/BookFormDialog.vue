<template>
  <!--
    新增 / 编辑图书弹窗
    - 新增：bookId 为 null，空表单
    - 编辑：bookId 有值，先调「查询详情」接口回填表单
    说明：提交时【故意不传 availableNum】，由后端自己算（见接口文档第 5 节）
  -->
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑图书' : '新增图书'"
    width="700px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="84px"
      label-position="right"
    >
      <!-- ================= 封面 ================= -->
      <el-form-item label="封面" prop="coverUrl">
        <div class="cover-box">
          <el-upload
            class="cover-uploader"
            :show-file-list="false"
            :http-request="handleCoverUpload"
            accept="image/png,image/jpeg,image/webp,image/gif"
          >
            <div v-loading="uploading" class="cover-inner">
              <img v-if="form.coverUrl" :src="form.coverUrl" class="cover-img" alt="封面" />
              <span v-else class="cover-empty">
                <span class="cover-plus">+</span>
                <span class="cover-hint">上传</span>
              </span>
            </div>
          </el-upload>

          <div class="cover-side">
            <div class="cover-url-row">
              <el-input
                v-model="form.coverUrl"
                placeholder="也可以直接粘贴图片地址"
                clearable
                size="small"
              />
            </div>
            <p class="cover-tip">
              jpg / png / webp / gif，不超过 5MB。<br />
              上传走后端 <code class="mono">POST /files/upload</code>，返回的
              <code class="mono">/uploads/xxx.jpg</code> 会自动填进封面地址。
            </p>
            <el-button v-if="form.coverUrl" link type="danger" size="small" @click="form.coverUrl = ''">
              清除封面
            </el-button>
          </div>
        </div>
      </el-form-item>

      <div class="divider"></div>

      <!-- ================= 基本信息 ================= -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="ISBN" prop="isbn">
            <el-input v-model="form.isbn" placeholder="书号，不可重复" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="书名" prop="name">
            <el-input v-model="form.name" placeholder="必填" maxlength="100" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="作者" prop="author">
            <el-input v-model="form.author" placeholder="必填" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="出版社" prop="publisher">
            <el-input v-model="form.publisher" placeholder="选填" maxlength="100" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="分类" prop="categoryId">
            <el-select v-model="form.categoryId" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="item in categories"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="出版日期" prop="publishDate">
            <el-date-picker
              v-model="form.publishDate"
              type="date"
              placeholder="选填"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="定价" prop="price">
            <el-input-number
              v-model="form.price"
              :min="0"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="总册数" prop="totalNum">
            <el-input-number
              v-model="form.totalNum"
              :min="1"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">在架</el-radio>
          <el-radio :value="0">下架</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="简介" prop="intro">
        <el-input v-model="form.intro" type="textarea" :rows="3" placeholder="选填" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存修改' : '确定新增' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { addBook, getBookById, updateBook } from '@/api/book'
import { uploadCover } from '@/api/file'

const props = defineProps({
  // 控制弹窗显示（配合 v-model 使用）
  modelValue: { type: Boolean, default: false },
  // 为 null 表示新增，有值表示编辑这条数据
  bookId: { type: [Number, String], default: null },
  // 分类列表由父组件传进来（父组件已经查过一次，不用重复请求）
  categories: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)

const isEdit = computed(() => props.bookId !== null && props.bookId !== undefined)

// 表单默认值：字段与后端 BookSaveDTO 一一对应
function createEmptyForm() {
  return {
    isbn: '',
    name: '',
    author: '',
    publisher: '',
    publishDate: '',
    categoryId: null,
    price: 0,
    totalNum: 1,
    coverUrl: '',
    intro: '',
    status: 1
  }
}

const form = reactive(createEmptyForm())

// 校验规则：与接口文档里的校验规则保持一致
const rules = {
  isbn: [
    { required: true, message: 'ISBN 不能为空', trigger: 'blur' },
    { max: 20, message: 'ISBN 最多 20 个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '书名不能为空', trigger: 'blur' },
    { max: 100, message: '书名最多 100 个字符', trigger: 'blur' }
  ],
  author: [
    { required: true, message: '作者不能为空', trigger: 'blur' },
    { max: 50, message: '作者最多 50 个字符', trigger: 'blur' }
  ],
  publisher: [{ max: 100, message: '出版社最多 100 个字符', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ type: 'number', min: 0, message: '定价不能为负数', trigger: 'blur' }],
  totalNum: [
    { required: true, message: '总册数不能为空', trigger: 'blur' },
    { type: 'number', min: 1, message: '总册数至少为 1', trigger: 'blur' }
  ],
  coverUrl: [{ max: 255, message: '封面地址最多 255 个字符', trigger: 'blur' }]
}

// 弹窗每次打开时：先清空，再（编辑时）拉详情
watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    resetForm()
    if (isEdit.value) {
      await loadDetail()
    }
  },
  { immediate: true }
)

function resetForm() {
  Object.assign(form, createEmptyForm())
  // 清掉上一次留下的红色校验提示（要在 DOM 更新后执行）
  formRef.value?.clearValidate()
}

async function loadDetail() {
  loading.value = true
  try {
    const data = await getBookById(props.bookId)
    // 只把表单需要的字段搬过来，避免 id / categoryName 等混进表单
    Object.assign(form, {
      isbn: data.isbn ?? '',
      name: data.name ?? '',
      author: data.author ?? '',
      publisher: data.publisher ?? '',
      publishDate: data.publishDate ?? '',
      categoryId: data.categoryId ?? null,
      price: data.price ?? 0,
      totalNum: data.totalNum ?? 1,
      coverUrl: data.coverUrl ?? '',
      intro: data.intro ?? '',
      status: data.status ?? 1
    })
  } catch (e) {
    // 错误提示已经由 request.js 的拦截器统一弹过了，这里只需要关掉弹窗
    close()
  } finally {
    loading.value = false
  }
}

/* ---------------- 封面上传（接口 9） ---------------- */
const ALLOW_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const MAX_SIZE_MB = 5

async function handleCoverUpload(options) {
  const file = options.file

  // 先在浏览器端挡一道，省一次没必要的网络往返（后端也必须再校验一次！）
  if (!ALLOW_TYPES.includes(file.type)) {
    ElMessage.error('只能上传 jpg / png / webp / gif 格式的图片')
    return
  }
  if (file.size / 1024 / 1024 > MAX_SIZE_MB) {
    ElMessage.error(`图片不能超过 ${MAX_SIZE_MB}MB`)
    return
  }

  uploading.value = true
  try {
    const data = await uploadCover(file)
    // 后端返回 { url: "/uploads/2026/10/xxx.jpg" }
    form.coverUrl = data.url
    ElMessage.success('封面上传成功')
  } catch (e) {
    // 上传失败（比如后端还没写这个接口）时给个明确提示，避免一脸懵
    if (!e?.response) {
      ElMessage.warning('上传失败：请确认后端已实现 POST /files/upload')
    }
  } finally {
    uploading.value = false
  }
}

/* ---------------- 提交 ---------------- */
async function handleSubmit() {
  // 先做前端表单校验，通过了再发请求
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateBook(props.bookId, { ...form })
      ElMessage.success('修改成功')
    } else {
      await addBook({ ...form })
      ElMessage.success('新增成功')
    }
    emit('success') // 通知父组件刷新列表
    close()
  } catch (e) {
    // 业务错误（比如 ISBN 已存在）已由拦截器弹窗提示，这里保持弹窗打开让用户改
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function handleVisibleChange(visible) {
  emit('update:modelValue', visible)
}

// 弹窗关闭动画结束后的清理，避免下次打开闪一下旧数据
function handleClosed() {
  resetForm()
}
</script>

<style scoped>
/* ---------------- 封面 ---------------- */
.cover-box {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  width: 100%;
}

.cover-uploader {
  flex: none;
}

.cover-inner {
  width: 88px;
  height: 116px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  cursor: pointer;
  background: var(--subtle);
  overflow: hidden;
  transition: border-color var(--ease), background var(--ease);
}

.cover-inner:hover {
  border-color: #000;
  background: #fff;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--ink-4);
}

.cover-plus {
  font-size: 20px;
  line-height: 1;
  color: var(--ink-3);
}

.cover-hint {
  font-size: 11.5px;
}

.cover-side {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}

.cover-url-row {
  margin-bottom: 8px;
}

.cover-tip {
  margin: 0 0 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-4);
}

.cover-tip code {
  background: var(--subtle);
  border: 1px solid var(--line);
  border-radius: 3px;
  padding: 0 4px;
  color: var(--ink-2);
}

/* ---------------- 分隔 ---------------- */
.divider {
  height: 1px;
  background: var(--line);
  margin: 4px 0 20px;
}
</style>
