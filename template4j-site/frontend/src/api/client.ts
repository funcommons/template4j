import axios from 'axios'
import { v4 as uuidv4 } from 'uuid'
import { ElMessage } from 'element-plus'

export const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

client.interceptors.request.use(cfg => {
  if (['post', 'put', 'patch', 'delete'].includes(cfg.method || '')) {
    const body = typeof cfg.data === 'string' ? cfg.data : JSON.stringify(cfg.data || {})
    cfg.headers['Idempotency-Key'] = simpleHash(body)
  }
  const storedTraceId = sessionStorage.getItem('trace_id')
  cfg.headers['X-Trace-Id'] = storedTraceId || ''
  return cfg
})

function simpleHash(s: string): string {
  let hash = 0
  for (let i = 0; i < s.length; i++) {
    const ch = s.charCodeAt(i)
    hash = ((hash << 5) - hash) + ch
    hash |= 0
  }
  return Math.abs(hash).toString(16).padStart(8, '0')
}

client.interceptors.response.use(
  res => {
    const body = res.data
    const traceId = res.headers['x-trace-id'] || body.trace_id
    if (traceId) sessionStorage.setItem('trace_id', traceId)
    if (body.code === 0) return body.data
    const errorDetail = body.error?.detail || body.message || 'Unknown error'
    ElMessage.error(errorDetail)
    throw new Error(errorDetail)
  },
  err => {
    const msg = err.response?.data?.message || 'Network error'
    ElMessage.error(msg)
    throw err
  }
)
