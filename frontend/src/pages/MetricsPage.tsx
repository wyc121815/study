import { lazy, Suspense, useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { fetchDataSources } from '../api/datasource'
import {
  deleteMetric,
  exportMetricCsv,
  fetchMetricRanking,
  fetchMetrics,
  runMetric,
  updateMetric,
} from '../api/metric'
import { useAuth } from '../auth/AuthContext'
import { isAdminRole } from '../auth/roles'
import Layout from '../components/Layout'
import Modal from '../components/Modal'
import { buildChartSpec, type ChartType } from '../charts/chartSpec'
import type { DataSource, Metric, MetricRankItem, SqlQueryResult } from '../types'

type ViewMode = ChartType | 'table'

// 图表库体积不小，按需加载：只有真的要看图时才下载这个 chunk
const MetricChart = lazy(() => import('../components/MetricChart'))

const VIEW_OPTIONS: { value: ViewMode; label: string }[] = [
  { value: 'line', label: '折线' },
  { value: 'bar', label: '柱状' },
  { value: 'pie', label: '饼图' },
  { value: 'table', label: '表格' },
]

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
  const [ranking, setRanking] = useState<MetricRankItem[]>([])
  const [datasources, setDatasources] = useState<DataSource[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [runState, setRunState] = useState<RunState | null>(null)
  const [view, setView] = useState<ViewMode>('line')
  const [noCache, setNoCache] = useState(false)

  const [editor, setEditor] = useState<EditorState | null>(null)
  const [editorError, setEditorError] = useState('')
  const [editorBusy, setEditorBusy] = useState(false)

  const chartSpec = useMemo(
    () => (runState ? buildChartSpec(runState.result) : null),
    [runState],
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [list, top] = await Promise.all([fetchMetrics(), fetchMetricRanking(8)])
      setMetrics(list)
      setRanking(top)
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

  // 没有可用图表时（比如结果全是文本列）自动落到表格视图
  useEffect(() => {
    if (runState && !chartSpec) {
      setView('table')
    }
  }, [runState, chartSpec])

  const canEdit = (metric: Metric) => isAdmin || metric.createdBy === user?.id

  const handleRun = async (metric: Metric) => {
    setBusyId(metric.id)
    setError('')
    setNotice('')
    try {
      const result = await runMetric(metric.id, undefined, noCache)
      setRunState({ metric, result })
      setView(chartSpecOf(result)?.preferred ?? 'table')
      fetchMetricRanking(8).then(setRanking).catch(() => undefined)
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
          <div className="panel__head-actions">
            <label className="switch">
              <input
                type="checkbox"
                checked={noCache}
                onChange={(event) => setNoCache(event.target.checked)}
              />
              <span>跳过缓存</span>
            </label>
            <button type="button" className="button button--ghost" onClick={() => void navigate('/sql')}>
              去 SQL 查询
            </button>
          </div>
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

        <div className="metrics-layout">
          <div className="metrics-layout__main">
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
                      <tr key={metric.id} className={runState?.metric.id === metric.id ? 'row--active' : ''}>
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
                            查看
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
              <div className="chart-card">
                <div className="chart-card__head">
                  <div>
                    <h2>{runState.metric.name}</h2>
                    <p className="muted">
                      {runState.result.rowCount} 行 ·{' '}
                      {runState.result.cached
                        ? `缓存命中（首次查询 ${runState.result.elapsedMillis} ms）`
                        : `耗时 ${runState.result.elapsedMillis} ms`}
                      {runState.result.truncated ? ' · 已截断' : ''}
                    </p>
                  </div>
                  <div className="chart-card__actions">
                    {runState.result.cached ? (
                      <span className="badge badge--ok">缓存命中</span>
                    ) : (
                      <span className="badge badge--unknown">实时查询</span>
                    )}
                    <div className="segmented">
                      {VIEW_OPTIONS.map((option) => (
                        <button
                          key={option.value}
                          type="button"
                          className={view === option.value ? 'segmented__item segmented__item--active' : 'segmented__item'}
                          disabled={option.value === 'pie' && !chartSpec?.pieCapable}
                          onClick={() => setView(option.value)}
                        >
                          {option.label}
                        </button>
                      ))}
                    </div>
                    <button type="button" className="link-button" onClick={() => setRunState(null)}>
                      收起
                    </button>
                  </div>
                </div>

                {view !== 'table' && chartSpec ? (
                  <Suspense fallback={<div className="chart chart--loading">图表加载中…</div>}>
                    <MetricChart spec={chartSpec} type={view} />
                  </Suspense>
                ) : (
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
                )}
              </div>
            ) : null}
          </div>

          <aside className="metrics-layout__side">
            <div className="side__head">
              <h2>热门指标</h2>
            </div>
            {ranking.length === 0 ? (
              <p className="muted">还没有查询记录，热度会随使用累积</p>
            ) : (
              <ol className="ranking">
                {ranking.map((item, index) => (
                  <li key={item.metricId} className="ranking__item">
                    <span className={`ranking__no ranking__no--${index < 3 ? index + 1 : 'rest'}`}>{index + 1}</span>
                    <span className="ranking__body">
                      <span className="ranking__name">{item.name}</span>
                      {item.datasourceName ? (
                        <span className="ranking__sub">{item.datasourceName}</span>
                      ) : null}
                    </span>
                    <span className="ranking__runs">{item.runs} 次</span>
                  </li>
                ))}
              </ol>
            )}
            <p className="side__note">次数由 Redis 计数，用于热度排序</p>
          </aside>
        </div>
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
              <label className="field__label">SQL（保存时会重新校验只读）</label>
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

/** 独立成函数，供 handleRun 在 setState 之前推断默认视图。 */
function chartSpecOf(result: SqlQueryResult) {
  return buildChartSpec(result)
}
