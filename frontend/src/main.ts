import { createPinia } from 'pinia'
import { createApp } from 'vue'

import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 组件按需引入可以再优化，骨架阶段先全量注册，省掉 unplugin 的配置
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
