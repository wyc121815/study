import { download, http } from './client'
import type { ExportRequest, ExportTask, PageResult } from '../types'

/** 提交导出任务：立即返回任务号，真正跑 SQL 的是后端队列消费者 */
export function createExportTask(payload: ExportRequest): Promise<ExportTask> {
  return http.post<ExportTask>('/api/exports', payload)
}

export function fetchExportTasks(scope: 'mine' | 'all' = 'mine'): Promise<PageResult<ExportTask>> {
  const params = new URLSearchParams({ scope, page: '1', size: '20' })
  return http.get<PageResult<ExportTask>>(`/api/exports?${params.toString()}`)
}

export function deleteExportTask(taskId: string): Promise<null> {
  return http.del<null>(`/api/exports/${taskId}`)
}

export function downloadExportTask(task: ExportTask): Promise<{ blob: Blob; filename: string }> {
  return download(`/api/exports/${task.taskId}/download`, undefined, task.fileName ?? 'export.csv')
}
