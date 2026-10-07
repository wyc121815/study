import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { fetchDataSources } from '../api/datasource'
import { createMetric } from '../api/metric'
import {
  clearQueryHistory,
  deleteQueryHistory,
  executeSql,
  exportSqlCsv,
  fetchQueryHistory,
} from '../api/query'
import { useAuth } from '../auth/AuthContext'
import { isAdminRole } from '../auth/roles'
import Layout from '../components/Layout'
import Modal from '../components/Modal'
import type { DataSource, QueryHistoryItem, SqlQueryResult } from '../types'

const DEFAULT_MAX_ROWS = 200
const HISTORY_SIZE = 20

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

export default function SqlConsolePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const isAdmin = isAdminRole(user?.role)

  const [datasources, setDatasources] = useState<DataSource[]>([])
  const [datasourceId, setDatasourceId] = useState('')
  const [sql, setSql] = useState('')
  const [maxRows, setMaxRows] = useState(String(DEFAULT_MAX_ROWS))

  const [running, setRunning] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [result, setResult] = useState<SqlQueryResult | null>(null)

  const [history, setHistory] = useState<QueryHistoryItem[]>([])
  const [historyScope, setHistoryScope] = useState<'mine' | 'all'>('mine')
  const [historyError, setHistoryError] = useState('')

  const [savingMetric, setSavingMetric] = useState(false)
  const [metricName, setMetricName] = useState('')
  const [metricDescription, setMetricDescription] = useState('')
  const [metricError, setMetricError] = useState('')
  const [metricBusy, setMetricBusy] = useState(false)

  const datasourceRef = useRef<DataSource[]>([])
  datasourceRef.current = datasources

  const selected = useMemo(
    () => datasources.find((item) => String(item.id) === datasourceId) ?? null,
    [datasources, datasourceId],
  )

  const canRun = Boolean(datasourceId) && sql.trim().length > 0 && !running

  useEffect(() => {
    fetchDataSources()
      .then((list) => {
        setDatasources(list)
        setDatasourceId((current) => current || (list[0] ? String(list[0].id) : ''))
      })
      .catch((err) => setError(errorMessage(err)))
  }, [])

  // 从「指标」页跳过来时，把指标的数据源与 SQL 填进编辑器
  useEffect(() => {
    const incoming = location.state as { connectionId?: number; sql?: string } | null
    if (!incoming) return
    if (incoming.connectionId) setDatasourceId(String(incoming.connectionId))
    if (incoming.sql) setSql(incoming.sql)
    navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const loadHistory = useCallback(async () => {
    setHistoryError('')
    try {
      const page = await fetchQueryHistory({ scope: historyScope, page: 1, size: HISTORY_SIZE })
      setHistory(page.list)
    } catch (err) {
      setHistory([])
      setHistoryError(errorMessage(err))
    }
  }, [historyScope])

  useEffect(() => {
    void loadHistory()
  }, [loadHistory])

  const currentMaxRows = (): number | undefined => {
    const parsed = Number.parseInt(maxRows, 10)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
  }

  const run = useCallback(async () => {
    if (!datasourceId || !sql.trim()) return
    setRunning(true)
    setError('')
    setNotice('')
    try {
      const data = await executeSql({
        connectionId: Number(datasourceId),
        sql,
        maxRows: Number.parseInt(maxRows, 10) > 0 ? Number.parseInt(maxRows, 10) : undefined,
      })
      setResult(data)
    } catch (err) {
      setResult(null)
      setError(errorMessage(err))
    } finally {
      setRunning(false)
      await loadHistory()
    }
  }, [datasourceId, sql, maxRows, loadHistory])

  const handleExport = async () => {
    if (!datasourceId || !sql.trim()) return
    setError('')
    try {
      const file = await exportSqlCsv({
        connectionId: Number(datasourceId),
        sql,
        maxRows: currentMaxRows(),
      })
      saveBlob(file.blob, file.filename)
      setNotice('已导出当前查询结果 CSV')
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    void run()
  }

  const handleEditorKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault()
      void run()
    }
  }

  const loadFromHistory = (item: QueryHistoryItem) => {
    const known = datasourceRef.current.some((source) => source.id === item.connectionId)
    if (item.connectionId && !known) {
      setError(`历史记录里的连接「${item.connectionName ?? item.connectionId}」已不可用或未开放查询`)
    } else if (item.connectionId) {
      setDatasourceId(String(item.connectionId))
      setError('')
    }
    setSql(item.sql)
    setResult(null)
  }

  const handleDeleteHistory = async (item: QueryHistoryItem) => {
    try {
      await deleteQueryHistory(item.id)
      await loadHistory()
    } catch (err) {
      setHistoryError(errorMessage(err))
    }
  }

  const handleClearHistory = async () => {
    const label = historyScope === 'all' ? '全部用户的' : '我的'
    if (!window.confirm(`确认清空${label}查询历史？该操作不可恢复。`)) return
    try {
      await clearQueryHistory(historyScope)
      await loadHistory()
    } catch (err) {
      setHistoryError(errorMessage(err))
    }
  }

  const openSaveMetric = () => {
    setMetricName('')
    setMetricDescription('')
    setMetricError('')
    setSavingMetric(true)
  }

  const submitMetric = async (event: FormEvent) => {
    event.preventDefault()
    if (!datasourceId) return
    setMetricBusy(true)
    setMetricError('')
    try {
      const metric = await createMetric({
        name: metricName.trim(),
        description: metricDescription.trim() || undefined,
        datasourceId: Number(datasourceId),
        sql,
      })
      setSavingMetric(false)
      setNotice(`已存为指标「${metric.name}」，可在「指标」页查看`)
    } catch (err) {
      setMetricError(errorMessage(err))
    } finally {
      setMetricBusy(false)
    }
  }

  return (
    <Layout>
      <section className="panel panel--wide">
        <div className="panel__head">
          <div>
            <h1>SQL 查询</h1>
            <p className="muted">
              在开放查询的数据源上执行只读 SQL，默认最多 {DEFAULT_MAX_ROWS} 行、单条语句 30 秒超时
            </p>
          </div>
          <button type="button" className="button button--ghost" onClick={() => void navigate('/metrics')}>
            查看指标
          </button>
        </div>

        <div className="console">
          <div className="console__main">
            <form className="query" onSubmit={handleSubmit}>
              <div className="query__bar">
                <select
                  className="field__input query__connection"
                  value={datasourceId}
                  onChange={(event) => setDatasourceId(event.target.value)}
                >
                  {datasources.length === 0 ? <option value="">暂无开放查询的数据源</option> : null}
                  {datasources.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}（{item.dbTypeLabel}）
                    </option>
                  ))}
                </select>
                <label className="query__rows">
                  最大行数
                  <input
                    type="number"
                    min={1}
                    className="field__input"
                    value={maxRows}
                    onChange={(event) => setMaxRows(event.target.value)}
                  />
                </label>
                <button type="submit" className="button" disabled={!canRun}>
                  {running ? '执行中…' : '执行'}
                </button>
                <button
                  type="button"
                  className="button button--ghost"
                  disabled={!canRun}
                  onClick={() => void handleExport()}
                >
                  导出 CSV
                </button>
                <button type="button" className="button button--ghost" disabled={!canRun} onClick={openSaveMetric}>
                  存为指标
                </button>
              </div>

              {selected ? (
                <p className="query__target mono">
                  {selected.host}:{selected.port}
                  {selected.databaseName ? `/${selected.databaseName}` : ''} · {selected.username}
                </p>
              ) : (
                <p className="query__target">
                  没有可用于查询的数据源。请在「数据库连接」里给需要查询的连接打开「允许查询」。
                </p>
              )}

              <textarea
                className="field__input query__editor"
                placeholder={'SELECT * FROM your_table LIMIT 100;\n\nCtrl / ⌘ + Enter 执行'}
                value={sql}
                onChange={(event) => setSql(event.target.value)}
                onKeyDown={handleEditorKeyDown}
                spellCheck={false}
              />
            </form>

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

            {result ? (
              <div className="query-result">
                <div className="query-result__meta">
                  <span className="badge badge--ok">{result.statementType}</span>
                  <span className="muted">
                    返回 {result.rowCount} 行 · 耗时 {result.elapsedMillis} ms
                  </span>
                  {result.truncated ? <span className="badge badge--failed">结果已截断</span> : null}
                </div>

                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th className="query-result__index">#</th>
                        {result.columns.map((column, index) => (
                          <th key={`${column.name}-${index}`}>
                            <div className="cell__main">{column.label || column.name}</div>
                            <div className="cell__sub">{column.typeName}</div>
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {result.rows.length === 0 ? (
                        <tr>
                          <td colSpan={result.columns.length + 1} className="table__empty">
                            查询成功，没有返回数据
                          </td>
                        </tr>
                      ) : (
                        result.rows.map((row, rowIndex) => (
                          <tr key={rowIndex}>
                            <td className="query-result__index mono">{rowIndex + 1}</td>
                            {result.columns.map((_, columnIndex) => {
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
          </div>

          <aside className="console__side">
            <div className="side__head">
              <h2>查询历史</h2>
              <button type="button" className="link-button" onClick={() => void handleClearHistory()}>
                清空
              </button>
            </div>

            {isAdmin ? (
              <div className="side__scope">
                <button
                  type="button"
                  className={historyScope === 'mine' ? 'chip chip--active' : 'chip'}
                  onClick={() => setHistoryScope('mine')}
                >
                  我的
                </button>
                <button
                  type="button"
                  className={historyScope === 'all' ? 'chip chip--active' : 'chip'}
                  onClick={() => setHistoryScope('all')}
                >
                  全部
                </button>
              </div>
            ) : null}

            {historyError ? <p className="side__error">{historyError}</p> : null}

            {history.length === 0 ? (
              <p className="muted">还没有查询记录</p>
            ) : (
              <ul className="history">
                {history.map((item) => (
                  <li key={item.id} className="history__item">
                    <button type="button" className="history__open" onClick={() => loadFromHistory(item)}>
                      <span className="history__sql mono">{item.sql}</span>
                      <span className="history__meta">
                        <span className={`badge badge--${item.success ? 'ok' : 'failed'}`}>
                          {item.success ? '成功' : '失败'}
                        </span>
                        <span className="muted">
                          {item.connectionName ?? '—'}
                          {item.rowCount !== null && item.rowCount !== undefined ? ` · ${item.rowCount} 行` : ''}
                          {item.elapsedMillis != null ? ` · ${item.elapsedMillis} ms` : ''}
                        </span>
                      </span>
                      <span className="history__time">
                        {historyScope === 'all' && item.username ? `${item.username} · ` : ''}
                        {formatTime(item.createdAt)}
                      </span>
                    </button>
                    <button
                      type="button"
                      className="link-button link-button--danger"
                      onClick={() => void handleDeleteHistory(item)}
                    >
                      删除
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </aside>
        </div>
      </section>

      {savingMetric ? (
        <Modal title="存为指标" onClose={() => setSavingMetric(false)}>
          <form className="form" onSubmit={submitMetric}>
            <p className="muted">
              指标是共享资产：所有同事都能在「指标」页看到并执行，只有你或管理员能修改、删除。
            </p>
            <div className="field">
              <label className="field__label">指标名称</label>
              <input
                className="field__input"
                value={metricName}
                onChange={(event) => setMetricName(event.target.value)}
                placeholder="例如：各渠道支付成功率"
                required
              />
            </div>
            <div className="field">
              <label className="field__label">口径说明（可选）</label>
              <input
                className="field__input"
                value={metricDescription}
                onChange={(event) => setMetricDescription(event.target.value)}
                placeholder="一句话说明这个指标怎么算"
              />
            </div>
            <div className="field">
              <label className="field__label">数据源</label>
              <input className="field__input" value={selected?.name ?? ''} readOnly />
            </div>
            {metricError ? <p className="form__fail">{metricError}</p> : null}
            <div className="form__actions">
              <span />
              <div className="form__actions-right">
                <button type="button" className="button button--ghost" onClick={() => setSavingMetric(false)}>
                  取消
                </button>
                <button type="submit" className="button" disabled={metricBusy || !metricName.trim()}>
                  {metricBusy ? '保存中…' : '保存'}
                </button>
              </div>
            </div>
          </form>
        </Modal>
      ) : null}
    </Layout>
  )
}
