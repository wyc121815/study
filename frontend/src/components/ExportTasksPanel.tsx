import type { ExportTask } from '../types'

const STATUS_TEXT: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '导出中',
  DONE: '已完成',
  FAILED: '失败',
}

const STATUS_CLASS: Record<string, string> = {
  PENDING: 'badge--unknown',
  RUNNING: 'badge--unknown',
  DONE: 'badge--ok',
  FAILED: 'badge--failed',
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString('zh-CN', { hour12: false })
}

interface Props {
  tasks: ExportTask[]
  error?: string
  onDownload: (task: ExportTask) => void
  onDelete: (task: ExportTask) => void
}

/**
 * 导出任务列表。导出在服务端排队执行，这里只展示进度和下载入口。
 */
export default function ExportTasksPanel({ tasks, error, onDownload, onDelete }: Props) {
  return (
    <div className="export-panel">
      <div className="side__head">
        <h2>导出任务</h2>
      </div>

      {error ? <p className="side__error">{error}</p> : null}

      {tasks.length === 0 ? (
        <p className="muted">还没有导出任务</p>
      ) : (
        <ul className="export-list">
          {tasks.map((task) => (
            <li key={task.taskId} className="export-item">
              <div className="export-item__head">
                <span className={`badge ${STATUS_CLASS[task.status] ?? 'badge--unknown'}`}>
                  {STATUS_TEXT[task.status] ?? task.status}
                </span>
                <span className="export-item__time">{formatTime(task.createdAt)}</span>
              </div>
              <div className="export-item__name">
                {task.connectionName ?? '—'}
                {task.rowCount !== null && task.rowCount !== undefined ? ` · ${task.rowCount} 行` : ''}
              </div>
              <div className="export-item__sql mono">{task.sql}</div>
              {task.status === 'FAILED' && task.message ? (
                <div className="export-item__fail">{task.message}</div>
              ) : null}
              <div className="export-item__actions">
                {task.downloadable ? (
                  <button type="button" className="link-button" onClick={() => onDownload(task)}>
                    下载
                  </button>
                ) : null}
                <button
                  type="button"
                  className="link-button link-button--danger"
                  onClick={() => onDelete(task)}
                >
                  删除
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
