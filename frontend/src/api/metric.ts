import { download, http } from './client'
import type { Metric, MetricPayload, MetricRankItem, SqlQueryResult } from '../types'

export function fetchMetrics(): Promise<Metric[]> {
  return http.get<Metric[]>('/api/metrics')
}

export function fetchMetric(id: number): Promise<Metric> {
  return http.get<Metric>(`/api/metrics/${id}`)
}

export function createMetric(payload: MetricPayload): Promise<Metric> {
  return http.post<Metric>('/api/metrics', payload)
}

export function updateMetric(id: number, payload: MetricPayload): Promise<Metric> {
  return http.put<Metric>(`/api/metrics/${id}`, payload)
}

export function deleteMetric(id: number): Promise<null> {
  return http.del<null>(`/api/metrics/${id}`)
}

/** 执行指标，指标自带数据源；noCache=true 时跳过 Redis 缓存直连数据库 */
export function runMetric(id: number, maxRows?: number, noCache = false): Promise<SqlQueryResult> {
  const params = new URLSearchParams()
  if (maxRows) params.set('maxRows', String(maxRows))
  if (noCache) params.set('noCache', 'true')
  const query = params.toString() ? `?${params.toString()}` : ''
  return http.post<SqlQueryResult>(`/api/metrics/${id}/run${query}`)
}

/** 指标热度排行（被查询次数，来自 Redis ZSET） */
export function fetchMetricRanking(limit = 8): Promise<MetricRankItem[]> {
  return http.get<MetricRankItem[]>(`/api/metrics/ranking?limit=${limit}`)
}

export function exportMetricCsv(id: number, maxRows?: number): Promise<{ blob: Blob; filename: string }> {
  const query = maxRows ? `?maxRows=${maxRows}` : ''
  return download(`/api/metrics/${id}/export${query}`)
}
