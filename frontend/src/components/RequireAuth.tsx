import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'

export default function RequireAuth() {
  const { user, initializing } = useAuth()
  const location = useLocation()

  if (initializing) {
    return <div className="page-loading">正在校验登录状态…</div>
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
