import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'

// Arco Design for PC
import ArcoVue from '@arco-design/web-vue'
import '@arco-design/web-vue/dist/arco.css'

// Vant for Mobile
import Vant from 'vant'
import 'vant/lib/index.css'

// 设计 token 必须在两套组件库的样式之后引入，才能覆盖它们的主题变量
import './styles/tokens.css'
import './styles/mobile.css'
import './styles/admin.css'

const app = createApp(App)

app.use(store)
app.use(router)
app.use(ArcoVue)
app.use(Vant)

app.mount('#app')
