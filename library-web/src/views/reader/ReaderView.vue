<template>
  <div ref="readerRef" class="reader" :style="themeStyle">
    <!-- ================= 底部悬浮窗 =================
         圆角矩形 + 半透明 + 背景模糊，浮在【窗口下方居中】（fixed，不占布局高度）。
         常驻显示 —— 不随滚动隐藏。 -->
    <header class="bar">
      <!-- 左：返回 + 书名 -->
      <div class="bar-left">
        <button class="bar-btn" title="返回" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <span class="bar-book">{{ meta?.title || '阅读' }}</span>
      </div>

      <!-- 上一章：单独一栏，紧贴中间章节名的左侧 -->
      <button
        class="bar-btn"
        title="上一章"
        :disabled="chapter?.prevIndex == null"
        @click="goChapter(chapter.prevIndex)"
      >
        <el-icon><ArrowLeftBold /></el-icon>
      </button>

      <!-- 中：当前章节。换章时旧标题左移消失、新标题从右侧浮现。
           外层 overflow: hidden 把动画限制在两个翻章按钮之间。 -->
      <div class="bar-chapter-wrap">
        <transition :name="slideName">
          <div :key="chapter?.index ?? 'empty'" class="bar-chapter">
            {{ chapter?.title || '加载中…' }}
          </div>
        </transition>
      </div>

      <!-- 下一章：单独一栏，紧贴中间的右侧 —— 和上一章关于中心对称 -->
      <button
        class="bar-btn"
        title="下一章"
        :disabled="chapter?.nextIndex == null"
        @click="goChapter(chapter.nextIndex)"
      >
        <el-icon><ArrowRightBold /></el-icon>
      </button>

      <!-- 右：设置 -->
      <div class="bar-actions">
        <button class="bar-btn" title="目录" @click="tocVisible = true">
          <el-icon><List /></el-icon>
        </button>
        <button class="bar-btn" title="缩小字号" @click="changeFont(-1)">A−</button>
        <button class="bar-btn" title="放大字号" @click="changeFont(1)">A+</button>

        <!-- 配色：点开是五个色块，选完立刻生效并记住 -->
        <el-popover placement="top" :width="188" trigger="click" :show-arrow="false" :offset="12">
          <template #reference>
            <button class="bar-btn" title="配色"><el-icon><Brush /></el-icon></button>
          </template>

          <div class="theme-list">
            <button
              v-for="t in THEMES"
              :key="t.key"
              class="theme-item"
              :class="{ 'is-active': theme === t.key }"
              :title="t.label"
              @click="setTheme(t.key)"
            >
              <span class="theme-dot" :style="{ background: t.bg }"></span>
              <span class="theme-name">{{ t.label }}</span>
            </button>
          </div>
        </el-popover>
      </div>
    </header>

    <!-- ================= 正文 ================= -->
    <main class="stage">
      <div v-if="loading && !chapter" class="tip">加载中…</div>

      <div v-else-if="denied" class="tip">{{ denied }}</div>

      <article v-else-if="chapter" class="paper" :style="{ '--read-size': fontSize + 'px' }">
        <h1 class="chapter-title">{{ chapter.title }}</h1>

        <!-- ★ 每段一个 <p>：中文书要求【每段】段首缩进 2em，
             整篇塞进一个 <p> 里的话 text-indent 只会缩第一行。
             仍然用插值、不用 v-html —— txt 里可能有 <script> -->
        <p v-for="(text, i) in paragraphs" :key="i" class="para">{{ text }}</p>

        <nav class="pager">
          <button class="pager-btn" :disabled="chapter.prevIndex === null" @click.stop="goChapter(chapter.prevIndex)">
            ← 上一章
          </button>
          <span class="pager-pos tnum">{{ chapter.index + 1 }} / {{ meta?.chapterCount || 1 }}</span>
          <button class="pager-btn" :disabled="chapter.nextIndex === null" @click.stop="goChapter(chapter.nextIndex)">
            下一章 →
          </button>
        </nav>
      </article>
    </main>

    <!-- ================= 目录抽屉 ================= -->
    <el-drawer v-model="tocVisible" title="目录" direction="ltr" size="320px" class="toc-drawer">
      <div v-if="!meta?.chapters?.length" class="tip">暂无章节</div>
      <ul v-else class="toc">
        <li
          v-for="item in meta.chapters"
          :key="item.index"
          class="toc-item"
          :class="{ 'is-current': item.index === currentIndex }"
          @click="goChapter(item.index)"
        >
          <span class="toc-title">{{ item.title }}</span>
          <span class="toc-count tnum">{{ item.wordCount }}</span>
        </li>
      </ul>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowLeftBold, ArrowRightBold, Brush, List } from '@element-plus/icons-vue'
import { getReaderChapter, getReaderMeta, saveReaderProgress } from '@/api/reader'
import { TOKEN_KEY } from '@/utils/request'

const route = useRoute()
const router = useRouter()

// 路由里的 type = 'book' | 'file'，id 是书 id 或文件 id
// ★ 进页面时【快照】一次，不要用 computed 跟着 route 走：
//   离开阅读页时路由先变成 /books，route.params.id 就成了 undefined，
//   卸载钩子里拼出来的 URL 会是 /reader/book/undefined/progress
//   → 后端 Integer 绑定失败 → 500「服务器异常」。（那是个纯粹的假请求）
const type = ref(String(route.params.type || 'book'))
const targetId = ref(route.params.id)

const meta = ref(null)
const chapter = ref(null)
const loading = ref(false)
const denied = ref('')
const tocVisible = ref(false)
// 滚动容器（.reader 自己是 fixed + overflow-y:auto，滚动发生在它身上，不在 window）
const readerRef = ref(null)

const currentIndex = computed(() => chapter.value?.index ?? 0)

// 按行切段：空行和每行首尾的空白都丢掉。
// 段首缩进统一交给 CSS 的 text-indent —— 不再依赖 txt 里到底敲了几个空格
// （有的 txt 用两个半角空格、有的用全角、有的压根没缩进）
const paragraphs = computed(() =>
  String(chapter.value?.content || '')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
)

/* ---------------- 阅读设置（存 localStorage，下次进来还是这个字号） ---------------- */
const FONT_KEY = 'reader_font_size'
const THEME_KEY = 'reader_theme'

const fontSize = ref(Number(localStorage.getItem(FONT_KEY)) || 18)
/* ---------------- 五套配色 ----------------
   每套把 7 个 CSS 变量【一次给全】，然后通过 :style 直接写到 .reader 上。

   为什么不用"切 class + 在 CSS 里写 .is-paper { --paper-bg: ... }"？
   那条路要经过「类名匹配 → 规则命中 → 变量重解析」三步，任何一步意外，
   表现都是"换了主题但页面没变"，而且极难查。
   写成行内 style 只剩一步：Vue 直接把变量写进元素的 style 属性，
   绕开所有 CSS 规则解析，不可能"没匹配上"。 */
const THEMES = [
  {
    key: 'white',
    label: '白',
    bg: '#ffffff',
    vars: {
      '--paper-bg': '#ffffff',
      '--paper-ink': '#1a1a1a',
      '--paper-ink-soft': '#8a8a8a',
      '--paper-line': '#ededed',
      '--bar-bg': 'rgba(255, 255, 255, .82)',
      '--bar-line': '#ededed',
      '--bar-border': '#000'
    }
  },
  {
    key: 'paper',
    label: '纸黄',
    bg: '#faf6ec',
    vars: {
      '--paper-bg': '#faf6ec',
      '--paper-ink': '#2b2418',
      '--paper-ink-soft': '#9a8f79',
      '--paper-line': '#e9e1cf',
      '--bar-bg': 'rgba(250, 246, 236, .85)',
      '--bar-line': '#e9e1cf',
      '--bar-border': '#000'
    }
  },
  {
    key: 'green',
    label: '护眼',
    bg: '#e8efe4',
    vars: {
      '--paper-bg': '#e8efe4',
      '--paper-ink': '#23302a',
      '--paper-ink-soft': '#7d8f80',
      '--paper-line': '#d6dfd1',
      '--bar-bg': 'rgba(232, 239, 228, .85)',
      '--bar-line': '#d6dfd1',
      '--bar-border': '#000'
    }
  },
  {
    key: 'gray',
    label: '灰',
    bg: '#f2f2f2',
    vars: {
      '--paper-bg': '#f2f2f2',
      '--paper-ink': '#222222',
      '--paper-ink-soft': '#8c8c8c',
      '--paper-line': '#e0e0e0',
      '--bar-bg': 'rgba(242, 242, 242, .85)',
      '--bar-line': '#e0e0e0',
      '--bar-border': '#000'
    }
  },
  {
    key: 'dark',
    label: '夜间',
    bg: '#121212',
    vars: {
      '--paper-bg': '#121212',
      '--paper-ink': '#d6d6d6',
      '--paper-ink-soft': '#6a6a6a',
      '--paper-line': '#262626',
      '--bar-bg': 'rgba(18, 18, 18, .82)',
      '--bar-line': '#262626',
      '--bar-border': 'rgba(255, 255, 255, .32)'
    }
  }
]

// localStorage 里可能留着旧版的 'light'/'dark'，校验一下再兜底到白色
const savedTheme = localStorage.getItem(THEME_KEY)
const theme = ref(THEMES.some((t) => t.key === savedTheme) ? savedTheme : 'white')

// 当前这一套的 7 个变量，直接绑到根元素的 :style
const themeStyle = computed(() => {
  const current = THEMES.find((t) => t.key === theme.value)
  return (current || THEMES[0]).vars
})

// 换章动画方向：'next' = 旧标题左移消失、新标题从右进；'prev' 反过来
const slideDir = ref('next')
const slideName = computed(() => `chapter-${slideDir.value}`)

function changeFont(delta) {
  const next = Math.min(24, Math.max(16, fontSize.value + delta))
  if (next === fontSize.value) return
  fontSize.value = next
  localStorage.setItem(FONT_KEY, String(next))
}

function setTheme(key) {
  theme.value = key
  localStorage.setItem(THEME_KEY, key)
}

/* ---------------- 加载 ---------------- */
async function loadMeta() {
  loading.value = true
  try {
    const data = await getReaderMeta(type.value, targetId.value)
    meta.value = data

    if (!data.readable) {
      denied.value = data.denyReason || '这本书暂时不能在线阅读'
      return
    }

    // 有进度就接着上次读，否则从第一章开始
    await loadChapter(data.lastChapter || 0)

    // 再把章内滚动位置恢复回去（等正文渲染完才能算高度）
    if (data.lastPercent) {
      await nextTick()
      RestoreScroll(data.lastPercent)
    }
  } catch (e) {
    denied.value = '加载失败，请返回重试'
  } finally {
    loading.value = false
  }
}

async function loadChapter(index) {
  if (index === null || index === undefined) return
  loading.value = true
  try {
    chapter.value = await getReaderChapter(type.value, targetId.value, index)
    readerRef.value?.scrollTo({ top: 0 })
  } finally {
    loading.value = false
  }
}

/**
 * 把滚动条恢复到上次的百分比位置。
 *
 * 为什么要 nextTick 之后再调？因为 scrollHeight 取决于正文有没有渲染出来，
 * 章节内容刚赋值、DOM 还没更新时算出来是 0，滚不动。
 */
function RestoreScroll(percent) {
  const el = readerRef.value
  if (!el) return
  const scrollable = el.scrollHeight - el.clientHeight
  if (scrollable <= 0) return
  el.scrollTop = (Number(percent) / 100) * scrollable
}

function goChapter(index) {
  if (index === null || index === undefined) return

  // 决定动画方向：往后翻 → 旧标题左移、新标题从右进；往前翻则相反
  const from = chapter.value?.index
  slideDir.value = typeof from === 'number' && index < from ? 'prev' : 'next'

  FlushProgress()         // 切章之前立刻把上一章的进度存了
  tocVisible.value = false
  loadChapter(index)
}

/* ---------------- 进度保存 ----------------
   三种时机，行为不同：
     saveProgress()   滚动时 → 800ms 防抖，避免狂发请求
     FlushProgress()  切章 / 点返回 / 组件卸载 → 立刻发，不等防抖
     SaveOnUnload()   关标签 / 刷新 → fetch + keepalive
   percent 一律用当前滚动位置算。 */
let saveTimer = null

/** 当前章内滚动百分比，0~100 */
function CalcPercent() {
  const el = readerRef.value
  const scrollable = el ? el.scrollHeight - el.clientHeight : 0
  return scrollable > 0 ? Number(Math.min(100, (el.scrollTop / scrollable) * 100).toFixed(2)) : 0
}

function BuildProgress() {
  return {
    chapterIndex: chapter.value.index,
    chapterTitle: chapter.value.title,
    percent: CalcPercent()
  }
}

function saveProgress() {
  if (!chapter.value || !meta.value?.readable || !targetId.value) return
  const payload = BuildProgress()
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveReaderProgress(type.value, targetId.value, payload).catch(() => {})
  }, 800)
}

/** 立刻发，不防抖 —— 切章、点返回、组件卸载都用它 */
function FlushProgress() {
  if (!chapter.value || !meta.value?.readable || !targetId.value) return
  clearTimeout(saveTimer)
  saveReaderProgress(type.value, targetId.value, BuildProgress()).catch(() => {})
}

/**
 * 关标签 / 刷新前的最后一次保存。
 *
 * ★ 必须用 fetch(keepalive: true)：axios 底层是 XHR，页面一卸载请求就被浏览器取消，
 *   进度会静默丢失。keepalive 让这个请求带着卸载过程一起发出去。
 *
 * 为什么不用 navigator.sendBeacon？它只能发表单或纯文本（Content-Type 受限），
 * 而我们的接口是 PUT + application/json，发不了。
 */
function SaveOnUnload() {
  if (!chapter.value || !meta.value?.readable || !targetId.value) return
  clearTimeout(saveTimer)
  const token = localStorage.getItem(TOKEN_KEY)
  fetch(`/api/reader/${type.value}/${targetId.value}/progress`, {
    method: 'PUT',
    keepalive: true,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(BuildProgress())
  }).catch(() => {})
}

/* ---------------- 键盘翻章 ---------------- */
function onKeydown(e) {
  if (e.key === 'ArrowLeft' && chapter.value?.prevIndex !== null) {
    goChapter(chapter.value.prevIndex)
  } else if (e.key === 'ArrowRight' && chapter.value?.nextIndex !== null) {
    goChapter(chapter.value.nextIndex)
  } else if (e.key === 'Escape') {
    tocVisible.value = false
  }
}

function goBack() {
  // 进度不在这个里存 —— 交给 onBeforeUnmount 统一存一次，
  // 两边都存会发两遍请求（第二遍还可能带上已经失效的路由参数）
  // 从书详情点进来的，退回图书列表；从「我的文件」点进来的，退回文件列表
  router.push(type.value === 'file' ? '/my-files' : '/books')
}

onMounted(() => {
  loadMeta()
  window.addEventListener('keydown', onKeydown)
  // 关标签页 / 刷新 / 手机切后台，都会触发 pagehide
  window.addEventListener('pagehide', SaveOnUnload)
})

onBeforeUnmount(() => {
  // ★ 必须用 FlushProgress：原来的 saveProgress 有 800ms 防抖，
  //   紧接着 clearTimeout 会把刚设的定时器取消 —— 等于一次都没存出去
  FlushProgress()
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pagehide', SaveOnUnload)
})
</script>

<style scoped>
/* ---------------- 主题变量 ----------------
   真正的配色值在脚本的 THEMES 里，通过 :style 行内写进来。
   这里这套只是"JS 还没跑起来"时的兜底（= 白色），不要在这里加别的主题 ——
   两处各写一份迟早不同步。 */
.reader {
  --paper-bg: #ffffff;
  --paper-ink: #1a1a1a;
  --paper-ink-soft: #8a8a8a;
  --paper-line: #ededed;
  --bar-bg: rgba(255, 255, 255, .82);
  --bar-line: #ededed;
  --bar-border: #000;

  position: fixed;
  inset: 0;
  overflow-y: auto;
  background-color: var(--paper-bg);
  color: var(--paper-ink);
  /* ★ 不做过场动画：换主题是"立刻切"，这也是阅读器的通行做法。
     留着 transition 只会给"偶尔不生效"多留一个可能的解释。 */
}

/* ---------------- 悬浮窗 ---------------- */
/* 圆角矩形 + 半透明 + 背景模糊，浮在【窗口下方居中】。
   fixed 而不是 sticky —— 它不该占布局高度，正文会从它底下穿过去。
   grid 五栏：左(1fr) / 上一章 / 章节名 / 下一章 / 右(1fr)
   —— 左右两栏等宽，所以「上一章·章节名·下一章」这一组是【关于窗口中心对称】的 */
.bar {
  position: fixed;
  bottom: 16px;
  left: 0;
  right: 0;
  margin: 0 auto;
  width: min(760px, calc(100% - 32px));
  z-index: 20;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  height: 54px;
  padding: 0 10px 0 12px;
  background-color: var(--bar-bg);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border: 1px solid var(--bar-border);
  border-radius: 16px;
  /* 它贴在底部，阴影朝上才有"浮起来"的感觉 */
  box-shadow: 0 -6px 24px rgba(0, 0, 0, .10);
}

/* 左栏：返回 + 书名 */
.bar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.bar-book {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 中栏：当前章节名。宽度固定，动画才有个"边界" */
.bar-chapter-wrap {
  position: relative;
  width: min(260px, 40vw);
  height: 20px;
  overflow: hidden;
}

.bar-chapter {
  position: absolute;
  inset: 0;
  height: 20px;
  line-height: 20px;
  font-size: 14px;
  font-weight: 500;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 换章动画：旧标题和新标题【同时】在场，一个左移一个从右进。
   所以不能用 mode="out-in"（那会先消失再出现），两个都绝对定位叠在同一格里。 */
.chapter-next-enter-from {
  opacity: 0;
  transform: translateX(100%);
}

.chapter-next-leave-to {
  opacity: 0;
  transform: translateX(-100%);
}

/* 「上一章」是反方向 */
.chapter-prev-enter-from {
  opacity: 0;
  transform: translateX(-100%);
}

.chapter-prev-leave-to {
  opacity: 0;
  transform: translateX(100%);
}

.chapter-next-enter-active,
.chapter-next-leave-active,
.chapter-prev-enter-active,
.chapter-prev-leave-active {
  transition: transform 220ms ease, opacity 220ms ease;
}

/* 右栏：设置 */
.bar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

/* ---------------- 配色选择面板（el-popover 的插槽内容） ---------------- */
.theme-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.theme-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  font: inherit;
  font-size: 13px;
  text-align: left;
  color: var(--ink-2);
  background: none;
  border: none;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background 120ms ease, color 120ms ease;
}

.theme-item:hover {
  background: var(--hover);
  color: var(--ink-1);
}

.theme-item.is-active {
  background: var(--selected);
  color: #000;
  font-weight: 500;
}

/* 色块直接用每套配色的纸色当预览；加一圈极淡的边，白色才看得见 */
.theme-dot {
  width: 18px;
  height: 18px;
  flex: none;
  border-radius: 50%;
  border: 1px solid var(--line-strong);
}

.theme-name {
  flex: 1;
}

.bar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 34px;
  padding: 0 7px;
  font-size: 14px;
  color: var(--paper-ink-soft);
  background: none;
  border: none;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: color 120ms ease, background 120ms ease;
}

.bar-btn:hover:not(:disabled) {
  color: var(--paper-ink);
  background: rgba(128, 128, 128, .12);
}

/* 第一章没有「上一章」、最后一章没有「下一章」 */
.bar-btn:disabled {
  opacity: .28;
  cursor: default;
}

/* ---------------- 正文 ---------------- */
/* 悬浮窗挪到底部了：顶部不用再让位，改成底部留出空间（16 + 54 + 呼吸） */
.stage {
  min-height: 100%;
  padding: 40px 20px 104px;
}

.paper {
  max-width: 34em;
  margin: 0 auto;
  font-size: var(--read-size, 18px);
  line-height: 1.9;      /* 中文没有词间距，行距要比英文松 */
  letter-spacing: .01em;
}

/* 章首：居中 + 上方一道短细线，做出"书页翻到新一章"的感觉（纯黑白，不抢戏） */
.chapter-title {
  position: relative;
  margin: 0 0 38px;
  padding-top: 38px;
  font-size: 1.35em;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: -.01em;
  text-align: center;
}

.chapter-title::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 32px;
  height: 1px;
  background: var(--paper-ink-soft);
}

.para {
  margin: 0;
  text-indent: 2em;      /* 中文书：段首缩进两个字符 */
  text-align: justify;   /* 两端对齐，右侧不会参差不齐 */
  word-break: break-word;
}

/* ---------------- 翻章 ---------------- */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 48px;
  padding-top: 20px;
  border-top: 1px solid var(--paper-line);
}

.pager-btn {
  padding: 7px 14px;
  font-size: 13px;
  color: var(--paper-ink-soft);
  background: none;
  border: 1px solid var(--paper-line);
  border-radius: var(--r-md);
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease;
}

.pager-btn:hover:not(:disabled) {
  color: var(--paper-ink);
  border-color: var(--paper-ink-soft);
}

.pager-btn:disabled {
  opacity: .35;
  cursor: not-allowed;
}

.pager-pos {
  font-size: 12px;
  color: var(--paper-ink-soft);
}

/* ---------------- 目录 ---------------- */
.toc {
  margin: 0;
  padding: 0;
  list-style: none;
}

.toc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 8px;
  font-size: 13px;
  color: var(--ink-2);
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background 100ms ease, color 100ms ease;
}

.toc-item:hover {
  background: var(--hover);
  color: var(--ink-1);
}

.toc-item.is-current {
  background: var(--selected);
  color: #000;
  font-weight: 500;
}

.toc-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toc-count {
  flex: none;
  font-size: 11.5px;
  color: var(--ink-4);
}

.tip {
  max-width: 34em;
  margin: 80px auto;
  text-align: center;
  font-size: 14px;
  color: var(--paper-ink-soft);
}

/* 窄屏：先牺牲书名，把空间让给章节名和按钮 */
@media (max-width: 720px) {
  .bar-book {
    display: none;
  }

  .bar-chapter-wrap {
    width: min(180px, 34vw);
  }
}

/* 更窄：左右留白收一点，底部给悬浮窗留位 */
@media (max-width: 640px) {
  .stage {
    padding: 24px 16px 96px;
  }

  .bar-btn {
    min-width: 30px;
    padding: 0 5px;
  }
}
</style>
