import { useCallback, useEffect, useState } from 'react'

import {
  createExportTask,
  deleteExportTask,
  downloadExportTask,
  fetchExportTasks,
} from '../api/exportTask'
import type { ExportRequest, ExportTask } from '../types'

const POLL_INTERVAL_MS = 2000

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

/**
 * 导出任务的提交与轮询。
 *
 * <p>导出走服务端队列，所以这里只负责"提交 → 轮询状态 → 完成后下载"，
 * 页面不会被一条慢查询挂住。</p>
 */
export function useExportTasks(scope: 'mine' | 'all' = 'mine') {
  const [tasks, setTasks] = useState<ExportTask[]>([])
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const page = await fetchExportTasks(scope)
      setTasks(page.list)
      setError('')
    } catch (err) {
      setError(errorMessage(err))
    }
  }, [scope])

  useEffect(() => {
    void refresh()
  }, [refresh])

  // 有任务在跑时才轮询，全部结束就停下来
  const hasActive = tasks.some((task) => task.status === 'PENDING' || task.status === 'RUNNING')
  useEffect(() => {
    if (!hasActive) return
    const timer = window.setInterval(() => void refresh(), POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [hasActive, refresh])

  const submit = useCallback(
    async (payload: ExportRequest) => {
      setSubmitting(true)
      setError('')
      try {
        const task = await createExportTask(payload)
        await refresh()
        return task
      } finally {
        setSubmitting(false)
      }
    },
    [refresh],
  )

  const remove = useCallback(
    async (task: ExportTask) => {
      try {
        await deleteExportTask(task.taskId)
        await refresh()
      } catch (err) {
        setError(errorMessage(err))
      }
    },
    [refresh],
  )

  const download = useCallback(async (task: ExportTask) => {
    const file = await downloadExportTask(task)
    saveBlob(file.blob, file.filename)
  }, [])

  return { tasks, error, submitting, submit, refresh, remove, download }
}
