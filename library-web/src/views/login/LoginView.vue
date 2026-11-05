<template>
  <div class="login-page">
    <!-- ================= 左：品牌区（纯黑，最强对比；窄屏隐藏） ================= -->
    <aside class="brand-side">
      <!-- 星空：三层静态小星星，闪烁相位错开（否则会整片一起呼吸，很假） -->
      <span class="stars stars-a" aria-hidden="true"></span>
      <span class="stars stars-b" aria-hidden="true"></span>
      <span class="stars stars-c" aria-hidden="true"></span>

      <!-- 流星雨：十颗错位飞行，起点全在面板外，所以看起来是从边缘飞进来的 -->
      <span class="meteor m1" aria-hidden="true"></span>
      <span class="meteor m2" aria-hidden="true"></span>
      <span class="meteor m3" aria-hidden="true"></span>
      <span class="meteor m4" aria-hidden="true"></span>
      <span class="meteor m5" aria-hidden="true"></span>
      <span class="meteor m6" aria-hidden="true"></span>
      <span class="meteor m7" aria-hidden="true"></span>
      <span class="meteor m8" aria-hidden="true"></span>
      <span class="meteor m9" aria-hidden="true"></span>
      <span class="meteor m10" aria-hidden="true"></span>

      <div class="brand">
        <img class="brand-mark" src="/brand/logo.svg" alt="星河图书" width="30" height="30" />
        <span class="brand-name">星河图书</span>
      </div>

      <div class="brand-body">
        <!-- 两行同步淡入 -->
        <h1 class="brand-title">
          <span class="line">满船清梦压星河</span>
          <span class="line">一馆书香待君来</span>
        </h1>
        <ul class="brand-points">
          <li>馆藏检索 · 分类筛选</li>
          <li>在线借阅 · 到馆归还</li>
          <li>通知公告 · 借阅记录</li>
        </ul>
      </div>

      <div class="brand-foot mono">LIBRARY · v4.0</div>
    </aside>

    <!-- ================= 右：表单区 ================= -->
    <main class="form-side">
      <div class="form-box">
        <h2 class="title">登录</h2>
        <p class="subtitle">登录后按账号角色显示不同功能</p>

        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent>
          <el-form-item prop="username">
            <el-input
              ref="usernameRef"
              v-model="form.username"
              placeholder="用户名"
              size="large"
              autocomplete="username"
              @keyup.enter="focusPassword"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              ref="passwordRef"
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit"
            :loading="loading"
            @click="handleSubmit"
          >
            登 录
          </el-button>

          <!-- 注册入口（2.1 新增）：跳独立的注册页，不在这里弹窗 -->
          <div class="register-row">
            <span class="register-tip">还没有账号？</span>
            <button class="register-link" type="button" @click="goRegister">注册新用户</button>
          </div>
        </el-form>

        <!-- 忘记密码的提示必须放在登录页：真忘了密码的人根本进不去个人中心 -->
        <p class="forgot-tip">
          忘记密码？请联系管理员重置
          <a class="mail" href="mailto:yzhe2722@gmail.com">yzhe2722@gmail.com</a>
        </p>
      </div>
    </main>

    <!-- 卡片下方的信息条：把外围的空白变成"有用的一行"，而不是拿装饰去填 -->
    <footer class="login-foot">
      <span class="mono">© 2026 星河图书</span>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref(null)
const loading = ref(false)

// 两个输入框的引用，用来实现「回车跳到下一格」
const usernameRef = ref(null)
const passwordRef = ref(null)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/**
 * 用户名框按回车 → 把焦点送到密码框（而不是直接提交）
 *
 * 为什么不直接登录：新用户输完用户名会顺手敲回车，
 * 这时密码还是空的，直接提交只会弹一句「请输入密码」—— 反而打断输入节奏。
 * 「回车 = 下一步」才符合表单操作的直觉。
 *
 * ⚠️ el-input 的聚焦必须调它自己暴露的 focus() 方法，
 *    不能对 $el 调 —— $el 只是个 div 外壳，focus() 聚焦不上去。
 */
function focusPassword() {
  passwordRef.value?.focus()
}

/** 去注册页（注册是独立页面，不在这里弹窗，避免两套表单互相干扰） */
function goRegister() {
  router.push('/register')
}

onMounted(() => {
  // 从注册页跳回来时会带 ?username=xxx，帮用户省一次输入
  const u = route.query.username
  if (typeof u === 'string' && u) {
    form.username = u
  }
})

async function handleSubmit() {
  // 先做前端校验，通过了再发请求
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const data = await store.doLogin({ ...form })
    ElMessage.success(`欢迎回来，${data.user.realName || data.user.username}`)

    // 登录前被拦下来的页面，登录后送回去
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : '/home')
  } catch (e) {
    // 失败提示（"用户名或密码错误"）已由 request.js 的拦截器弹过了
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  display: grid;
  /* 定宽双栏：黑 380 + 白 540 = 920，整块在画布上居中。
     之前左栏用 44% + 右栏 flex:1，宽屏上黑块被拉到 560px、表单被推到最右边 */
  grid-template-columns: 380px 540px;
  place-content: center;
  padding: 40px 24px;
  /* 极淡网格（3% 对比度）：给大片留白一点"纸"的质感，又不抢卡片的注意力 */
  background-color: var(--canvas);
  background-image:
    linear-gradient(to right, rgba(0, 0, 0, .03) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(0, 0, 0, .03) 1px, transparent 1px);
  background-size: 32px 32px;
}

/* 卡片下方信息条：和卡片同宽，左右两端对齐 */
.login-foot {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--ink-4);
}

/* ================= 左：品牌 ================= */
.brand-side {
  position: relative;
  overflow: hidden;
  min-height: 560px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 44px 36px;
  background: #000;
  color: #fff;
  border-radius: var(--r-lg) 0 0 var(--r-lg);
}

/* ---------------- 静态星空 ----------------
   用 box-shadow 列表画星星：一个 1px 的元素 + 几十条阴影 = 几十颗星，
   比写几十个 <span> 干净得多。三层各自有闪烁周期和相位，不会整片同步。 */
.stars {
  position: absolute;
  left: 0;
  top: 0;
  width: 1px;
  height: 1px;
  border-radius: 50%;
  background: #fff;
  pointer-events: none;
  animation: twinkle 7s ease-in-out infinite;
}

.stars-a {
  box-shadow:
    14px 26px 0 0 rgba(255, 255, 255, .8),
    62px 74px 0 0 rgba(255, 255, 255, .45),
    108px 18px 0 0 rgba(255, 255, 255, .65),
    156px 96px 0 0 rgba(255, 255, 255, .5),
    205px 38px 0 0 rgba(255, 255, 255, .75),
    248px 130px 0 0 rgba(255, 255, 255, .4),
    296px 62px 0 0 rgba(255, 255, 255, .7),
    340px 110px 0 0 rgba(255, 255, 255, .5),
    28px 168px 0 0 rgba(255, 255, 255, .6),
    88px 205px 0 0 rgba(255, 255, 255, .45),
    140px 258px 0 0 rgba(255, 255, 255, .8),
    196px 220px 0 0 rgba(255, 255, 255, .4),
    252px 292px 0 0 rgba(255, 255, 255, .65),
    300px 240px 0 0 rgba(255, 255, 255, .5),
    356px 310px 0 0 rgba(255, 255, 255, .7),
    46px 330px 0 0 rgba(255, 255, 255, .45);
}

.stars-b {
  box-shadow:
    76px 44px 0 1px rgba(255, 255, 255, .55),
    130px 120px 0 1px rgba(255, 255, 255, .4),
    218px 82px 0 1px rgba(255, 255, 255, .6),
    320px 30px 0 1px rgba(255, 255, 255, .45),
    24px 240px 0 1px rgba(255, 255, 255, .5),
    168px 300px 0 1px rgba(255, 255, 255, .65),
    274px 180px 0 1px rgba(255, 255, 255, .4),
    348px 410px 0 1px rgba(255, 255, 255, .55),
    102px 380px 0 1px rgba(255, 255, 255, .45),
    210px 440px 0 1px rgba(255, 255, 255, .6),
    58px 470px 0 1px rgba(255, 255, 255, .4),
    300px 500px 0 1px rgba(255, 255, 255, .5),
    12px 520px 0 1px rgba(255, 255, 255, .45),
    160px 545px 0 1px rgba(255, 255, 255, .4);
  animation-duration: 5.3s;
  animation-delay: -1.7s;
}

.stars-c {
  box-shadow:
    190px 8px 0 0 rgba(255, 255, 255, .5),
    38px 108px 0 0 rgba(255, 255, 255, .7),
    128px 170px 0 0 rgba(255, 255, 255, .4),
    230px 250px 0 0 rgba(255, 255, 255, .6),
    330px 200px 0 0 rgba(255, 255, 255, .45),
    80px 290px 0 0 rgba(255, 255, 255, .55),
    176px 350px 0 0 rgba(255, 255, 255, .4),
    264px 400px 0 0 rgba(255, 255, 255, .65),
    340px 460px 0 0 rgba(255, 255, 255, .45),
    120px 490px 0 0 rgba(255, 255, 255, .55),
    220px 530px 0 0 rgba(255, 255, 255, .4),
    30px 420px 0 0 rgba(255, 255, 255, .6);
  animation-duration: 8.4s;
  animation-delay: -3.9s;
}

@keyframes twinkle {
  0%, 100% { opacity: .45; }
  50% { opacity: 1; }
}

/* ---------------- 流星雨 ----------------
   六颗错位飞行，统一 24.7°（= atan(258/560)）从左上往右下。
   每颗起点都在面板外，所以"淡入"那一瞬看不见 —— 看起来就是从边缘飞进来。
   拖尾 = 90° 线性渐变（尾巴全透明 → 头部纯白）+ 沿线柔光；
   头部再叠一颗四角星（clip-path 画的，不依赖字体字形），比单个圆点更像流星。 */
.meteor {
  position: absolute;
  height: 2px;
  border-radius: 2px;
  pointer-events: none;
  background: linear-gradient(90deg,
      rgba(255, 255, 255, 0) 0%,
      rgba(255, 255, 255, .15) 45%,
      rgba(255, 255, 255, .55) 78%,
      #fff 100%);
  box-shadow: 0 0 10px rgba(255, 255, 255, .45);
  transform: rotate(24.7deg);
  animation: shower 5s linear infinite;
}

/* 头部那颗四角星 */
.meteor::after {
  content: '';
  position: absolute;
  right: -3px;
  top: 50%;
  width: 9px;
  height: 9px;
  margin-top: -4.5px;
  background: #fff;
  clip-path: polygon(50% 0%, 61% 39%, 100% 50%, 61% 61%, 50% 100%, 39% 61%, 0% 50%, 39% 39%);
}

/* 六颗的起点、长度、快慢、相位全都不同，才能铺满整块黑 */
.m1 { left: -140px; top: -60px;  width: 140px; animation-duration: 4.2s; animation-delay: 0s; }
.m2 { left: 60px;   top: -120px; width: 110px; animation-duration: 5.4s; animation-delay: -1.4s; }
.m3 { left: -200px; top: 140px;  width: 170px; animation-duration: 6.2s; animation-delay: -3.1s; }
.m4 { left: -120px; top: 300px;  width: 125px; animation-duration: 4.8s; animation-delay: -2.2s; }
.m5 { left: 180px;  top: -160px; width: 100px; animation-duration: 5.8s; animation-delay: -.7s; }
.m6 { left: -160px; top: 470px;  width: 150px; animation-duration: 6.8s; animation-delay: -4.5s; }

/* 后四颗专门补右侧和中部，否则面板右边会显得空 */
.m7 { left: 40px;   top: -220px; width: 120px; animation-duration: 5.0s; animation-delay: -3.6s; }
.m8 { left: -240px; top: 60px;   width: 130px; animation-duration: 4.5s; animation-delay: -.5s; }
.m9 { left: -60px;  top: 210px;  width: 155px; animation-duration: 6.6s; animation-delay: -1.9s; }
.m10 { left: 120px; top: 340px;  width: 115px; animation-duration: 5.6s; animation-delay: -4.0s; }

/* 位移和角度必须匹配：258/560 ≈ tan24.7°，否则拖尾方向和飞行方向会对不上 */
@keyframes shower {
  0% {
    opacity: 0;
    transform: translate(0, 0) rotate(24.7deg);
  }
  6% {
    opacity: 1;
  }
  55% {
    opacity: 1;
    transform: translate(560px, 258px) rotate(24.7deg);
  }
  62% {
    opacity: 0;
    transform: translate(560px, 258px) rotate(24.7deg);
  }
  100% {
    opacity: 0;
    transform: translate(560px, 258px) rotate(24.7deg);
  }
}

/* 文字压在流星之上（流星从字背后飞过） */
.brand,
.brand-body,
.brand-foot {
  position: relative;
  z-index: 1;
}



/* 系统里关了动效的用户，就别给他们放流星和闪烁了 */
@media (prefers-reduced-motion: reduce) {
  .meteor,
  .stars {
    animation: none;
    opacity: 0;
  }

  /* 诗句直接给完整版，别让人等淡入 */
  .line {
    animation: none;
    opacity: 1;
    filter: none;
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* logo.svg 内部是 fill="currentColor"，但用 <img> 引时拿不到父级颜色，
   所以黑底上直接反色成白 */
.brand-mark {
  width: 30px;
  height: 30px;
  flex: none;
  display: block;
  filter: invert(1);
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -.01em;
}

.brand-title {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.75;
  letter-spacing: .02em;
}

/* 两行同步淡入（同一个 delay，不再错拍）。
   filter: blur 是可选的雾化效果，删掉那两行就变成纯透明度淡入。 */
.line {
  display: block;
  animation: line-in 1.4s ease-out .3s both;
}

@keyframes line-in {
  from {
    opacity: 0;
    filter: blur(6px);
  }
  to {
    opacity: 1;
    filter: blur(0);
  }
}

.brand-points {
  margin: 26px 0 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  line-height: 2.1;
  color: rgba(255, 255, 255, .6);
}

.brand-foot {
  font-size: 10.5px;
  letter-spacing: .06em;
  color: rgba(255, 255, 255, .35);
}

/* ================= 右：表单 ================= */
.form-side {
  display: grid;
  place-items: center;
  padding: 44px 40px;
  background: #fff;
  border-radius: 0 var(--r-lg) var(--r-lg) 0;
}

.form-box {
  width: 100%;
  max-width: 340px;
}

.title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -.03em;
}

.subtitle {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--ink-3);
}

.submit {
  width: 100%;
  margin-top: 4px;
}

/* ---------------- 注册入口 ---------------- */
.register-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 14px;
  font-size: 13px;
}

.register-tip {
  color: var(--ink-4);
}

/* 用原生 button 而不是 el-button：这里只要一条文字链接的观感，
   引组件反而要跟它自带的 padding / hover 样式打架 */
.register-link {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: #000;
  font-weight: 500;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.register-link:hover {
  color: var(--ink-3);
}

/* ---------------- 忘记密码提示 ---------------- */
.forgot-tip {
  margin: 10px 0 0;
  text-align: center;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--ink-4);
}

.forgot-tip .mail {
  color: var(--ink-2);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.forgot-tip .mail:hover {
  color: #000;
}

/* ---------------- 窄屏降级 ---------------- */
/* 920 放不下时先收窄两栏 */
@media (max-width: 1000px) {
  .login-page {
    grid-template-columns: 320px minmax(0, 1fr);
  }
}

/* 再窄就砍掉左栏，回到单栏卡片 */
@media (max-width: 820px) {
  .login-page {
    grid-template-columns: minmax(0, 420px);
  }

  .brand-side {
    display: none;
  }

  .form-side {
    border-radius: var(--r-lg);
  }
}
</style>
