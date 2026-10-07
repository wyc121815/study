import { download, http } from './client'
import type { Metric, MetricPayload, SqlQueryResult } from '../types'

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

/** 执行指标，指标自带数据源 */
export function runMetric(id: number, maxRows?: number): Promise<SqlQueryResult> {
  const query = maxRows ? `?maxRows=${maxRows}` : ''
  return http.post<SqlQueryResult>(`/api/metrics/${id}/run${query}`)
}

export function exportMetricCsv(id: number, maxRows?: number): Promise<{ blob: Blob; filename: string }> {
  const query = maxRows ? `?maxRows=${maxRows}` : ''
  return download(`/api/metrics/${id}/export${query}`)
}
