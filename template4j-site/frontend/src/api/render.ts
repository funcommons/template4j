import { client } from './client'
import type { RenderRequest, RenderResponse } from '@/types/api'

export async function render(req: RenderRequest): Promise<RenderResponse> {
  return client.post('/render', req)
}
