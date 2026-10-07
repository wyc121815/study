import { useState, type FormEvent } from 'react'

import { changePassword } from '../api/auth'
import Modal from './Modal'

interface Props {
  onClose: () => void
  /** 修改成功：服务端已吊销其它会话，前端应退回登录页重新登录 */
  onChanged: () => void
}

export default function ChangePasswordModal({ onClose, onChanged }: Props) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (next !== confirm) {
      setError('两次输入的新密码不一致')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await changePassword(current, next)
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="修改密码" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        <label className="field field--full">
          <span className="field__label">当前密码 *</span>
          <input
            className="field__input"
            type="password"
            value={current}
            required
            autoFocus
            autoComplete="current-password"
            onChange={(event) => setCurrent(event.target.value)}
          />
        </label>

        <label className="field field--full">
          <span className="field__label">新密码 *（至少 8 位，含字母和数字）</span>
          <input
            className="field__input"
            type="password"
            value={next}
            required
            minLength={8}
            autoComplete="new-password"
            onChange={(event) => setNext(event.target.value)}
          />
        </label>

        <label className="field field--full">
          <span className="field__label">确认新密码 *</span>
          <input
            className="field__input"
            type="password"
            value={confirm}
            required
            autoComplete="new-password"
            onChange={(event) => setConfirm(event.target.value)}
          />
        </label>

        {error ? <p className="form__fail">{error}</p> : null}

        <div className="form__actions">
          <span className="muted">修改成功后需要重新登录</span>
          <div className="form__actions-right">
            <button type="button" className="button button--ghost" onClick={onClose}>
              取消
            </button>
            <button type="submit" className="button" disabled={submitting}>
              {submitting ? '提交中…' : '确认修改'}
            </button>
          </div>
        </div>
      </form>
    </Modal>
  )
}
