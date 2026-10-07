import { http } from './client'
import type { LoginResponse, UserInfo } from '../types'

export function login(username: string, password: string): Promise<LoginResponse> {
  return http.post<LoginResponse>('/api/auth/login', { username, password })
}

export function fetchMe(): Promise<UserInfo> {
  return http.get<UserInfo>('/api/auth/me')
}

/**
 * 登出：服务端作废刷新令牌，并把访问令牌的 jti 拉黑，保证立即失效。
 * 访问令牌已过期时只传 refreshToken 即可。
 */
export function logout(accessToken: string | null, refreshToken: string | null): Promise<null> {
  return http.post<null>('/api/auth/logout', { accessToken, refreshToken })
}

/** 修改当前用户密码，成功后其它会话会被服务端吊销。 */
export function changePassword(currentPassword: string, newPassword: string): Promise<null> {
  return http.post<null>('/api/auth/password', { currentPassword, newPassword })
}
