import { client } from './client'
import type { TryRequest, TryResponse } from '@/types/api'

export async function tryRender(req: TryRequest): Promise<TryResponse> {
  return client.post('/try', req)
}
