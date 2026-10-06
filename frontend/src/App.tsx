import { useCallback, useEffect, useState } from 'react'

import { fetchHello, type HelloResponse } from './api/hello'
import './App.css'

type Status = 'loading' | 'success' | 'error'

const STATUS_TEXT: Record<Status, string> = {
  loading: '连接中…',
  success: '已连通',
  error: '未连通',
}

export default function App() {
  const [status, setStatus] = useState<Status>('loading')
  const [data, setData] = useState<HelloResponse | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setStatus('loading')
    setError('')
    try {
      setData(await fetchHello())
      setStatus('success')
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <main className="app">
      <header className="hero">
        <p className="eyebrow">Spring Boot + React</p>
        <h1>前后端分离脚手架</h1>
        <p className="subtitle">
          后端 Spring Boot 3 / Java 21,前端 React 18 + Vite + TypeScript。本页数据来自后端{' '}
          <code>GET /api/hello</code> 接口。
        </p>
      </header>

      <section className={`card card--${status}`}>
        <div className="card__head">
          <span className="card__title">后端连通性</span>
          <span className="pill">{STATUS_TEXT[status]}</span>
        </div>

        {status === 'loading' && <p className="muted">正在请求后端…</p>}

        {status === 'success' && data && (
          <dl className="kv">
            <div>
              <dt>message</dt>
              <dd>{data.message}</dd>
            </div>
            <div>
              <dt>service</dt>
              <dd>{data.service}</dd>
            </div>
            <div>
              <dt>time</dt>
              <dd>{data.time}</dd>
            </div>
          </dl>
        )}

        {status === 'error' && (
          <div>
            <p className="muted">{error}</p>
            <p className="hint">
              请确认后端已启动:<code>cd backend &amp;&amp; mvnw spring-boot:run</code>
            </p>
          </div>
        )}

        <button type="button" className="button" onClick={() => void load()}>
          重新请求
        </button>
      </section>
    </main>
  )
}

