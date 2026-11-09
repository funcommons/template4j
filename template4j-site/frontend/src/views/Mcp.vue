<template>
  <div class="mcp">
    <h2>{{ t('mcp.title') }}</h2>
    <p class="intro">
      {{ t('mcp.intro') }}
    </p>

    <section class="steps">
      <h3>{{ t('mcp.steps3') }}</h3>

      <div class="step">
        <div class="step-num">1</div>
        <div class="step-body">
          <strong>{{ t('mcp.step1Title') }}</strong>
          <pre>mvn -Pshade -pl template4j-core package -DskipTests
# {{ t('mcp.step1Hint') }}</pre>
        </div>
      </div>

      <div class="step">
        <div class="step-num">2</div>
        <div class="step-body">
          <strong>{{ t('mcp.step2Title') }}</strong>
          <i18n-t keypath="mcp.step2Hint" tag="p" scope="global">
            <template #path><code>~/Library/Application Support/Claude/claude_desktop_config.json</code></template>
          </i18n-t>
          <pre>{{ claudeConfig }}</pre>
          <el-button size="small" @click="copy(claudeConfig)">{{ t('common.copy') }}</el-button>
        </div>
      </div>

      <div class="step">
        <div class="step-num">3</div>
        <div class="step-body">
          <strong>{{ t('mcp.step3Title') }}</strong>
          <i18n-t keypath="mcp.step3Hint" tag="p" scope="global">
            <template #code><code>/api/demo/mcp</code></template>
          </i18n-t>
          <div class="test-row">
            <el-button size="small" @click="callToolsList" :loading="loadingTools">▶ tools/list</el-button>
            <el-button size="small" @click="callRender" :loading="loadingCall">▶ render_template</el-button>
          </div>
        </div>
      </div>
    </section>

    <section v-if="output" class="output">
      <h3>{{ t('mcp.response') }}</h3>
      <pre>{{ output }}</pre>
    </section>

    <section class="security">
      <h3>{{ t('mcp.securityTitle') }}</h3>
      <ul>
        <li>{{ t('mcp.security1') }}</li>
        <li><strong>{{ t('mcp.security2Bold') }}</strong>:{{ t('mcp.security2') }}</li>
        <li>{{ t('mcp.security3') }}</li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const claudeConfig = `{
  "mcpServers": {
    "template4j": {
      "command": "java",
      "args": ["-jar", "/path/to/template4j-core-shaded.jar", "mcp"]
    }
  }
}`

const loadingTools = ref(false)
const loadingCall = ref(false)
const output = ref('')

function copy(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success(t('mcp.copied'))
}

async function call(method: string, params: any) {
  if (method === 'tools/list') loadingTools.value = true
  else loadingCall.value = true
  try {
    const req = { jsonrpc: '2.0', id: 1, method, params }
    const res = await axios.post('/api/demo/mcp', req)
    output.value = JSON.stringify(res.data, null, 2)
  } catch (e: any) {
    output.value = 'ERROR: ' + (e.response?.data?.message || e.message)
  } finally {
    loadingTools.value = false
    loadingCall.value = false
  }
}

function callToolsList() { call('tools/list', {}) }
function callRender() {
  call('tools/call', {
    name: 'render_template',
    arguments: { template: '{"v":"{@v}"}', data: { v: 42 } },
  })
}
</script>

<style scoped>
.mcp { max-width: 900px; margin: 0 auto; padding: 20px 24px; }
.mcp h2 { font-size: 32px; margin-bottom: 8px; }
.intro { color: var(--el-text-color-secondary); margin-bottom: 32px; }
.steps h3, .output h3, .security h3 { font-size: 20px; margin: 32px 0 12px; }
.step { display: grid; grid-template-columns: 32px 1fr; gap: 12px; margin-bottom: 20px; }
.step-num { background: var(--el-color-primary); color: white; width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-weight: 600; font-size: 14px; }
.step-body { background: var(--el-fill-color-light); padding: 12px 16px; border-radius: 6px; }
.step-body strong { font-family: monospace; }
.step-body p { margin: 8px 0; color: var(--el-text-color-secondary); font-size: 14px; }
.step-body pre, .output pre { background: #1a1a1a; color: #e5e7eb; padding: 12px; border-radius: 4px; font-family: monospace; font-size: 12px; overflow-x: auto; margin: 8px 0; }
.step-body code { background: var(--el-fill-color); padding: 1px 6px; border-radius: 2px; font-size: 12px; }
.test-row { display: flex; gap: 8px; margin-top: 12px; }
.output pre { min-height: 100px; }
.security { background: #fef3c7; border: 1px solid #f59e0b; border-radius: 6px; padding: 16px; margin-top: 40px; }
.security h3 { margin-top: 0; color: #92400e; }
.security ul { margin: 0; padding-left: 20px; color: #78350f; }
.security code { background: rgba(0,0,0,0.08); padding: 1px 6px; border-radius: 2px; font-size: 12px; }
</style>
