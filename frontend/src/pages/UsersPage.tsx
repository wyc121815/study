import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'

import { createUser, fetchUsers, resetUserPassword, updateUser } from '../api/user'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN, ROLE_USER, isAdminRole, normalizeRole, roleLabel } from '../auth/roles'
import Layout from '../components/Layout'
import Modal from '../components/Modal'
import type { ManagedUser } from '../types'

const PAGE_SIZE = 10

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

interface CreateState {
  username: string
  password: string
  nickname: string
  role: string
}

interface EditState {
  user: ManagedUser
  nickname: string
  role: string
  status: number
}

interface ResetState {
  user: ManagedUser
  newPassword: string
}

export default function UsersPage() {
  const { user } = useAuth()

  const [items, setItems] = useState<ManagedUser[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const [creating, setCreating] = useState<CreateState | null>(null)
  const [editing, setEditing] = useState<EditState | null>(null)
  const [resetting, setResetting] = useState<ResetState | null>(null)
  const [modalError, setModalError] = useState('')
  const [modalBusy, setModalBusy] = useState(false)

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await fetchUsers({ keyword, page, size: PAGE_SIZE })
      setItems(result.list)
      setTotal(result.total)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [keyword, page])

  useEffect(() => {
    void load()
  }, [load])

  if (user && !isAdminRole(user.role)) {
    return <Navigate to="/" replace />
  }

  const handleSearch = (event: FormEvent) => {
    event.preventDefault()
    setPage(1)
    setKeyword(keywordInput.trim())
  }

  const openCreate = () => {
    setModalError('')
    setCreating({ username: '', password: '', nickname: '', role: ROLE_USER })
  }

  const openEdit = (item: ManagedUser) => {
    setModalError('')
    setEditing({
      user: item,
      nickname: item.nickname,
      role: normalizeRole(item.role),
      status: item.status,
    })
  }

  const openReset = (item: ManagedUser) => {
    setModalError('')
    setResetting({ user: item, newPassword: '' })
  }

  const submitCreate = async (event: FormEvent) => {
    event.preventDefault()
    if (!creating) return
    setModalBusy(true)
    setModalError('')
    try {
      await createUser({
        username: creating.username.trim(),
        password: creating.password,
        nickname: creating.nickname.trim(),
        role: creating.role,
      })
      setCreating(null)
      setNotice(`已创建用户「${creating.username.trim()}」`)
      setPage(1)
      await load()
    } catch (err) {
      setModalError(errorMessage(err))
    } finally {
      setModalBusy(false)
    }
  }

  const submitEdit = async (event: FormEvent) => {
    event.preventDefault()
    if (!editing) return
    setModalBusy(true)
    setModalError('')
    try {
      await updateUser(editing.user.id, {
        nickname: editing.nickname.trim(),
        role: editing.role,
        status: editing.status,
      })
      setEditing(null)
      setNotice('用户信息已更新')
      await load()
    } catch (err) {
      setModalError(errorMessage(err))
    } finally {
      setModalBusy(false)
    }
  }

  const submitReset = async (event: FormEvent) => {
    event.preventDefault()
    if (!resetting) return
    setModalBusy(true)
    setModalError('')
    try {
      await resetUserPassword(resetting.user.id, resetting.newPassword)
      setResetting(null)
      setNotice(`已重置「${resetting.user.username}」的密码，其所有会话已失效`)
    } catch (err) {
      setModalError(errorMessage(err))
    } finally {
      setModalBusy(false)
    }
  }

  const toggleStatus = async (item: ManagedUser) => {
    const next = item.status === 1 ? 0 : 1
    if (next === 0 && !window.confirm(`确认禁用「${item.nickname}」？该用户将立即无法登录。`)) return
    setBusyId(item.id)
    setError('')
    try {
      await updateUser(item.id, { nickname: item.nickname, role: normalizeRole(item.role), status: next })
      setNotice(next === 1 ? `已启用「${item.nickname}」` : `已禁用「${item.nickname}」`)
      await load()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <Layout>
      <section className="panel">
        <div className="panel__head">
          <div>
            <h1>用户管理</h1>
            <p className="muted">共 {total} 个用户，修改角色或禁用后该用户的登录态会立即失效</p>
          </div>
          <button type="button" className="button" onClick={openCreate}>
            新建用户
          </button>
        </div>

        <form className="toolbar" onSubmit={handleSearch}>
          <input
            className="field__input toolbar__input"
            placeholder="按登录名或昵称搜索"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
          <button type="submit" className="button">
            查询
          </button>
          <button
            type="button"
            className="button button--ghost"
            onClick={() => {
              setKeywordInput('')
              setKeyword('')
              setPage(1)
            }}
          >
            重置
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
                <th>登录名</th>
                <th>昵称</th>
                <th>角色</th>
                <th>状态</th>
                <th>创建时间</th>
                <th className="table__actions">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="table__empty">
                    加载中…
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={6} className="table__empty">
                    没有匹配的用户
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.id}>
                    <td className="mono">
                      {item.username}
                      {item.id === user?.id ? <span className="cell__sub">（我）</span> : null}
                    </td>
                    <td>{item.nickname}</td>
                    <td>
                      <span className={`badge ${isAdminRole(item.role) ? 'badge--ok' : 'badge--unknown'}`}>
                        {roleLabel(item.role)}
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${item.status === 1 ? 'badge--ok' : 'badge--failed'}`}>
                        {item.status === 1 ? '启用' : '禁用'}
                      </span>
                      {item.lockedUntil ? <div className="cell__sub">锁定至 {formatTime(item.lockedUntil)}</div> : null}
                    </td>
                    <td>{formatTime(item.createdAt)}</td>
                    <td className="table__actions">
                      <button type="button" className="link-button" onClick={() => openEdit(item)}>
                        编辑
                      </button>
                      <button type="button" className="link-button" onClick={() => openReset(item)}>
                        重置密码
                      </button>
                      <button
                        type="button"
                        className={`link-button ${item.status === 1 ? 'link-button--danger' : ''}`}
                        disabled={busyId === item.id || item.id === user?.id}
                        onClick={() => void toggleStatus(item)}
                      >
                        {item.status === 1 ? '禁用' : '启用'}
                      </button>
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

      {creating ? (
        <Modal title="新建用户" onClose={() => setCreating(null)}>
          <form className="form" onSubmit={submitCreate}>
            <div className="form__grid">
              <div className="field">
                <label className="field__label">登录名</label>
                <input
                  className="field__input"
                  value={creating.username}
                  onChange={(event) => setCreating({ ...creating, username: event.target.value })}
                  required
                />
              </div>
              <div className="field">
                <label className="field__label">昵称</label>
                <input
                  className="field__input"
                  value={creating.nickname}
                  onChange={(event) => setCreating({ ...creating, nickname: event.target.value })}
                  required
                />
              </div>
              <div className="field">
                <label className="field__label">初始密码（≥8 位，含字母和数字）</label>
                <input
                  className="field__input"
                  type="password"
                  value={creating.password}
                  onChange={(event) => setCreating({ ...creating, password: event.target.value })}
                  required
                />
              </div>
              <div className="field">
                <label className="field__label">角色</label>
                <select
                  className="field__input"
                  value={creating.role}
                  onChange={(event) => setCreating({ ...creating, role: event.target.value })}
                >
                  <option value={ROLE_USER}>USER（只读）</option>
                  <option value={ROLE_ADMIN}>ADMIN（管理）</option>
                </select>
              </div>
            </div>
            {modalError ? <p className="form__fail">{modalError}</p> : null}
            <div className="form__actions">
              <span />
              <div className="form__actions-right">
                <button type="button" className="button button--ghost" onClick={() => setCreating(null)}>
                  取消
                </button>
                <button type="submit" className="button" disabled={modalBusy}>
                  {modalBusy ? '创建中…' : '创建'}
                </button>
              </div>
            </div>
          </form>
        </Modal>
      ) : null}

      {editing ? (
        <Modal title={`编辑用户 · ${editing.user.username}`} onClose={() => setEditing(null)}>
          <form className="form" onSubmit={submitEdit}>
            <div className="field">
              <label className="field__label">昵称</label>
              <input
                className="field__input"
                value={editing.nickname}
                onChange={(event) => setEditing({ ...editing, nickname: event.target.value })}
                required
              />
            </div>
            <div className="form__grid">
              <div className="field">
                <label className="field__label">角色</label>
                <select
                  className="field__input"
                  value={editing.role}
                  disabled={editing.user.id === user?.id}
                  onChange={(event) => setEditing({ ...editing, role: event.target.value })}
                >
                  <option value={ROLE_USER}>USER（只读）</option>
                  <option value={ROLE_ADMIN}>ADMIN（管理）</option>
                </select>
              </div>
              <div className="field">
                <label className="field__label">状态</label>
                <select
                  className="field__input"
                  value={editing.status}
                  disabled={editing.user.id === user?.id}
                  onChange={(event) => setEditing({ ...editing, status: Number(event.target.value) })}
                >
                  <option value={1}>启用</option>
                  <option value={0}>禁用</option>
                </select>
              </div>
            </div>
            {editing.user.id === user?.id ? (
              <p className="muted">不能修改自己的角色或禁用自己的账号。</p>
            ) : null}
            {modalError ? <p className="form__fail">{modalError}</p> : null}
            <div className="form__actions">
              <span />
              <div className="form__actions-right">
                <button type="button" className="button button--ghost" onClick={() => setEditing(null)}>
                  取消
                </button>
                <button type="submit" className="button" disabled={modalBusy}>
                  {modalBusy ? '保存中…' : '保存'}
                </button>
              </div>
            </div>
          </form>
        </Modal>
      ) : null}

      {resetting ? (
        <Modal title={`重置密码 · ${resetting.user.username}`} onClose={() => setResetting(null)}>
          <form className="form" onSubmit={submitReset}>
            <p className="muted">重置后该用户的全部会话会立即失效，需要用新密码重新登录。</p>
            <div className="field">
              <label className="field__label">新密码（≥8 位，含字母和数字）</label>
              <input
                className="field__input"
                type="password"
                value={resetting.newPassword}
                onChange={(event) => setResetting({ ...resetting, newPassword: event.target.value })}
                required
              />
            </div>
            {modalError ? <p className="form__fail">{modalError}</p> : null}
            <div className="form__actions">
              <span />
              <div className="form__actions-right">
                <button type="button" className="button button--ghost" onClick={() => setResetting(null)}>
                  取消
                </button>
                <button type="submit" className="button" disabled={modalBusy || !resetting.newPassword}>
                  {modalBusy ? '提交中…' : '重置'}
                </button>
              </div>
            </div>
          </form>
        </Modal>
      ) : null}
    </Layout>
  )
}
