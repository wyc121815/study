import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { fetchDataSources } from '../api/datasource'
import {
  deleteMetric,
  exportMetricCsv,
  fetchMetrics,
  runMetric,
  updateMetric,
} from '../api/metric'
import { useAuth } from '../auth/AuthContext'
import { isAdminRole } from '../auth/roles'
import Layout from '../components/Layout'
import Modal from '../components/Modal'
import type { DataSource, Metric, SqlQueryResult } from '../types'

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

function formatTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

interface RunState {
  metric: Metric
  result: SqlQueryResult
}

interface EditorState {
  metric: Metric
  name: string
  description: string
  sql: string
}

export default function MetricsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const isAdmin = isAdminRole(user?.role)

  const [metrics, setMetrics] = useState<Metric[]>([])
  const [datasources, setDatasources] = useState<DataSource[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [runState, setRunState] = useState<RunState | null>(null)
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [editorError, setEditorError] = useState('')
  const [editorBusy, setEditorBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setMetrics(await fetchMetrics())
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
    fetchDataSources().then(setDatasources).catch(() => setDatasources([]))
  }, [load])

  const canEdit = (metric: Metric) => isAdmin || metric.createdBy === user?.id

  const handleRun = async (metric: Metric) => {
    setBusyId(metric.id)
    setError('')
    setNotice('')
    try {
      const result = await runMetric(metric.id)
      setRunState({ metric, result })
    } catch (err) {
      setRunState(null)
      setError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const handleExport = async (metric: Metric) => {
    setBusyId(metric.id)
    setError('')
    try {
      const file = await exportMetricCsv(metric.id)
      saveBlob(file.blob, file.filename)
      setNotice(`已导出「${metric.name}」`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const handleDelete = async (metric: Metric) => {
    if (!window.confirm(`确认删除指标「${metric.name}」？该操作不可恢复。`)) return
    setBusyId(metric.id)
    setError('')
    try {
      await deleteMetric(metric.id)
      setNotice(`已删除「${metric.name}」`)
      if (runState?.metric.id === metric.id) setRunState(null)
      await load()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const openEditor = (metric: Metric) => {
    setEditorError('')
    setEditor({
      metric,
      name: metric.name,
      description: metric.description ?? '',
      sql: metric.sql,
    })
  }

  const submitEditor = async (event: FormEvent) => {
    event.preventDefault()
    if (!editor) return
    setEditorBusy(true)
    setEditorError('')
    try {
      await updateMetric(editor.metric.id, {
        name: editor.name.trim(),
        description: editor.description.trim() || undefined,
        datasourceId: editor.metric.datasourceId,
        sql: editor.sql,
      })
      setEditor(null)
      setNotice('指标已更新')
      await load()
    } catch (err) {
      setEditorError(errorMessage(err))
    } finally {
      setEditorBusy(false)
    }
  }

  const datasourceLabel = (metric: Metric) => {
    const source = datasources.find((item) => item.id === metric.datasourceId)
    return source?.name ?? metric.datasourceName ?? `#${metric.datasourceId}`
  }

  return (
    <Layout>
      <section className="panel panel--wide">
        <div className="panel__head">
          <div>
            <h1>指标</h1>
            <p className="muted">
              在「SQL 查询」页把调好的 SQL 存为指标，就能被所有人复用；共 {metrics.length} 个
            </p>
          </div>
          <button type="button" className="button button--ghost" onClick={() => void navigate('/sql')}>
            去 SQL 查询
          </button>
        </div>

        {error ? (
          <p className="banner banner--error">
            {error}
            <button type="button" className="link-button" onClick={() => setError('')}>
              关闭
            </button>
          </p>
        ) : null}
        {notice ? (
          <p className="banner banner--ok">
            {notice}
            <button type="button" className="link-button" onClick={() => setNotice('')}>
              关闭
            </button>
          </p>
        ) : null}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>指标</th>
                <th>数据源</th>
                <th>创建人</th>
                <th>更新时间</th>
                <th className="table__actions">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={5} className="table__empty">
                    加载中…
                  </td>
                </tr>
              ) : metrics.length === 0 ? (
                <tr>
                  <td colSpan={5} className="table__empty">
                    还没有指标，去「SQL 查询」里把调好的 SQL 存为指标吧
                  </td>
                </tr>
              ) : (
                metrics.map((metric) => (
                  <tr key={metric.id}>
                    <td>
                      <div className="cell__main">{metric.name}</div>
                      {metric.description ? <div className="cell__sub">{metric.description}</div> : null}
                    </td>
                    <td>{datasourceLabel(metric)}</td>
                    <td>{metric.createdByName ?? (metric.createdBy ? `#${metric.createdBy}` : '—')}</td>
                    <td>{formatTime(metric.updatedAt)}</td>
                    <td className="table__actions">
                      <button
                        type="button"
                        className="link-button"
                        disabled={busyId === metric.id}
                        onClick={() => void handleRun(metric)}
                      >
                        执行
                      </button>
                      <button
                        type="button"
                        className="link-button"
                        disabled={busyId === metric.id}
                        onClick={() => void handleExport(metric)}
                      >
                        导出
                      </button>
                      <button
                        type="button"
                        className="link-button"
                        onClick={() =>
                          void navigate('/sql', {
                            state: { connectionId: metric.datasourceId, sql: metric.sql },
                          })
                        }
                      >
                        在查询台打开
                      </button>
                      {canEdit(metric) ? (
                        <>
                          <button type="button" className="link-button" onClick={() => openEditor(metric)}>
                            编辑
                          </button>
                          <button
                            type="button"
                            className="link-button link-button--danger"
                            disabled={busyId === metric.id}
                            onClick={() => void handleDelete(metric)}
                          >
                            删除
                          </button>
                        </>
                      ) : null}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {runState ? (
          <div className="query-result">
            <div className="query-result__meta">
              <span className="badge badge--ok">{runState.metric.name}</span>
              <span className="muted">
                返回 {runState.result.rowCount} 行 · 耗时 {runState.result.elapsedMillis} ms
              </span>
              {runState.result.truncated ? <span className="badge badge--failed">结果已截断</span> : null}
              <button type="button" className="link-button" onClick={() => setRunState(null)}>
                收起
              </button>
            </div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    {runState.result.columns.map((column, index) => (
                      <th key={`${column.name}-${index}`}>
                        <div className="cell__main">{column.label || column.name}</div>
                        <div className="cell__sub">{column.typeName}</div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {runState.result.rows.length === 0 ? (
                    <tr>
                      <td colSpan={Math.max(runState.result.columns.length, 1)} className="table__empty">
                        查询成功，没有返回数据
                      </td>
                    </tr>
                  ) : (
                    runState.result.rows.map((row, rowIndex) => (
                      <tr key={rowIndex}>
                        {runState.result.columns.map((_, columnIndex) => {
                          const cell = row[columnIndex]
                          return (
                            <td key={columnIndex} className="mono query-result__cell">
                              {cell === null || cell === undefined ? (
                                <span className="query-result__null">NULL</span>
                              ) : (
                                String(cell)
                              )}
                            </td>
                          )
                        })}
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
      </section>

      {editor ? (
        <Modal title={`编辑指标 · ${editor.metric.name}`} onClose={() => setEditor(null)}>
          <form className="form" onSubmit={submitEditor}>
            <div className="field">
              <label className="field__label">指标名称</label>
              <input
                className="field__input"
                value={editor.name}
                onChange={(event) => setEditor({ ...editor, name: event.target.value })}
                required
              />
            </div>
            <div className="field">
              <label className="field__label">口径说明</label>
              <input
                className="field__input"
                value={editor.description}
                onChange={(event) => setEditor({ ...editor, description: event.target.value })}
              />
            </div>
            <div className="field">
              <label className="field__label">SQL（只读，保存时会重新校验）</label>
              <textarea
                className="field__input query__editor"
                value={editor.sql}
                onChange={(event) => setEditor({ ...editor, sql: event.target.value })}
                spellCheck={false}
              />
            </div>
            <p className="muted">数据源：{datasourceLabel(editor.metric)}</p>
            {editorError ? <p className="form__fail">{editorError}</p> : null}
            <div className="form__actions">
              <span />
              <div className="form__actions-right">
                <button type="button" className="button button--ghost" onClick={() => setEditor(null)}>
                  取消
                </button>
                <button type="submit" className="button" disabled={editorBusy || !editor.name.trim()}>
                  {editorBusy ? '保存中…' : '保存'}
                </button>
              </div>
            </div>
          </form>
        </Modal>
      ) : null}
    </Layout>
  )
}
