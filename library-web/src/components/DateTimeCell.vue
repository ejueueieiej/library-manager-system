<template>
  <span class="dt" :class="{ 'is-empty': !parts.date }">
    <span v-if="parts.date" class="dt-date tnum">{{ parts.date }}</span>
    <span v-if="parts.time" class="dt-time tnum">{{ parts.time }}</span>
    <span v-if="!parts.date">—</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { splitDateTime } from '@/utils/datetime'

const props = defineProps({
  value: { type: String, default: '' }
})

const parts = computed(() => splitDateTime(props.value))
</script>

<style scoped>
/* 年月日一行、时分秒一行。颜色一律继承父级（inherit），
   这样外层给它挂 .muted 或 .is-alert 时，整个两行块会一起变色 */
.dt {
  display: inline-flex;
  flex-direction: column;
  line-height: 1.3;
}

.dt-date {
  color: inherit;
}

.dt-time {
  color: inherit;
  opacity: .72;
  font-size: 12px;
}

.is-empty {
  color: var(--ink-4);
}
</style>
