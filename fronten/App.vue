<template>
  <div id="app" :class="{ 'dark-mode': darkMode }">
    <aside class="sidebar-nav">
      <div class="sidebar-header">
        <span class="logo-icon">
          <img src="./images/police.png" width="40" alt="Logo" />
        </span>
        <h3>菜单</h3>
      </div>
      <div class="nav-links">
        <router-link to="/" class="nav-item">
          <span class="nav-icon">🏠</span>
          <span class="nav-text">主页</span>
        </router-link>
        <router-link to="/chat" class="nav-item">
          <span class="nav-icon">💬</span>
          <span class="nav-text">聊天</span>
        </router-link>
        <router-link to="/knowledge" class="nav-item">
          <span class="nav-icon">📕</span>
          <span class="nav-text">知识库</span>
        </router-link>
      </div>
    </aside>

    <main class="main-content">
      <header class="app-header">
        <div class="header-content">
          <div class="header-title">
            <h1 class="app-title">Qianwen Chat</h1>
            <p class="app-subtitle">您的智能对话助手</p>
          </div>
          <div class="header-actions">
            <button @click="toggleDarkMode" class="theme-toggle">
              {{ darkMode ? '☀️' : '🌙' }}
            </button>
          </div>
        </div>
      </header>

      <router-view></router-view>
    </main>
  </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
      darkMode: false,
    };
  },
  created() {
    this.darkMode = localStorage.getItem('darkMode') === 'true';
  },
  watch: {
    darkMode(newVal) {
      localStorage.setItem('darkMode', newVal);
      document.documentElement.setAttribute('data-theme', newVal ? 'dark' : 'light');
    }
  },
  methods: {
    toggleDarkMode() {
      this.darkMode = !this.darkMode;
    },
  }
};
</script>

<style>
@import "./assets/app.css";
</style>