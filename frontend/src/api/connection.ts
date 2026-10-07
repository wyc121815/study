import { http } from './client'
import type {
  Connection,
  ConnectionPayload,
  ConnectionTestResult,
  DbTypeInfo,
  PageResult,
} from '../types'

export interface ConnectionQuery {
  keyword?: string
  dbType?: string
  page?: number
  size?: number
}

export function fetchDbTypes(): Promise<DbTypeInfo[]> {
  return http.get<DbTypeInfo[]>('/api/db-types')
}

export function fetchConnections(query: ConnectionQuery): Promise<PageResult<Connection>> {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.dbType) params.set('dbType', query.dbType)
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 10))
  return http.get<PageResult<Connection>>(`/api/connections?${params.toString()}`)
}

export function createConnection(payload: ConnectionPayload): Promise<Connection> {
  return http.post<Connection>('/api/connections', payload)
}

export function updateConnection(id: number, payload: ConnectionPayload): Promise<Connection> {
  return http.put<Connection>(`/api/connections/${id}`, payload)
}

export function deleteConnection(id: number): Promise<null> {
  return http.del<null>(`/api/connections/${id}`)
}

/** 用表单里的配置直接试连，不落库 */
export function testConnection(payload: ConnectionPayload & { id?: number }): Promise<ConnectionTestResult> {
  return http.post<ConnectionTestResult>('/api/connections/test', payload)
}

/** 对已保存的连接做一次真实探测 */
export function testSavedConnection(id: number): Promise<ConnectionTestResult> {
  return http.post<ConnectionTestResult>(`/api/connections/${id}/test`)
}
