export interface ApiError {
  type: string
  detail: string
  path: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  trace_id: string
  error?: ApiError
}

export interface RenderRequest {
  template_type: string
  template: string
  data: Record<string, any>
}

export interface RenderResponse {
  rendered: any
  template_type: string
  timing_ms: number
}

export interface ExampleMeta {
  id: string
  title: string
  description: string
  difficulty: number
  template_type: string
}

export interface ExampleDetail {
  id: string
  title: string
  description: string
  difficulty: number
  template_type: string
  template: string
  data: any
  expected: any
  sections: DocSection[]
  source?: string
  prev_id: string | null
  next_id: string | null
}

export interface DocSection {
  type: 'markdown' | 'json-renderer' | string
  content: any
}

export interface TryRequest {
  template_type: string
  template: string
  data: Record<string, any>
  options?: {
    with_trace: boolean
  }
}

export interface TryResponse {
  rendered: any
  timing_ms: number
  trace: string[]
}

export interface EngineInfo {
  version: string
  components: string[]
  status: string
}
