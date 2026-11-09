import { client } from './client'
import type { ExampleMeta, ExampleDetail } from '@/types/api'

export async function listExamples(): Promise<{ examples: ExampleMeta[], total: number }> {
  return client.get('/learn')
}

export async function getExample(id: string): Promise<ExampleDetail> {
  return client.get(`/learn/${id}`)
}
