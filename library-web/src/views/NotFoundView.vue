<template>
  <!-- 404：B 版不做插画，用排版解决 -->
  <div class="nf">
    <div class="nf-code mono">404</div>
    <p class="nf-text">这个页面不存在，或者已经被删掉了。</p>
    <el-button type="primary" @click="router.push('/books')">返回图书管理</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.nf {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 48px;
}

.nf-code {
  font-size: 72px;
  font-weight: 500;
  letter-spacing: -.04em;
  line-height: 1;
  color: #000;
}

.nf-text {
  margin: 0;
  font-size: 13.5px;
  color: var(--ink-3);
}
</style>
