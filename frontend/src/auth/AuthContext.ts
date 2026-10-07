import { createContext, useContext } from 'react'

import type { UserInfo } from '../types'

export interface AuthContextValue {
  user: UserInfo | null
  /** 正在用本地 token 向后端确认登录态 */
  initializing: boolean
  signIn: (username: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内部使用')
  }
  return context
}
