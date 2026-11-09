<template>
  <div class="perf">
    <h2>{{ t('perf.title') }}: 0.4.0 → 0.5.0</h2>
    <p class="intro">{{ t('perf.intro') }}</p>

    <div class="chart">
      <div v-for="b in benchmarks" :key="b.name" class="row">
        <div class="row-name">{{ b.name }}</div>
        <div class="row-bars">
          <div class="bar bar-old" :style="{ width: barWidth(b.old) }">
            <span class="bar-val">{{ format(b.old) }}</span>
          </div>
          <div class="bar bar-new" :style="{ width: barWidth(b.new) }">
            <span class="bar-val">{{ format(b.new) }}</span>
            <span class="bar-speedup">{{ b.speedup }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="legend">
      <span><span class="dot dot-old"></span>0.4.0 ({{ t('perf.before') }})</span>
      <span><span class="dot dot-new"></span>0.5.0 ({{ t('perf.after') }})</span>
    </div>

    <section class="live">
      <h3>{{ t('perf.liveBenchmark') }}</h3>
      <i18n-t keypath="perf.liveHint" tag="p" scope="global">
        <template #code><code>/api/demo/compile</code></template>
      </i18n-t>
      <el-button type="primary" :loading="loading" @click="run">▶ {{ t('perf.runLive') }}</el-button>
      <div v-if="live" class="live-result">
        <div class="live-row"><strong>{{ live.iterations }}</strong> {{ t('perf.iterations') }}</div>
        <div class="live-row">bare render: <strong>{{ live.bareRenderTotalMs }} ms</strong> (avg {{ live.bareAvgNs }} ns)</div>
        <div class="live-row">compiled render: <strong>{{ live.compiledRenderTotalMs }} ms</strong> (avg {{ live.compiledAvgNs }} ns)</div>
        <div class="live-row">{{ t('perf.speedupLabel') }}: <strong class="speedup">{{ live.speedup }}</strong></div>
      </div>
    </section>

    <section class="methodology">
      <h3>{{ t('perf.methodology') }}</h3>
      <ul>
        <li>{{ t('perf.method1') }}</li>
        <li>
          <i18n-t keypath="perf.method2" tag="span" scope="global">
            <template #code><code>"Hello, `name`!"</code></template>
          </i18n-t>
        </li>
        <li>{{ t('perf.method3') }}</li>
        <li>{{ t('perf.method4') }}</li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'

const { t } = useI18n()

const benchmarks = [
  { name: 'Simple (warm)', old: 2060, new: 360, speedup: '5.7x' },
  { name: 'JSON (warm)',   old: 6780, new: 3920, speedup: '1.7x' },
  { name: 'Script (warm)', old: 25600, new: 23700, speedup: '~' },
  { name: 'Each-100 (warm)', old: 1265000, new: 949000, speedup: '1.3x' },
]
const max = Math.max(...benchmarks.flatMap(b => [b.old, b.new]))

function barWidth(v: number) { return `${(v / max) * 100}%` }
function format(v: number) {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + ' ms'
  if (v >= 1_000) return (v / 1_000).toFixed(2) + ' μs'
  return v + ' ns'
}

const loading = ref(false)
const live = ref<any>(null)
async function run() {
  loading.value = true
  try {
    const res = await axios.get('/api/demo/compile')
    live.value = res.data?.data
  } catch (e: any) {
    alert(t('perf.runFailed') + ': ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.perf { max-width: 1100px; margin: 0 auto; padding: 20px 24px; }
.perf h2 { font-size: 32px; margin-bottom: 8px; }
.intro { color: var(--el-text-color-secondary); margin-bottom: 32px; }
.chart { display: flex; flex-direction: column; gap: 16px; }
.row { display: grid; grid-template-columns: 160px 1fr; gap: 12px; align-items: center; }
.row-name { font-family: monospace; font-size: 13px; }
.row-bars { display: flex; flex-direction: column; gap: 4px; }
.bar { height: 24px; display: flex; align-items: center; padding-left: 8px; color: white; font-family: monospace; font-size: 11px; min-width: 60px; border-radius: 3px; }
.bar-old { background: #ef4444; }
.bar-new { background: #10b981; }
.bar-val { margin-right: 8px; }
.bar-speedup { background: rgba(255,255,255,0.25); padding: 1px 6px; border-radius: 2px; }
.legend { display: flex; gap: 24px; margin-top: 24px; font-size: 13px; }
.dot { display: inline-block; width: 12px; height: 12px; border-radius: 2px; margin-right: 6px; vertical-align: middle; }
.dot-old { background: #ef4444; }
.dot-new { background: #10b981; }
.live { margin-top: 60px; padding: 24px; border: 1px solid var(--el-border-color); border-radius: 8px; }
.live h3 { margin-top: 0; }
.live-result { margin-top: 16px; font-family: monospace; }
.live-row { padding: 4px 0; }
.speedup { color: #10b981; font-size: 18px; }
.methodology { margin-top: 40px; }
.methodology ul { color: var(--el-text-color-secondary); }
.methodology code { background: var(--el-fill-color); padding: 1px 6px; border-radius: 2px; font-size: 12px; }
</style>
