<template>
  <!--
    图书详情抽屉（2.0 新增）
    - 普通用户：这是他唯一的"深入"功能，只读
    - 管理员：也能用（列表里点书名就能快速看一眼，不用进编辑弹窗）
  -->
  <el-drawer
    :model-value="modelValue"
    title="图书详情"
    size="480px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div v-if="book" class="detail">
      <!-- 头部：封面 + 书名 + 作者 + 分类/状态 -->
      <div class="head">
        <img v-if="book.coverUrl" :src="book.coverUrl" class="cover" alt="封面" />
        <span v-else class="cover-mark">{{ (book.name || '?').trim().charAt(0) }}</span>

        <div class="head-info">
          <h2 class="name">{{ book.name }}</h2>
          <div class="author">{{ book.author }}</div>
          <div class="chips">
            <span class="chip">{{ book.categoryName || '未分类' }}</span>
            <span class="chip" :class="{ 'is-off': book.status !== 1 }">
              <span class="dot"></span>{{ book.status === 1 ? '在架' : '下架' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 三个关键数字 -->
      <div class="stats">
        <div class="stat">
          <div class="k">在馆可借</div>
          <div class="v tnum" :class="{ 'is-zero': book.availableNum === 0 }">
            {{ book.availableNum }}
          </div>
        </div>
        <div class="stat">
          <div class="k">总册数</div>
          <div class="v tnum">{{ book.totalNum }}</div>
        </div>
        <div class="stat">
          <div class="k">定价</div>
          <div class="v tnum">
            {{ book.price !== null && book.price !== undefined ? '¥ ' + Number(book.price).toFixed(2) : '—' }}
          </div>
        </div>
      </div>

      <!-- 字段明细 -->
      <dl class="fields">
        <div class="row">
          <dt>ISBN</dt>
          <dd class="mono">{{ book.isbn }}</dd>
        </div>
        <div class="row">
          <dt>出版社</dt>
          <dd>{{ book.publisher || '—' }}</dd>
        </div>
        <div class="row">
          <dt>出版日期</dt>
          <dd>{{ book.publishDate || '—' }}</dd>
        </div>
        <div class="row">
          <dt>分类</dt>
          <dd>{{ book.categoryName || '—' }}</dd>
        </div>
        <div class="row">
          <dt>录入时间</dt>
          <dd><date-time-cell :value="book.createTime" /></dd>
        </div>
        <div class="row">
          <dt>最近更新</dt>
          <dd><date-time-cell :value="book.updateTime" /></dd>
        </div>
      </dl>

      <!-- 简介 -->
      <div class="block">
        <div class="block-title">内容简介</div>
        <p class="intro" :class="{ 'is-empty': isIntroEmpty }">{{ introText }}</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <!-- 4.0：有电子版才显示在线阅读（readable 由后端返回，1 = 已开启且有正文） -->
      <el-button v-if="book && book.readable === 1" @click="openReader">在线阅读</el-button>
      <!-- 只有管理员看得到「编辑」—— 普通用户在这里也不能改任何东西 -->
      <el-button v-if="isAdmin" type="primary" @click="emit('edit', book)">编辑这本</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import DateTimeCell from '@/components/DateTimeCell.vue'
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'

/** 这本书没写简介时，显示的占位文案（比干巴巴的"暂无简介"友好一点） */
const EMPTY_INTRO = '该书本暂时没有详情介绍，奇妙的内容等待着你的探索~'

const props = defineProps({
  // 控制抽屉显示（配合 v-model）
  modelValue: { type: Boolean, default: false },
  // 要展示的图书对象（列表行传进来即可，不用再查一次接口）
  book: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'edit'])

const { isAdmin } = useUserStore()
const router = useRouter()

/** 4.0：进阅读页。readable=1 表示管理员上传了正文并开启了在线阅读 */
function openReader() {
  if (!props.book?.id) return
  emit('update:modelValue', false)
  router.push(`/read/book/${props.book.id}`)
}

/**
 * 简介文案
 *
 * 用 computed 而不是模板里写 `book.intro || '...'` 的原因：
 *   `||` 只能挡住 null / undefined / 空串，
 *   但后台如果把简介存成了几个空格（"   "），`||` 会放它过去，
 *   抽屉里就出现一块看着像 bug 的空白。这里 trim 一下再判断。
 */
const introText = computed(() => {
  const text = props.book?.intro
  return text && String(text).trim() ? text : EMPTY_INTRO
})

/** 当前显示的是不是占位文案 —— 用来把字色调淡，弱化它的存在感 */
const isIntroEmpty = computed(() => introText.value === EMPTY_INTRO)
</script>

<style scoped>
.detail {
  font-size: 13.5px;
}

/* ---------------- 头部 ---------------- */
.head {
  display: flex;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line);
}

.cover {
  width: 84px;
  height: 112px;
  flex: none;
  object-fit: cover;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
}

.cover-mark {
  width: 84px;
  height: 112px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background: var(--subtle);
  color: var(--ink-3);
  font-size: 30px;
}

.head-info {
  min-width: 0;
}

.name {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.35;
}

.author {
  font-size: 13px;
  color: var(--ink-2);
  margin-bottom: 10px;
}

.chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  padding: 2px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 99px;
  color: var(--ink-2);
}

.chip .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #000;
}

.chip.is-off {
  color: var(--ink-4);
}

.chip.is-off .dot {
  background: var(--ink-5);
}

/* ---------------- 数字区 ---------------- */
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  overflow: hidden;
  margin: 20px 0;
}

.stat {
  background: #fff;
  padding: 14px 12px;
}

.stat .k {
  font-size: 12px;
  color: var(--ink-4);
  margin-bottom: 4px;
}

.stat .v {
  font-size: 17px;
  font-weight: 600;
}

.stat .v.is-zero {
  color: var(--alert);
}

/* ---------------- 字段明细 ---------------- */
.fields {
  margin: 0;
}

.row {
  display: flex;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--row-line);
}

.row dt {
  width: 76px;
  flex: none;
  color: var(--ink-4);
  font-size: 13px;
}

.row dd {
  margin: 0;
  min-width: 0;
  word-break: break-all;
}

/* ---------------- 简介 ---------------- */
.block {
  margin-top: 22px;
}

.block-title {
  font-size: 12px;
  color: var(--ink-4);
  margin-bottom: 8px;
}

.intro {
  margin: 0;
  line-height: 1.8;
  color: var(--ink-2);
  white-space: pre-wrap;
}

/* 没写简介时的占位文案：字色淡一点，别让它看起来像正文 */
.intro.is-empty {
  color: var(--ink-4);
}
</style>
