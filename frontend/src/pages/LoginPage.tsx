import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'

interface LocationState {
  from?: string
  notice?: string
}

export default function LoginPage() {
  const { user, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to="/" replace />
  }

  const notice = (location.state as LocationState | null)?.notice

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await signIn(username.trim(), password)
      const from = (location.state as LocationState | null)?.from
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <header className="login-card__head">
          <span className="brand__mark">DB</span>
          <h1>数据库连接管理平台</h1>
          <p className="muted">登录后可管理各环境数据库连接并做连通性巡检</p>
        </header>

        <form className="form" onSubmit={handleSubmit}>
          <label className="field field--full">
            <span className="field__label">用户名</span>
            <input
              className="field__input"
              value={username}
              required
              autoFocus
              autoComplete="username"
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>

          <label className="field field--full">
            <span className="field__label">密码</span>
            <input
              className="field__input"
              type="password"
              value={password}
              required
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          {error ? <p className="form__fail">{error}</p> : null}
          {!error && notice ? <p className="form__ok">{notice}</p> : null}

          <button type="submit" className="button button--block" disabled={submitting}>
            {submitting ? '登录中…' : '登录'}
          </button>
        </form>
      </div>
    </div>
  )
}
