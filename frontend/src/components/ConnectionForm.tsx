import { useState, type FormEvent } from 'react'

import type { Connection, ConnectionPayload, ConnectionTestResult, DbTypeInfo } from '../types'

interface Props {
  initial: Connection | null
  dbTypes: DbTypeInfo[]
  submitting: boolean
  error: string
  onCancel: () => void
  onSubmit: (payload: ConnectionPayload) => void
  onTest: (payload: ConnectionPayload) => Promise<ConnectionTestResult>
}

interface FormState {
  name: string
  dbType: string
  host: string
  port: string
  databaseName: string
  username: string
  password: string
  params: string
  remark: string
  queryEnabled: boolean
}

function buildInitialState(initial: Connection | null, dbTypes: DbTypeInfo[]): FormState {
  if (initial) {
    return {
      name: initial.name,
      dbType: initial.dbType,
      host: initial.host,
      port: String(initial.port),
      databaseName: initial.databaseName ?? '',
      username: initial.username,
      // 编辑时不回显密码，留空即保持原密码
      password: '',
      params: initial.params ?? '',
      remark: initial.remark ?? '',
      queryEnabled: initial.queryEnabled,
    }
  }
  const first = dbTypes[0]
  return {
    name: '',
    dbType: first?.code ?? 'MYSQL',
    host: '',
    port: String(first?.defaultPort ?? 3306),
    databaseName: '',
    username: '',
    password: '',
    params: '',
    remark: '',
    queryEnabled: true,
  }
}

export default function ConnectionForm({
  initial,
  dbTypes,
  submitting,
  error,
  onCancel,
  onSubmit,
  onTest,
}: Props) {
  const [form, setForm] = useState<FormState>(() => buildInitialState(initial, dbTypes))
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null)

  const isEdit = Boolean(initial)

  const update = (patch: Partial<FormState>) => {
    setForm((prev) => ({ ...prev, ...patch }))
    setTestResult(null)
  }

  const handleDbTypeChange = (dbType: string) => {
    const target = dbTypes.find((item) => item.code === dbType)
    update({ dbType, port: String(target?.defaultPort ?? form.port) })
  }

  const buildPayload = (): ConnectionPayload => ({
    name: form.name.trim(),
    dbType: form.dbType,
    host: form.host.trim(),
    port: Number(form.port),
    databaseName: form.databaseName.trim() || undefined,
    username: form.username.trim(),
    password: form.password || undefined,
    params: form.params.trim() || undefined,
    remark: form.remark.trim() || undefined,
    queryEnabled: form.queryEnabled,
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit(buildPayload())
  }

  const handleTest = async () => {
    setTesting(true)
    setTestResult(null)
    try {
      setTestResult(await onTest({ ...buildPayload(), ...(initial ? { id: initial.id } : {}) }))
    } catch (err) {
      setTestResult({
        success: false,
        message: err instanceof Error ? err.message : String(err),
        costMillis: 0,
        serverInfo: null,
      })
    } finally {
      setTesting(false)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="form__grid">
        <label className="field field--full">
          <span className="field__label">连接名称 *</span>
          <input
            className="field__input"
            value={form.name}
            required
            maxLength={128}
            placeholder="例如：订单库-生产"
            onChange={(event) => update({ name: event.target.value })}
          />
        </label>

        <label className="field">
          <span className="field__label">数据库类型 *</span>
          <select
            className="field__input"
            value={form.dbType}
            onChange={(event) => handleDbTypeChange(event.target.value)}
          >
            {dbTypes.map((item) => (
              <option key={item.code} value={item.code}>
                {item.label}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span className="field__label">端口 *</span>
          <input
            className="field__input"
            type="number"
            min={1}
            max={65535}
            value={form.port}
            required
            onChange={(event) => update({ port: event.target.value })}
          />
        </label>

        <label className="field field--full">
          <span className="field__label">主机地址 *</span>
          <input
            className="field__input"
            value={form.host}
            required
            placeholder="127.0.0.1 或 db.internal"
            onChange={(event) => update({ host: event.target.value })}
          />
        </label>

        <label className="field">
          <span className="field__label">库名</span>
          <input
            className="field__input"
            value={form.databaseName}
            placeholder="可留空"
            onChange={(event) => update({ databaseName: event.target.value })}
          />
        </label>

        <label className="field">
          <span className="field__label">数据库账号 *</span>
          <input
            className="field__input"
            value={form.username}
            required
            onChange={(event) => update({ username: event.target.value })}
          />
        </label>

        <label className="field field--full">
          <span className="field__label">密码 {isEdit ? '（留空表示不修改）' : '*'}</span>
          <input
            className="field__input"
            type="password"
            value={form.password}
            required={!isEdit}
            autoComplete="new-password"
            placeholder={isEdit ? '不填则沿用原密码' : ''}
            onChange={(event) => update({ password: event.target.value })}
          />
        </label>

        <label className="field field--full">
          <span className="field__label">附加参数</span>
          <input
            className="field__input"
            value={form.params}
            placeholder="例如 useSSL=false&serverTimezone=Asia/Shanghai"
            onChange={(event) => update({ params: event.target.value })}
          />
        </label>

        <label className="field field--full">
          <span className="field__label">备注</span>
          <textarea
            className="field__input"
            rows={2}
            value={form.remark}
            onChange={(event) => update({ remark: event.target.value })}
          />
        </label>

        <label className="field field--full field--check">
          <input
            type="checkbox"
            checked={form.queryEnabled}
            onChange={(event) => update({ queryEnabled: event.target.checked })}
          />
          <span>
            允许用于 SQL / 指标查询
            <em className="field__hint">
              关闭后该连接只能做管理与连通性测试，不会出现在查询台和指标的数据源里。
            </em>
          </span>
        </label>
      </div>

      {testResult ? (
        <p className={testResult.success ? 'form__ok' : 'form__fail'}>
          {testResult.success
            ? `连通成功（${testResult.costMillis} ms）${testResult.serverInfo ? ' · ' + testResult.serverInfo : ''}`
            : `连通失败：${testResult.message}`}
        </p>
      ) : null}

      {error ? <p className="form__fail">{error}</p> : null}

      <div className="form__actions">
        <button type="button" className="button button--ghost" disabled={testing} onClick={() => void handleTest()}>
          {testing ? '测试中…' : '测试连接'}
        </button>
        <div className="form__actions-right">
          <button type="button" className="button button--ghost" onClick={onCancel}>
            取消
          </button>
          <button type="submit" className="button" disabled={submitting}>
            {submitting ? '保存中…' : '保存'}
          </button>
        </div>
      </div>
    </form>
  )
}
