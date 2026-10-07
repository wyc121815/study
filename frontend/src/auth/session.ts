import type { UserInfo } from '../types'

const TOKEN_KEY = 'conn-platform.token'
const REFRESH_KEY = 'conn-platform.refresh'
const EXPIRES_KEY = 'conn-platform.expiresAt'
const USER_KEY = 'conn-platform.user'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

/** 访问令牌到期时间（毫秒时间戳），未知返回 null。 */
export function getExpiresAt(): number | null {
  const raw = localStorage.getItem(EXPIRES_KEY)
  if (!raw) return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

export function getUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export function saveSession(
  token: string,
  refreshToken: string,
  expiresIn: number,
  user: UserInfo,
): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(REFRESH_KEY, refreshToken)
  localStorage.setItem(EXPIRES_KEY, String(Date.now() + expiresIn * 1000))
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 只更新令牌，用户信息不变（自动续期时用）。 */
export function updateTokens(token: string, refreshToken: string, expiresIn: number): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(REFRESH_KEY, refreshToken)
  localStorage.setItem(EXPIRES_KEY, String(Date.now() + expiresIn * 1000))
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(EXPIRES_KEY)
  localStorage.removeItem(USER_KEY)
}
