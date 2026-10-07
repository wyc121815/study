import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import * as authApi from '../api/auth'
import { ensureFreshToken, onUnauthorized } from '../api/client'
import type { UserInfo } from '../types'
import { AuthContext } from './AuthContext'
import {
  clearSession,
  getExpiresAt,
  getRefreshToken,
  getToken,
  getUser,
  saveSession,
} from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(() => (getToken() ? getUser() : null))
  const [initializing, setInitializing] = useState<boolean>(() => Boolean(getToken()))

  // 带 token 启动时，向后端确认一次，避免拿着过期 token 进去到处报错
  useEffect(() => {
    if (!getToken()) {
      setInitializing(false)
      return
    }
    let cancelled = false
    authApi
      .fetchMe()
      .then((me) => {
        if (!cancelled) setUser(me)
      })
      .catch(() => {
        if (!cancelled) {
          clearSession()
          setUser(null)
        }
      })
      .finally(() => {
        if (!cancelled) setInitializing(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 任何请求收到 401，都统一退回登录页
  useEffect(() => {
    onUnauthorized(() => setUser(null))
    return () => onUnauthorized(null)
  }, [])

  // 访问令牌快到期时提前续期，避免用户在操作过程中被踢回登录页
  useEffect(() => {
    if (!user) return
    let cancelled = false
    let timer: number | undefined

    const schedule = () => {
      const expiresAt = getExpiresAt()
      if (expiresAt === null) return
      const delay = Math.max(expiresAt - Date.now() - 60_000, 5_000)
      timer = window.setTimeout(async () => {
        if (cancelled) return
        const ok = await ensureFreshToken()
        if (cancelled) return
        if (!ok) {
          clearSession()
          setUser(null)
          return
        }
        schedule()
      }, delay)
    }

    schedule()
    return () => {
      cancelled = true
      if (timer !== undefined) window.clearTimeout(timer)
    }
  }, [user])

  const signIn = useCallback(async (username: string, password: string) => {
    const result = await authApi.login(username, password)
    saveSession(result.token, result.refreshToken, result.expiresIn, result.user)
    setUser(result.user)
  }, [])

  const signOut = useCallback(async () => {
    const accessToken = getToken()
    const refreshToken = getRefreshToken()
    clearSession()
    setUser(null)
    // 本地先登出；令牌显式带在请求体里，不依赖 localStorage 已被清空
    if (accessToken || refreshToken) {
      try {
        await authApi.logout(accessToken, refreshToken)
      } catch {
        // 忽略：令牌可能已经过期，服务端无需要处理
      }
    }
  }, [])

  const value = useMemo(
    () => ({ user, initializing, signIn, signOut }),
    [user, initializing, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
