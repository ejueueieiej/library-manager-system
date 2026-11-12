<template>
  <div class="ai-page">
    <!-- ================= 顶栏 ================= -->
    <header class="ai-head">
      <div class="ai-head-main">
        <h2 class="ai-title">AI 馆员</h2>
        <p class="ai-sub">基于馆藏知识的问答 · 当前为单轮对话，暂不记忆上下文</p>
      </div>
      <el-button v-if="messages.length" link class="clear-btn" :disabled="sending" @click="clearChat">
        清空对话
      </el-button>
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

          <p class="welcome-note">本版不保存对话，刷新页面即清空。</p>
        </div>

        <!-- 消息列表 -->
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="'is-' + m.role">
          <div v-if="m.role === 'assistant'" class="avatar">AI</div>

          <div class="msg-main">
            <div
              class="bubble"
              :class="{ 'is-error': m.error, 'is-thinking': m.streaming && !m.content }"
            >
              <span>{{ m.content || (m.streaming ? '正在思考…' : '') }}</span>
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
      <div class="composer">
        <el-input
          ref="inputRef"
          v-model="draft"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
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
            <el-button v-if="USE_STREAM && sending" @click="stop">停止生成</el-button>
            <el-button
              v-else
              type="primary"
              :loading="sending && !USE_STREAM"
              :disabled="!canSend"
              @click="send"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>

      <p class="disclaimer">AI 也会犯错，重要信息请以图书详情页为准。</p>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { chat, streamChat } from '@/api/ai'

/* ---------------- 状态 ---------------- */
const messages = ref([])
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

// 方案 C：后端 SSE 就绪后改成 true；出问题改回 false 立刻走 JSON 接口
const USE_STREAM = true

let controller = null
let rafId = 0

onMounted(() => {
  // 进页面直接能打字，少点一次鼠标
  inputRef.value?.focus?.()
})

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
 * 方案 C：USE_STREAM 决定这一轮走 SSE 还是普通 JSON。
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

  try {
    if (USE_STREAM) {
      controller = new AbortController()

      await streamChat({
        content: question,
        signal: controller.signal,
        onMeta: (meta) => {
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
    } else {
      const data = await chat(question)
      assistant.content = data?.content || '（没有返回内容）'
      assistant.meta = buildMeta(data)
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
    await scrollToBottom()
  }
}

function buildMeta(data) {
  if (!data) return ''
  const parts = []
  if (data.model) parts.push(data.model)
  if (data.elapsedMs) parts.push((data.elapsedMs / 1000).toFixed(1) + ' s')
  if (data.mock) parts.push('演示模式')
  return parts.join(' · ')
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

function clearChat() {
  if (sending.value) return
  messages.value = []
  nextTick(() => inputRef.value?.focus?.())
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
  flex-direction: column;
  background: #fff;
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

.clear-btn {
  flex: none;
  color: var(--ink-3);
}

.clear-btn:hover {
  color: var(--ink-1);
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
  /* 本版不渲染 Markdown，靠它保留模型返回的换行和缩进 */
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

/* ---------------- 输入区 ---------------- */
.ai-foot {
  flex: none;
  padding: 12px 28px 16px;
  border-top: 1px solid var(--line);
  background: #fff;
}

.composer {
  max-width: 820px;
  margin: 0 auto;
  padding: 8px 10px 6px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  transition: border-color var(--ease);
}

.composer:focus-within {
  border-color: var(--ink-1);
}

/* Element Plus 的 textarea 自带边框和阴影，这里全部去掉，交给外层 .composer */
.composer :deep(.el-textarea__inner) {
  padding: 4px 2px;
  border: 0;
  box-shadow: none;
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--ink-1);
  background: transparent;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 6px;
}

.hint {
  font-size: 11.5px;
  color: var(--ink-4);
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.count {
  font-size: 11.5px;
  color: var(--ink-5);
  font-variant-numeric: tabular-nums;
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
  .ai-head,
  .ai-body,
  .ai-foot {
    padding-left: 16px;
    padding-right: 16px;
  }

  .msg-main {
    max-width: calc(100vw - 120px);
  }

  .welcome {
    margin-top: 4vh;
  }
}
</style>
