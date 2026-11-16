<template>
  <div class="ai-page">
    <!-- ================= 左侧：会话列表 ================= -->
    <aside class="conv-pane">
      <button type="button" class="new-chat" :disabled="sending" @click="newConversation">
        ＋ 新对话
      </button>

      <div class="conv-list">
        <div
          v-for="c in conversations"
          :key="c.id"
          class="conv-item"
          :class="{ 'is-active': c.id === conversationId }"
          @click="openConversation(c.id)"
        >
          <span class="conv-title">{{ c.title || '新对话' }}</span>
          <button
            type="button"
            class="conv-edit"
            title="重命名"
            @click.stop="onRenameConversation(c)"
          >✎</button>
          <button
            type="button"
            class="conv-del"
            title="删除会话"
            @click.stop="onDeleteConversation(c)"
          >×</button>
        </div>

        <p v-if="!conversations.length" class="conv-empty">还没有会话</p>
      </div>
    </aside>

    <!-- ================= 右侧：聊天 ================= -->
    <div class="ai-main">
      <!-- 顶栏 -->
      <header class="ai-head">
        <div class="ai-head-main">
          <h2 class="ai-title">AI 馆员</h2>
          <p class="ai-sub">基于馆藏知识的问答 · 会记住当前会话的上下文</p>
        </div>
      </header>

    <!-- ================= 消息区 ================= -->
    <div ref="scrollRef" class="ai-body">
      <div class="thread">
        <!-- 欢迎态：没有消息时才出现 -->
        <div v-if="!messages.length" class="welcome">
          <div class="welcome-mark">AI</div>
          <h3 class="welcome-title">今天想找点什么书？</h3>
          <p class="welcome-sub">可以问馆藏、借阅规则，也可以让我帮你找书。</p>

          <div class="chips">
            <button
              v-for="q in suggestions"
              :key="q"
              type="button"
              class="chip"
              @click="ask(q)"
            >
              {{ q }}
            </button>
          </div>

          <p class="welcome-note">会话会自动保存，左侧可以切换历史会话。</p>
        </div>

        <!-- 消息列表 -->
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="'is-' + m.role">
          <div v-if="m.role === 'assistant'" class="avatar">AI</div>

          <div class="msg-main">
            <div
              class="bubble"
              :class="{
                'is-error': m.error,
                'is-thinking': m.streaming && !m.content,
                'is-md': m.role === 'assistant'
              }"
            >
              <template v-if="m.role === 'assistant'">
                <div v-if="m.content" class="md" v-html="renderMarkdown(m.content)"></div>
                <span v-else-if="m.streaming">正在思考…</span>
              </template>
              <span v-else>{{ m.content }}</span>
              <button v-if="m.error" type="button" class="retry" @click="retry(m)">重试</button>
            </div>
            <div v-if="m.role === 'assistant' && m.meta" class="meta">{{ m.meta }}</div>
          </div>

          <div v-if="m.role === 'user'" class="avatar is-user">我</div>
        </div>

      </div>
    </div>

    <!-- ================= 输入区 ================= -->
    <footer class="ai-foot">
      <div
        class="composer"
        :class="{ 'is-open': draft.length > 0 }"
        @click="focusComposer"
      >
        <el-input
          ref="inputRef"
          v-model="draft"
          type="textarea"
          rows="1"
          resize="none"
          maxlength="2000"
          placeholder="问问馆藏、借阅规则，或者让我帮你找本书…"
          @keydown.enter.exact="onEnter"
        />

        <div class="composer-bar">
          <span class="hint">Enter 发送 · Shift + Enter 换行</span>

          <div class="composer-actions">
            <span class="count" :class="{ 'is-limit': draft.length >= 2000 }">
              {{ draft.length }} / 2000
            </span>

            <el-button
              v-if="sending"
              class="send-btn is-stop"
              circle
              title="停止生成"
              @click="stop"
            >
              <el-icon><VideoPause /></el-icon>
            </el-button>

            <el-button
              v-else
              class="send-btn"
              type="primary"
              circle
              :disabled="!canSend"
              title="发送"
              @click="send"
            >
              <el-icon><ArrowUp /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <p class="disclaimer">AI 也会犯错，重要信息请以图书详情页为准。</p>
    </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'
import '@/styles/markdown.css'
import { listConversations, listMessages, removeConversation, renameConversation, streamChat } from '@/api/ai'

/* 只注册用得到的语言，避免把整个 highlight.js 打进来 */
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('python', python)

/* ---------------- Markdown ---------------- */
const md = new MarkdownIt({
  html: false,     // 禁止模型输出原始 HTML
  linkify: true,   // 裸链接自动变 <a>
  breaks: true,    // 换行即换行，符合聊天习惯
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code class="language-' + lang + '">' +
          hljs.highlight(code, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>'
      } catch (e) {
        // 高亮失败就退回纯文本，不让整段渲染挂掉
      }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(code) + '</code></pre>'
  }
})

/** 助手消息走 Markdown；DOMPurify 再兜一层，防止 XSS */
function renderMarkdown(text) {
  return DOMPurify.sanitize(md.render(text || ''))
}

/* ---------------- 状态 ---------------- */
const messages = ref([])
const conversations = ref([])
const conversationId = ref(null)
const draft = ref('')
const sending = ref(false)
const scrollRef = ref(null)
const inputRef = ref(null)

const suggestions = [
  '帮我找几本讲 Java 的书',
  '借阅期限是多少天？',
  '书弄丢了怎么办？'
]

const canSend = computed(() => !sending.value && draft.value.trim().length > 0)

const route = useRoute()
const router = useRouter()

let controller = null
let rafId = 0

onMounted(async () => {
  await refreshConversations()

  const id = Number(route.query.id)
  if (id) {
    await openConversation(id)
  } else {
    // 进页面直接能打字，少点一次鼠标
    inputRef.value?.focus?.()
  }
})

/* ---------------- 会话列表 ---------------- */
async function refreshConversations() {
  try {
    const data = await listConversations()
    conversations.value = Array.isArray(data) ? data : []
  } catch {
    // 后端会话接口没就绪时静默，不影响聊天
  }
}

async function openConversation(id) {
  if (sending.value) {
    ElMessage.warning('请先停止当前回答')
    return
  }

  try {
    const data = await listMessages(id)
    conversationId.value = id
    messages.value = (Array.isArray(data) ? data : []).map((m) => ({
      role: m.role,
      content: m.content || '',
      meta: m.role === 'assistant' && m.status === 2
        ? '已中断'
        : (m.role === 'assistant' && m.status === 3 ? '生成失败' : ''),
      streaming: false,
      error: false
    }))
    router.replace({ path: '/ai', query: { id } })
    await scrollToBottom()
  } catch (e) {
    ElMessage.error(describeError(e))
  }
}

function newConversation() {
  if (sending.value) return
  conversationId.value = null
  messages.value = []
  draft.value = ''
  router.replace({ path: '/ai' })
  nextTick(() => inputRef.value?.focus?.())
}

async function onRenameConversation(conv) {
  if (sending.value) {
    ElMessage.warning('请先停止当前回答')
    return
  }

  try {
    const { value } = await ElMessageBox.prompt('输入新的会话名称', '重命名会话', {
      inputValue: conv.title || '',
      inputValidator: (v) => {
        const text = (v || '').trim()
        if (!text) return '名称不能为空'
        if (text.length > 50) return '名称不能超过 50 个字'
        return true
      },
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })

    await renameConversation(conv.id, value.trim())
    ElMessage.success('已重命名')
    await refreshConversations()
  } catch (e) {
    // 点取消/关闭时 ElMessageBox 会 reject 字符串，不是网络错误
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(describeError(e))
  }
}

async function onDeleteConversation(conv) {
  if (sending.value) {
    ElMessage.warning('请先停止当前回答')
    return
  }

  try {
    await ElMessageBox.confirm(
      '删除后聊天记录将无法恢复，确定要继续吗？',
      `删除「${conv.title || '新对话'}」`,
      {
        type: 'warning',
        confirmButtonText: '仍要删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  try {
    await removeConversation(conv.id)
    ElMessage.success('会话已删除')

    if (conversationId.value === conv.id) {
      conversationId.value = null
      messages.value = []
      router.replace({ path: '/ai' })
    }
    await refreshConversations()
  } catch (e) {
    ElMessage.error(describeError(e))
  }
}

/* ---------------- 发送 ---------------- */
function onEnter(e) {
  // 中文输入法选词时按回车是"确认候选词"，不能当成发送。
  // keyCode 229 是部分浏览器在组合输入期间给出的老值，一并兜住。
  if (e.isComposing || e.keyCode === 229) return
  // 只在真正要发送时才 preventDefault，避免把输入法的回车一起吃掉
  e.preventDefault()
  send()
}

function ask(text) {
  if (sending.value) return
  draft.value = text
  send()
}

/** 点卡片空白处 / 内边距也聚焦输入框；点按钮时不抢焦点 */
function focusComposer(e) {
  const target = e.target
  if (target instanceof Element && target.closest('button')) return
  inputRef.value?.focus?.()
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return

  draft.value = ''
  messages.value.push({ role: 'user', content: text })
  await scrollToBottom()
  await requestAnswer(text)
}

/**
 * 单独抽出来是为了「重试」能复用：
 * 重试只重发问题，不会在列表里再插一条重复的用户消息。
 *
 * 现在只走 SSE：没有普通 JSON 接口了。
 */
async function requestAnswer(question) {
  sending.value = true
  await scrollToBottom()

  const assistant = reactive({
    role: 'assistant',
    content: '',
    meta: '',
    streaming: true,
    error: false,
    question
  })
  messages.value.push(assistant)

  const isNewConversation = conversationId.value == null

  try {
    controller = new AbortController()

    await streamChat({
      content: question,
      conversationId: conversationId.value,
      signal: controller.signal,
      onMeta: (meta) => {
        if (meta?.conversationId) {
          conversationId.value = meta.conversationId

          // 新会话第一次拿到 id：写进 URL，并立刻让它出现在左侧
          if (isNewConversation) {
            router.replace({ path: '/ai', query: { id: conversationId.value } })
            refreshConversations()
          }
        }
        assistant.meta = meta?.model || ''
      },
      onDelta: (text) => {
        assistant.content += text
        scheduleScroll()
      }
    })

    if (!assistant.content) {
      assistant.content = '（没有返回内容）'
    }
  } catch (e) {
    if (e?.response?.status === 401 || e?.code === 401) {
      // 拦截器会跳登录页；把这个空气泡删掉，别留在对话里
      const index = messages.value.indexOf(assistant)
      if (index >= 0) messages.value.splice(index, 1)
      return
    }

    if (e?.name === 'AbortError') {
      assistant.content = assistant.content || '已停止'
      assistant.meta = '已停止生成'
    } else {
      assistant.error = true
      if (!assistant.content) {
        assistant.content = '这次没答上来：' + describeError(e)
      } else {
        assistant.content += '\n\n（连接中断，以上是已生成的内容）'
      }
    }
  } finally {
    assistant.streaming = false
    sending.value = false
    controller = null
    if (conversationId.value) refreshConversations()
    await scrollToBottom()
  }
}

function describeError(e) {
  if (e?.code === 'ECONNABORTED') return '请求超时了，请重试'

  if (!e?.response) {
    const msg = e?.message || ''
    // fetch 的网络错误在浏览器里是英文的，翻成人话
    if (!msg || msg.includes('Failed to fetch') || msg.includes('NetworkError')) {
      return '网络异常，请检查后端是否已启动'
    }
    return msg
  }

  if (e.response.status === 500) return '服务器异常，请稍后再试'
  return e.response.data?.msg || '请求失败'
}

async function retry(m) {
  if (sending.value) return
  const index = messages.value.indexOf(m)
  if (index >= 0) messages.value.splice(index, 1)
  await requestAnswer(m.question)
}

/** 流式时每个 chunk 都滚会掉帧：用 rAF 合并 */
function scheduleScroll() {
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = 0
    scrollToBottom()
  })
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 停止流式生成：abort 会让浏览器断开连接，后端 onCompletion 里 dispose */
function stop() {
  if (controller) controller.abort()
}
</script>

<style scoped>
/* ============================================================================
   AI 对话页（5.0 P0）
   沿用设计系统：纯白面板 + 1px 发丝线 + 无阴影 + 13.5px 正文
   ========================================================================== */

.ai-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: row;
  /* 很浅的灰白渐变：让悬浮输入框的玻璃质感有东西可以“透” */
  background: linear-gradient(180deg, #fcfcfc 0%, #f3f3f3 100%);
}

/* ---------------- 左侧会话栏 ---------------- */
.conv-pane {
  flex: none;
  width: 230px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 10px 12px;
  border-right: 1px solid var(--line);
  background: rgba(255, 255, 255, .42);
  backdrop-filter: blur(14px);
}

.new-chat {
  flex: none;
  width: 100%;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  color: var(--ink-1);
  font-size: 13px;
  cursor: pointer;
  transition: background .15s, border-color .15s;
}

.new-chat:hover:not(:disabled) {
  background: #f7f7f7;
}

.new-chat:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.conv-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 8px 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--ink-2);
  cursor: pointer;
}

.conv-item:hover {
  background: rgba(255, 255, 255, .72);
}

.conv-item.is-active {
  background: #fff;
  border-color: var(--line);
}

.conv-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
}

.conv-edit,
.conv-del {
  flex: none;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink-4);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity .15s, background .15s, color .15s;
}

.conv-item:hover .conv-edit,
.conv-item:hover .conv-del {
  opacity: 1;
}

.conv-edit:hover {
  background: #eef4ff;
  color: #1d4ed8;
}

.conv-del:hover {
  background: #fff1f0;
  color: #d4380d;
}

.conv-empty {
  margin: 16px 4px;
  color: var(--ink-4);
  font-size: 12px;
  text-align: center;
}

/* 右侧聊天主区 */
.ai-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ---------------- 顶栏 ---------------- */
.ai-head {
  flex: none;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 28px 16px;
  border-bottom: 1px solid var(--line);
}

.ai-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -.02em;
}

.ai-sub {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--ink-4);
}

/* ---------------- 消息区 ---------------- */
.ai-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px 8px;
}

.thread {
  max-width: 820px;
  margin: 0 auto;
}

/* 欢迎态 */
.welcome {
  max-width: 560px;
  margin: 7vh auto 0;
  text-align: center;
}

.welcome-mark {
  width: 44px;
  height: 44px;
  margin: 0 auto 18px;
  border-radius: 50%;
  background: var(--ink-1);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: .04em;
  line-height: 44px;
}

.welcome-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -.01em;
}

.welcome-sub {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--ink-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 22px;
}

.chip {
  padding: 7px 12px;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--ink-2);
  background: #fff;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  cursor: pointer;
  transition: color var(--ease), border-color var(--ease), background var(--ease);
}

.chip:hover {
  color: var(--ink-1);
  border-color: var(--ink-1);
  background: var(--hover);
}

.welcome-note {
  margin: 20px 0 0;
  font-size: 11.5px;
  color: var(--ink-5);
}

/* 消息 */
.msg {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
}

.msg.is-user {
  justify-content: flex-end;
}

.avatar {
  flex: none;
  width: 26px;
  height: 26px;
  margin-top: 2px;
  border-radius: 50%;
  background: var(--ink-1);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 26px;
  text-align: center;
  letter-spacing: .02em;
}

.avatar.is-user {
  background: var(--selected);
  color: var(--ink-2);
  border: 1px solid var(--line-strong);
  line-height: 24px;
}

.msg-main {
  max-width: 660px;
  min-width: 0;
}

.msg.is-user .msg-main {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.bubble {
  padding: 10px 14px;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--ink-1);
  background: var(--subtle);
  border: 1px solid var(--line);
  border-radius: 8px;
  /* 用户消息保留换行；助手消息由 .is-md 覆盖成正常排版 */
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.msg.is-user .bubble {
  background: var(--ink-1);
  border-color: var(--ink-1);
  color: #fff;
}

.bubble.is-thinking {
  color: var(--ink-4);
}

.bubble.is-error {
  border-style: dashed;
  color: var(--ink-2);
}

/* 助手消息的 Markdown 排版在 src/styles/markdown.css（全局样式，能作用于 v-html） */
.bubble.is-md {
  white-space: normal;
}

.retry {
  margin-left: 8px;
  padding: 0;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--ink-1);
  text-decoration: underline;
  text-underline-offset: 3px;
  background: none;
  border: 0;
  cursor: pointer;
}

.meta {
  margin-top: 5px;
  font-size: 11px;
  color: var(--ink-5);
}

/* ---------------- 输入区：悬浮玻璃窗 ---------------- */
.ai-foot {
  flex: none;
  padding: 8px 28px 14px;
  border-top: 0;
  background: transparent;
}

.composer {
  /* 把 Element Plus 输入框的边框色局部改成透明：
     textarea 的“黑圈”就是它用 --el-input-focus-border-color 画的 inset 阴影 */
  --el-input-border-color: transparent;
  --el-input-hover-border-color: transparent;
  --el-input-focus-border-color: transparent;
  --el-input-bg-color: transparent;

  position: relative;
  isolation: isolate;
  overflow: hidden;
  cursor: text;

  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  /* 空闲高度 ≈ 按钮高 + 一点点；聚焦后只长到 140 */
  height: 52px;
  padding: 6px 56px 6px 16px;
  border: 1px solid rgba(255, 255, 255, .7);
  border-radius: 28px;

  /* 玻璃：斜向的白色渐变 + 背景模糊，模拟图里的磨砂反光 */
  background:
    linear-gradient(
      135deg,
      rgba(255, 255, 255, .62) 0%,
      rgba(255, 255, 255, .24) 42%,
      rgba(255, 255, 255, .50) 100%
    );
  backdrop-filter: blur(28px) saturate(170%);
  -webkit-backdrop-filter: blur(28px) saturate(170%);

  box-shadow:
    inset 1px 1px 0 rgba(255, 255, 255, .95),
    inset -1px -1px 0 rgba(255, 255, 255, .35),
    0 18px 50px rgba(0, 0, 0, .10);

  transition:
    height 520ms cubic-bezier(.22, 1.45, .36, 1),
    padding 520ms cubic-bezier(.22, 1.45, .36, 1),
    border-color var(--ease),
    box-shadow var(--ease);
}

/* 左上角那道斜向高光，图里最显眼的“玻璃反光” */
.composer::before {
  content: "";
  position: absolute;
  z-index: 0;
  top: -55%;
  left: -10%;
  width: 120%;
  height: 105%;
  pointer-events: none;
  background: linear-gradient(
    115deg,
    rgba(255, 255, 255, .85) 0%,
    rgba(255, 255, 255, .25) 34%,
    rgba(255, 255, 255, 0) 58%
  );
  transform: rotate(-4deg);
  opacity: .5;
}

/* 内容压在反光层上面 */
.composer > * {
  position: relative;
  z-index: 1;
}

/* 提示文字平时不显示，聚焦后才浮出来 */
.hint {
  opacity: 0;
  visibility: hidden;
  transform: translateY(4px);
  transition: opacity 200ms ease, transform 200ms ease, visibility 200ms;
}

.composer:focus-within .hint {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

/* 聚焦 / 有内容：只长一点，给底部按钮和提示留出空间 */
.composer:focus-within,
.composer.is-open {
  height: 140px;
  padding: 12px 16px 44px;
}

.composer:focus-within {
  border-color: rgba(255, 255, 255, .95);
  box-shadow:
    inset 1px 1px 0 rgba(255, 255, 255, 1),
    inset -1px -1px 0 rgba(255, 255, 255, .45),
    0 20px 55px rgba(0, 0, 0, .12);
}

/* Element Plus 的 textarea 自带边框/阴影/outline/focus ring，全部去掉 */
.composer :deep(.el-textarea),
.composer :deep(.el-textarea__inner) {
  border: 0;
  outline: none;
  box-shadow: none;
  -webkit-appearance: none;
  appearance: none;
}

/* textarea 吃满剩余高度；底部工具栏用绝对定位，不参与挤压 */
.composer :deep(.el-textarea) {
  height: 100%;
  min-height: 0;
}

.composer :deep(.el-textarea__inner) {
  height: 100% !important;
  min-height: 0 !important;
  overflow-y: auto;
  padding: 8px 2px 0;
  border-radius: 0;
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--ink-1);
  background: transparent;
}

/* 输入时只保留外层的柔和阴影，textarea 自己一圈都不画。
   用 !important 是为了压过 Element Plus 的 inset 阴影（它用变量控制） */
.composer :deep(.el-textarea__inner),
.composer :deep(.el-textarea__inner:hover),
.composer :deep(.el-textarea__inner:focus),
.composer :deep(.el-textarea__inner:focus-visible) {
  border: 0 !important;
  outline: 0 !important;
  box-shadow: none !important;
  background: transparent !important;
}

.composer :deep(.el-textarea__inner::placeholder) {
  color: var(--ink-5);
}

.composer-bar {
  /* 钉在底部：容器变高时按钮不会跟着上下跑 */
  position: absolute;
  left: 16px;
  right: 10px;
  bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  /* 让空白区域的点击穿透到 textarea */
  pointer-events: none;
}

.composer-actions {
  pointer-events: auto;
}

.hint {
  font-size: 11.5px;
  color: var(--ink-4);
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 圆形发送键：纯黑底 + 白色向上箭头 */
.send-btn.el-button {
  width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--ink-1);
  color: #fff;
  font-size: 16px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, .18);
  transition: opacity var(--ease), transform var(--ease), background var(--ease);
}

.send-btn.el-button:hover {
  background: #1a1a1a;
  transform: translateY(-1px);
}

.send-btn.el-button.is-disabled,
.send-btn.el-button.is-disabled:hover {
  background: var(--ink-5);
  color: #fff;
  box-shadow: none;
  transform: none;
}

.send-btn.is-stop {
  background: var(--ink-2);
}

.send-btn .el-icon {
  font-size: 17px;
}

.count {
  font-size: 11.5px;
  color: var(--ink-5);
  font-variant-numeric: tabular-nums;
  opacity: 0;
  visibility: hidden;
  transition: opacity 180ms ease, visibility 180ms;
}

.composer:focus-within .count,
.composer.is-open .count {
  opacity: 1;
  visibility: visible;
}

.count.is-limit {
  color: var(--ink-1);
}

.disclaimer {
  max-width: 820px;
  margin: 8px auto 0;
  font-size: 11px;
  color: var(--ink-5);
  text-align: center;
}

/* ---------------- 窄屏 ---------------- */
@media (max-width: 720px) {
  .conv-pane {
    width: 168px;
    padding: 12px 6px 10px;
  }

  .new-chat {
    font-size: 12px;
  }

  .ai-head,
  .ai-body,
  .ai-foot {
    padding-left: 16px;
    padding-right: 16px;
  }

  /* 窄屏直接占满，展开高度也收一点 */
  .composer,
  .composer:focus-within {
    max-width: 100%;
  }

  .composer {
    height: 52px;
    padding: 6px 52px 6px 14px;
  }

  .composer:focus-within,
  .composer.is-open {
    height: 120px;
    padding: 10px 14px 42px;
  }

  /* 窄屏下提示文字会让输入框 toolbar 溢出，隐藏它 */
  .hint {
    display: none;
  }

  .msg-main {
    max-width: 100%;
  }

  .welcome {
    margin-top: 4vh;
  }
}
</style>
