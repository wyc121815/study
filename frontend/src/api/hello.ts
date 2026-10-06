import { apiGet } from './client'

export interface HelloResponse {
  message: string
  service: string
  time: string
}

// 调用后端 GET /api/hello
export function fetchHello(name = 'React'): Promise<HelloResponse> {
  return apiGet<HelloResponse>(`/api/hello?name=${encodeURIComponent(name)}`)
}

