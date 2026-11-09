<template>
  <div class="layout">
    <header class="nav-bar">
      <el-menu
        :default-active="route.path"
        mode="horizontal"
        router
        class="nav-menu"
      >
        <el-menu-item index="/">{{ t('nav.home') }}</el-menu-item>
        <el-menu-item index="/playground">{{ t('nav.playground') }}</el-menu-item>
        <el-menu-item index="/showcase">{{ t('nav.showcase') }}</el-menu-item>
        <el-menu-item index="/perf">{{ t('nav.perf') }}</el-menu-item>
        <el-menu-item index="/mcp">{{ t('nav.mcp') }}</el-menu-item>
        <el-menu-item index="/learn">{{ t('nav.learn') }}</el-menu-item>
        <el-menu-item index="/docs">{{ t('nav.docs') }}</el-menu-item>
      </el-menu>
      <!-- 动作区放在 el-menu 之外: 原生元素写进 el-menu 会被解析成空子菜单 -->
      <div class="nav-actions">
        <el-dropdown trigger="click" @command="onLocaleChange">
          <span class="nav-lang-trigger">
            🌐 {{ currentLangLabel }} <el-icon class="el-icon--right"><i-ep-arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-item command="zh-CN" :disabled="locale === 'zh-CN'">中文</el-dropdown-item>
            <el-dropdown-item command="en-US" :disabled="locale === 'en-US'">English</el-dropdown-item>
          </template>
        </el-dropdown>
        <a
          class="nav-gh"
          href="https://github.com/funcommons/template4j"
          target="_blank"
          rel="noopener"
        >GitHub ↗</a>
      </div>
    </header>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const { t, locale } = useI18n()

const currentLangLabel = computed(() => locale.value === 'zh-CN' ? '中文' : 'English')

function onLocaleChange(cmd: string) {
  if (cmd === locale.value) return
  locale.value = cmd
  try { localStorage.setItem('locale', cmd) } catch {}
  // 整页 reload,让 vue-i18n 重新解析所有翻译键(避免组件内 computed 缓存)
  location.reload()
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.nav-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: var(--el-bg-color);
}
.nav-menu {
  flex: 1;
  min-width: 0; /* flex item 默认 min-width:auto 不可收缩, 溢出修复 (e2e smoke @600/@800) */
  border-bottom: none;
}
.nav-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 16px;
}
.nav-lang-trigger {
  display: inline-flex; align-items: center; gap: 4px;
  cursor: pointer; font-size: 13px; font-weight: 600;
  color: var(--el-text-color-primary);
  padding: 0 8px; height: 100%;
}
.nav-lang-trigger:hover { color: var(--el-color-primary); }
.nav-gh { list-style: none; display: flex; align-items: center; padding: 0 20px; }
.nav-gh a { color: var(--el-text-color-primary); text-decoration: none; font-weight: 600; }
.nav-gh a:hover { color: var(--el-color-primary); }
.main-content {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
}
</style>
