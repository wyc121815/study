import { http } from './client'
import type { DataSource } from '../types'

/** 可用于查询的数据源：所有登录用户都能读 */
export function fetchDataSources(): Promise<DataSource[]> {
  return http.get<DataSource[]>('/api/datasources')
}
