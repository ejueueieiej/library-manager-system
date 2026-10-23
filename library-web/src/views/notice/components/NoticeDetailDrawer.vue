<template>
  <el-drawer v-model="visible" size="620px" :with-header="false">
    <div v-loading="loading" class="detail">
      <template v-if="notice">
        <!-- ---------------- 头部 ---------------- -->
        <header class="head">
          <div class="head-tags">
            <span v-if="notice.isTop === 1" class="pin-tag">置顶</span>
            <span class="type-chip">{{ noticeTypeLabel(notice.type) }}</span>
            <span v-if="notice.status === 0" class="off-tag">已下架</span>
          </div>

          <h2 class="title">{{ notice.title }}</h2>

          <div class="meta">
            <span>{{ notice.publisherName || '—' }}</span>
            <span class="dot">·</span>
            <span class="tnum">发布于 {{ formatDateTime(notice.publishTime) || '—' }}</span>
            <!-- 编辑过才显示这一行，没编辑过就不多占一行 -->
            <template v-if="notice.updateTime && notice.updateTime !== notice.publishTime">
              <span class="dot">·</span>
              <span class="tnum">最后编辑 {{ formatDateTime(notice.updateTime) }}</span>
            </template>
          </div>
        </header>

        <!-- ---------------- 正文 ----------------
             后端给的是纯文本 + 换行符，这里按换行拆成段落。
             ⚠️ 千万不要用 v-html 渲染：
                通知正文是管理员输入的，用 v-html 等于把 XSS 的大门敞开
                （万一有人往正文里塞 <script> 或 <img onerror>）。
                纯文本 + 拆段落，既安全又能保留排版。 -->
        <article class="content">
          <p v-for="(para, index) in paragraphs" :key="index">{{ para }}</p>
        </article>

        <footer class="foot">
          <el-button @click="visible = false">关闭</el-button>
        </footer>
      </template>

      <el-empty v-else-if="!loading" description="通知不存在或已被删除" />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { formatDateTime } from '@/utils/datetime'
import { getNoticeById, noticeTypeLabel } from '@/api/notice'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  noticeId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'loaded'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const notice = ref(null)

/**
 * 正文拆段
 * 空行（连续换行）会被过滤掉，避免正文里出现一大片空白
 */
const paragraphs = computed(() => {
  const text = notice.value?.content ?? ''
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
})

/**
 * 打开时拉详情
 *
 * ★ 这个请求有一个副作用：如果当前登录的是普通读者，
 *   后端会顺手把这条通知标记成"已读"。所以拉成功之后要 emit('loaded')，
 *   让列表页把那行的圆点灭掉、侧边栏红点跟着降 —— 前端不用自己判断角色。
 */
watch(
  () => props.modelValue,
  async (open) => {
    if (!open || props.noticeId === null || props.noticeId === undefined) return

    notice.value = null
    loading.value = true
    try {
      notice.value = await getNoticeById(props.noticeId)
      emit('loaded', notice.value)
    } catch (e) {
      // 失败提示由拦截器统一弹出（如"通知不存在或已下架"）
      notice.value = null
    } finally {
      loading.value = false
    }
  }
)
</script>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  min-height: 200px;
}

/* ---------------- 头部 ---------------- */
.head {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}

.head-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.pin-tag {
  font-size: 11px;
  line-height: 1;
  padding: 3px 5px;
  background: #000;
  color: #fff;
  border-radius: var(--r-sm);
}

.type-chip {
  font-size: 12px;
  padding: 2px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 99px;
  color: var(--ink-2);
}

.off-tag {
  font-size: 11px;
  line-height: 1;
  padding: 3px 5px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  color: var(--ink-3);
}

.title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: -.02em;
  color: var(--ink-1);
  text-wrap: balance;
}

.meta {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--ink-4);
}

.meta .dot {
  margin: 0 6px;
}

/* ---------------- 正文 ---------------- */
.content {
  flex: 1;
  padding: 18px 0;
}

.content p {
  margin: 0 0 12px;
  font-size: 13.5px;
  line-height: 1.85;
  /* 正文是中文，首行缩进两字看着更像正式通知 */
  text-indent: 2em;
  color: var(--ink-2);
  white-space: pre-wrap;
}

.content p:last-child {
  margin-bottom: 0;
}

/* ---------------- 底部 ---------------- */
.foot {
  padding-top: 14px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
}
</style>
