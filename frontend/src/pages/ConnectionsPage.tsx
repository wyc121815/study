import { useCallback, useEffect, useState, type FormEvent } from 'react'

import {
  createConnection,
  deleteConnection,
  fetchConnections,
  fetchDbTypes,
  testConnection,
  testSavedConnection,
  updateConnection,
} from '../api/connection'
import ConnectionForm from '../components/ConnectionForm'
import Layout from '../components/Layout'
import Modal from '../components/Modal'
import { useAuth } from '../auth/AuthContext'
import { isAdminRole } from '../auth/roles'
import type { Connection, ConnectionPayload, ConnectionTestResult, DbTypeInfo } from '../types'

const PAGE_SIZE = 10

const STATUS_TEXT: Record<string, string> = {
  OK: '连通',
  FAILED: '失败',
  UNKNOWN: '未测试',
}

interface EditorState {
  mode: 'create' | 'edit'
  record: Connection | null
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

export default function ConnectionsPage() {
  const { user } = useAuth()
  const canManage = isAdminRole(user?.role)

  const [items, setItems] = useState<Connection[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [dbType, setDbType] = useState('')

  const [dbTypes, setDbTypes] = useState<DbTypeInfo[]>([])
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [editorError, setEditorError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [busyId, setBusyId] = useState<number | null>(null)

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await fetchConnections({ keyword, dbType, page, size: PAGE_SIZE })
      setItems(result.list)
      setTotal(result.total)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [keyword, dbType, page])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    fetchDbTypes()
      .then(setDbTypes)
      .catch(() => setDbTypes([]))
  }, [])

  const handleSearch = (event: FormEvent) => {
    event.preventDefault()
    setPage(1)
    setKeyword(keywordInput.trim())
  }

  const handleReset = () => {
    setKeywordInput('')
    setKeyword('')
    setDbType('')
    setPage(1)
  }

  const handleTestSaved = async (record: Connection) => {
    setBusyId(record.id)
    setNotice('')
    setError('')
    try {
      const result = await testSavedConnection(record.id)
      setNotice(
        result.success
          ? `「${record.name}」连通成功（${result.costMillis} ms）${result.serverInfo ? ' · ' + result.serverInfo : ''}`
          : `「${record.name}」连通失败：${result.message}`,
      )
      await load()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const handleDelete = async (record: Connection) => {
    if (!window.confirm(`确认删除连接「${record.name}」？该操作不可恢复。`)) return
    setBusyId(record.id)
    setError('')
    setNotice('')
    try {
      await deleteConnection(record.id)
      setNotice(`已删除「${record.name}」`)
      if (items.length === 1 && page > 1) {
        setPage(page - 1)
      } else {
        await load()
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const handleSubmit = async (payload: ConnectionPayload) => {
    setSubmitting(true)
    setEditorError('')
    try {
      if (editor?.mode === 'edit' && editor.record) {
        await updateConnection(editor.record.id, payload)
        setNotice(`已更新「${payload.name}」`)
      } else {
        await createConnection(payload)
        setNotice(`已创建「${payload.name}」`)
        setPage(1)
      }
      setEditor(null)
      await load()
    } catch (err) {
      setEditorError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const handleTestDraft = async (payload: ConnectionPayload): Promise<ConnectionTestResult> =>
    testConnection(payload)

  const openCreate = () => {
    setEditorError('')
    setEditor({ mode: 'create', record: null })
  }

  const openEdit = (record: Connection) => {
    setEditorError('')
    setEditor({ mode: 'edit', record })
  }

  return (
    <Layout>
      <section className="panel">
        <div className="panel__head">
          <div>
            <h1>数据库连接</h1>
            <p className="muted">共 {total} 条连接，密码以 AES-GCM 加密存储</p>
          </div>
          {canManage ? (
            <button type="button" className="button" onClick={openCreate} disabled={dbTypes.length === 0}>
              新建连接
            </button>
          ) : (
            <span className="muted">当前为只读账号，如需增删改请联系管理员</span>
          )}
        </div>

        <form className="toolbar" onSubmit={handleSearch}>
          <input
            className="field__input toolbar__input"
            placeholder="按名称或主机搜索"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
          <select
            className="field__input toolbar__select"
            value={dbType}
            onChange={(event) => {
              setDbType(event.target.value)
              setPage(1)
            }}
          >
            <option value="">全部类型</option>
            {dbTypes.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </select>
          <button type="submit" className="button">
            查询
          </button>
          <button type="button" className="button button--ghost" onClick={handleReset}>
            重置
          </button>
          <button type="button" className="button button--ghost" onClick={() => void load()}>
            刷新
          </button>
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

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>名称</th>
                <th>类型</th>
                <th>地址</th>
                <th>账号</th>
                <th>密码</th>
                <th>查询</th>
                <th>状态</th>
                <th>最近测试</th>
                <th>创建人</th>
                <th className="table__actions">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={10} className="table__empty">
                    加载中…
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={10} className="table__empty">
                    {canManage ? '还没有连接，点右上角「新建连接」添加一条' : '还没有连接'}
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <div className="cell__main">{item.name}</div>
                      {item.remark ? <div className="cell__sub">{item.remark}</div> : null}
                    </td>
                    <td>{item.dbTypeLabel}</td>
                    <td className="mono">
                      {item.host}:{item.port}
                      {item.databaseName ? `/${item.databaseName}` : ''}
                    </td>
                    <td className="mono">{item.username}</td>
                  <td className="mono">{item.passwordMasked || '—'}</td>
                  <td>
                    <span className={`badge ${item.queryEnabled ? 'badge--ok' : 'badge--unknown'}`}>
                      {item.queryEnabled ? '允许' : '仅管理'}
                    </span>
                  </td>
                    <td>
                      <span className={`badge badge--${item.status.toLowerCase()}`}>
                        {STATUS_TEXT[item.status] ?? item.status}
                      </span>
                    </td>
                    <td>
                      <div className="cell__main">{formatTime(item.lastTestAt)}</div>
                      {item.lastTestMessage ? <div className="cell__sub">{item.lastTestMessage}</div> : null}
                    </td>
                    <td>{item.createdByName ?? (item.createdBy ? `#${item.createdBy}` : '—')}</td>
                    <td className="table__actions">
                      <button
                        type="button"
                        className="link-button"
                        disabled={busyId === item.id}
                        onClick={() => void handleTestSaved(item)}
                      >
                        测试
                      </button>
                      {canManage ? (
                        <>
                          <button type="button" className="link-button" onClick={() => openEdit(item)}>
                            编辑
                          </button>
                          <button
                            type="button"
                            className="link-button link-button--danger"
                            disabled={busyId === item.id}
                            onClick={() => void handleDelete(item)}
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

        <div className="pager">
          <span className="muted">
            第 {page} / {totalPages} 页
          </span>
          <div className="pager__buttons">
            <button
              type="button"
              className="button button--ghost"
              disabled={page <= 1 || loading}
              onClick={() => setPage(page - 1)}
            >
              上一页
            </button>
            <button
              type="button"
              className="button button--ghost"
              disabled={page >= totalPages || loading}
              onClick={() => setPage(page + 1)}
            >
              下一页
            </button>
          </div>
        </div>
      </section>

      {editor ? (
        <Modal
          title={editor.mode === 'create' ? '新建连接' : `编辑连接 · ${editor.record?.name ?? ''}`}
          onClose={() => setEditor(null)}
        >
          <ConnectionForm
            initial={editor.record}
            dbTypes={dbTypes}
            submitting={submitting}
            error={editorError}
            onCancel={() => setEditor(null)}
            onSubmit={(payload) => void handleSubmit(payload)}
            onTest={handleTestDraft}
          />
        </Modal>
      ) : null}
    </Layout>
  )
}
