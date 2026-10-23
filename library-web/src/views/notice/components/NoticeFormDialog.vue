<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑通知' : '发布通知'"
    width="640px"
    :close-on-click-modal="false"
  >
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="72px"
    >
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="一句话说清这条通知是什么"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="form.type">
          <el-radio v-for="item in NOTICE_TYPES" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="正文" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="9"
          placeholder="直接换行分段即可，不需要写 HTML"
          maxlength="5000"
        />
        <div class="field-tip">
          换行会原样保留，读者那边按段显示 —— 所以<strong>不要</strong>手写 &lt;br&gt; 或 HTML 标签。
        </div>
      </el-form-item>

      <el-form-item label="置顶">
        <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
        <span class="field-tip inline">
          置顶的通知会一直排在列表最前面（多个置顶之间仍按发布时间倒序）
        </span>
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">立即发布</el-radio>
          <el-radio :value="0">先存着（下架状态）</el-radio>
        </el-radio-group>
        <div class="field-tip">
          选「先存着」的话，通知只有管理员看得到，普通读者不会看到 —— 相当于草稿箱。
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存修改' : '确定发布' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { NOTICE_TYPES, addNotice, getNoticeById, updateNotice } from '@/api/notice'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** null = 新增；有值 = 编辑哪一条 */
  noticeId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => props.noticeId !== null && props.noticeId !== undefined)

const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)

function createEmptyForm() {
  return {
    title: '',
    content: '',
    type: 1,
    isTop: 0,
    status: 1
  }
}

const form = reactive(createEmptyForm())

/* 校验规则和接口文档第 4 节保持一致。
   ⚠️ 前端校验只是【体验】，后端必须再校验一遍 —— 用 Apifox 绕过它太容易了。 */
const rules = {
  title: [
    { required: true, message: '标题不能为空', trigger: 'blur' },
    { max: 100, message: '标题长度不能超过 100 个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '正文不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '请选择通知类型', trigger: 'change' }]
}

/**
 * 每次打开弹窗都重新初始化
 *
 * 编辑时要【重新去后端拉一次详情】—— 列表接口只返回 summary（摘要），
 * 没有全文。直接拿列表那行的数据填进 textarea，会把摘要当成正文存回去，
 * 一保存就把原文截断了。
 */
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return

    Object.assign(form, createEmptyForm())
    formRef.value?.clearValidate()

    if (!isEdit.value) return

    loading.value = true
    try {
      const data = await getNoticeById(props.noticeId)
      Object.assign(form, {
        title: data?.title ?? '',
        content: data?.content ?? '',
        type: data?.type ?? 1,
        isTop: data?.isTop ?? 0,
        status: data?.status ?? 1
      })
    } catch (e) {
      // 拉不到详情就别让用户对着一个空表单改（保存下去会覆盖掉原文）
      visible.value = false
    } finally {
      loading.value = false
    }
  }
)

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    // ⚠️ 这里【不传】publisherId / publishTime —— 谁发的、什么时候发的由后端定
    const payload = {
      title: form.title.trim(),
      content: form.content,
      type: form.type,
      isTop: form.isTop,
      status: form.status
    }

    if (isEdit.value) {
      await updateNotice(props.noticeId, payload)
      ElMessage.success('修改成功')
    } else {
      await addNotice(payload)
      ElMessage.success('发布成功')
    }

    visible.value = false
    emit('success')
  } catch (e) {
    // 失败提示由拦截器统一弹出（如"标题不能为空"）
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.field-tip {
  font-size: 12px;
  color: var(--ink-4);
  line-height: 1.6;
}

.field-tip.inline {
  margin-left: 10px;
}

.field-tip strong {
  color: var(--ink-2);
  font-weight: 500;
}
</style>
