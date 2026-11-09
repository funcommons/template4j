<template>
  <div class="layout">
    <el-menu :default-active="route.path" mode="horizontal" router class="nav-menu">
      <el-menu-item index="/">{{ t('nav.home') }}</el-menu-item>
      <el-menu-item index="/learn">{{ t('nav.learn') }}</el-menu-item>
      <el-menu-item index="/try">{{ t('nav.try') }}</el-menu-item>
      <el-menu-item index="/docs">{{ t('nav.docs') }}</el-menu-item>
    </el-menu>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
const route = useRoute()
const { t } = useI18n()
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.nav-menu {
  position: sticky;
  top: 0;
  z-index: 100;
}
.main-content {
  flex: 1;
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}
</style>
