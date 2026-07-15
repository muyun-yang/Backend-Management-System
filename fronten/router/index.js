import HomeInterface from "../components/HomeInterface.vue"
import chat_interface from "../components/chat_interface2.vue"
import knowledge_interface from "../components/knowledge_interface.vue"
import {createRouter, createWebHistory} from "vue-router"

const routes = [
    {
        path: '/',
        name: 'Home',
        component: HomeInterface
    },
    {
        path: '/chat',
        name: 'Chat',
        component: chat_interface
    },
    {
        path: '/knowledge',
        name: 'knowledge',
        component: knowledge_interface
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router