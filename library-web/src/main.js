import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// ★ 必须放在 element-plus 样式【之后】引入，否则我们的设计系统覆盖不生效
import './styles/theme.css'
// 中文语言包：让分页器、日期选择器等组件显示中文
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// Element Plus 的图标库，全量注册后模板里可直接用 <el-icon><Search /></el-icon>
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注册所有图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 全量引入 Element Plus（学习阶段这样最简单，不用配自动按需导入插件）
app.use(ElementPlus, { locale: zhCn })
app.use(router)

app.mount('#app')
