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

const app = createApp(App)

app.use(store)
app.use(router)
app.use(ArcoVue)
app.use(Vant)

app.mount('#app')
