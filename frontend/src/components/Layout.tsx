import { useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import { isAdminRole, normalizeRole } from '../auth/roles'
import ChangePasswordModal from './ChangePasswordModal'

interface Props {
  children: ReactNode
}

export default function Layout({ children }: Props) {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [changingPassword, setChangingPassword] = useState(false)

  const navItems = [
    { to: '/', label: '数据库连接', end: true },
    { to: '/sql', label: 'SQL 查询', end: false },
    { to: '/metrics', label: '指标', end: false },
    ...(isAdminRole(user?.role) ? [{ to: '/users', label: '用户管理', end: false }] : []),
  ]

  const handleSignOut = async () => {
    await signOut()
    navigate('/login', { replace: true })
  }

  const handlePasswordChanged = async () => {
    setChangingPassword(false)
    await signOut()
    navigate('/login', { replace: true, state: { notice: '密码已修改，请重新登录' } })
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar__left">
          <div className="brand">
            <span className="brand__mark">DB</span>
            <span className="brand__text">数据库连接管理平台</span>
          </div>
          <nav className="nav">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => (isActive ? 'nav__link nav__link--active' : 'nav__link')}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="topbar__right">
          <span className="user-chip">
            {user?.nickname ?? user?.username}
            <em>{normalizeRole(user?.role)}</em>
          </span>
          <button type="button" className="button button--ghost" onClick={() => setChangingPassword(true)}>
            修改密码
          </button>
          <button type="button" className="button button--ghost" onClick={() => void handleSignOut()}>
            退出登录
          </button>
        </div>
      </header>
      <main className="content">{children}</main>
      {changingPassword ? (
        <ChangePasswordModal
          onClose={() => setChangingPassword(false)}
          onChanged={() => void handlePasswordChanged()}
        />
      ) : null}
    </div>
  )
}
