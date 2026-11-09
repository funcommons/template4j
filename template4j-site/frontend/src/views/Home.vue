<template>
  <div class="home">
    <div class="hero">
      <h1>{{ t('app.title') }}</h1>
      <p class="subtitle">{{ t('home.subtitle') }}</p>
      <div class="cta">
        <el-button type="primary" size="large" @click="router.push('/learn')">{{ t('home.startLearning') }}</el-button>
        <el-button type="success" size="large" @click="router.push('/try')">{{ t('home.tryOnline') }}</el-button>
        <el-button size="large" @click="router.push('/docs')">{{ t('home.viewDocs') }}</el-button>
      </div>
    </div>
    <el-row :gutter="20" class="features">
      <el-col :span="6" v-for="f in features" :key="f.title">
        <el-card shadow="hover">
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

const features = computed(() => [
  { title: t('home.feature1_title'), desc: t('home.feature1_desc') },
  { title: t('home.feature2_title'), desc: t('home.feature2_desc') },
  { title: t('home.feature3_title'), desc: t('home.feature3_desc') },
  { title: t('home.feature4_title'), desc: t('home.feature4_desc') },
])
</script>

<style scoped>
.home { text-align: center; }
.hero { padding: 60px 20px; }
.hero h1 { font-size: 2.5em; margin-bottom: 10px; }
.subtitle { color: #666; font-size: 1.2em; margin-bottom: 30px; }
.cta { display: flex; gap: 12px; justify-content: center; }
.features { margin-top: 40px; }
.features h3 { margin: 0 0 8px 0; }
.features p { color: #666; margin: 0; }
</style>
