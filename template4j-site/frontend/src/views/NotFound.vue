<template>
  <div class="notfound" data-testid="app-not-found">
    <div class="code">404</div>
    <p class="msg">{{ t('notfound.message') }}</p>
    <div class="actions">
      <el-button type="primary" @click="$router.push('/')">{{ t('notfound.back') }}</el-button>
      <el-button @click="$router.push('/learn')">{{ t('nav.learn') }}</el-button>
      <el-button @click="$router.push('/docs')">{{ t('nav.docs') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<style scoped>
.notfound {
  max-width: 640px;
  margin: 80px auto;
  text-align: center;
}
.code {
  font-size: 72px;
  font-weight: 700;
  font-family: 'JetBrains Mono', Menlo, monospace;
  color: var(--el-color-primary);
  line-height: 1;
}
.msg {
  color: var(--el-text-color-secondary);
  margin: 16px 0 24px;
}
.actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}
</style>
