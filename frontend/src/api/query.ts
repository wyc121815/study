import { download, http } from './client'
import type { PageResult, QueryHistoryItem, SqlQueryPayload, SqlQueryResult } from '../types'

/** 在指定连接上执行 SQL，默认服务端只读 */
export function executeSql(payload: SqlQueryPayload): Promise<SqlQueryResult> {
  return http.post<SqlQueryResult>('/api/queries/execute', payload)
}

/** 导出 CSV，返回文件流 */
export function exportSqlCsv(payload: SqlQueryPayload): Promise<{ blob: Blob; filename: string }> {
  return download('/api/queries/export', payload)
}

export interface HistoryQuery {
  scope?: 'mine' | 'all'
  page?: number
  size?: number
}

export function fetchQueryHistory(query: HistoryQuery = {}): Promise<PageResult<QueryHistoryItem>> {
  const params = new URLSearchParams()
  params.set('scope', query.scope ?? 'mine')
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return http.get<PageResult<QueryHistoryItem>>(`/api/queries/history?${params.toString()}`)
}

export function deleteQueryHistory(id: number): Promise<null> {
  return http.del<null>(`/api/queries/history/${id}`)
}

export function clearQueryHistory(scope: 'mine' | 'all' = 'mine'): Promise<null> {
  return http.del<null>(`/api/queries/history?scope=${scope}`)
}
