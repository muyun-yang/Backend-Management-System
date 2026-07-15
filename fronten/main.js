import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import DOMPurify from 'dompurify';
import router from './router/index.js';
// createApp(App).mount('#app')
import { QuillEditor } from '@vueup/vue-quill'
const app = createApp(App)
app.use(router)
app.component('QuillEditor', QuillEditor)
app.mount('#app')

DOMPurify.setConfig({
    ALLOWED_TAGS: ['p', 'b', 'i', 'u', 'h1', 'h2', 'h3', 'ul', 'ol', 'li', 'br'],
    ALLOWED_ATTR: ['style'],
    FORBID_ATTR: ['style', 'class', 'on*'],
    FORBID_TAGS: ['script', 'iframe', 'form', 'object', 'embed'],
    RETURN_TRUSTED_TYPE: true
});