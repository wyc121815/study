import { clearSession, getExpiresAt, getRefreshToken, getToken, updateTokens } from '../auth/session'
import type { ApiResult, LoginResponse } from '../types'

// 留空表示走同源请求，由 vite 的 proxy 转发到网关（见 vite.config.ts）。
// 前后端分离部署时在 .env.local 里设置 VITE_API_BASE_URL=http://网关地址
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  readonly code: number
  readonly status: number
  readonly traceId?: string

  constructor(message: string, code: number, status: number, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/** 由 AuthProvider 注册：令牌失效时清理登录态并跳回登录页。 */
export function onUnauthorized(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

/** 访问令牌剩余不足这个时间就先续期，避免请求正好卡在过期点上。 */
const REFRESH_THRESHOLD_MS = 60_000

/** 同一时刻只允许一个续期请求，避免并发请求各刷一次。 */
let refreshInFlight: Promise<boolean> | null = null

async function performRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  try {
    const response = await fetch(`${BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!response.ok) return false
    const payload = (await response.json()) as ApiResult<LoginResponse>
    if (!payload || payload.code !== 0 || !payload.data) return false
    updateTokens(payload.data.token, payload.data.refreshToken, payload.data.expiresIn)
    return true
  } catch {
    return false
  }
}

function refreshOnce(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

/**
 * 需要时刷新访问令牌。返回 false 表示登录态确实失效，调用方应清理并回登录页。
 * 没启用刷新令牌的场景返回 true，交由 401 分支处理。
 */
export async function ensureFreshToken(): Promise<boolean> {
  if (!getRefreshToken()) return true
  const expiresAt = getExpiresAt()
  if (expiresAt === null || expiresAt - Date.now() > REFRESH_THRESHOLD_MS) {
    return true
  }
  return refreshOnce()
}

async function doFetch(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')

  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }

  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  try {
    return await fetch(`${BASE_URL}${path}`, { ...init, headers })
  } catch {
    throw new ApiError('无法连接服务器，请确认网关已启动', -1, 0)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  await ensureFreshToken()

  let response = await doFetch(path, init)

  // 访问令牌过期：用刷新令牌换一次新的，成功后原请求重放一次
  if (response.status === 401 && getRefreshToken()) {
    const refreshed = await refreshOnce()
    if (refreshed) {
      response = await doFetch(path, init)
    }
  }

  let payload: ApiResult<T> | null = null
  try {
    payload = (await response.json()) as ApiResult<T>
  } catch {
    payload = null
  }

  if (response.status === 401) {
    clearSession()
    unauthorizedHandler?.()
  }

  if (!response.ok || !payload || payload.code !== 0) {
    throw new ApiError(
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
      payload?.code ?? -1,
      response.status,
      payload?.traceId,
    )
  }

  return payload.data
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

/**
 * 下载二进制响应（如 CSV 导出）。
 *
 * <p>不能走 {@link request}，因为它会无条件把响应体当 JSON 解析。</p>
 */
export async function download(
  path: string,
  body?: unknown,
): Promise<{ blob: Blob; filename: string }> {
  await ensureFreshToken()
  const init: RequestInit = {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
  }

  let response = await doFetch(path, init)
  if (response.status === 401 && getRefreshToken()) {
    const refreshed = await refreshOnce()
    if (refreshed) {
      response = await doFetch(path, init)
    }
  }

  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`
    let code = -1
    try {
      const payload = (await response.json()) as ApiResult<unknown>
      if (payload?.message) message = payload.message
      if (typeof payload?.code === 'number') code = payload.code
    } catch {
      // 非 JSON 响应，保留默认提示
    }
    if (response.status === 401) {
      clearSession()
      unauthorizedHandler?.()
    }
    throw new ApiError(message, code, response.status)
  }

  const blob = await response.blob()
  return { blob, filename: parseFilename(response.headers.get('Content-Disposition')) }
}

function parseFilename(header: string | null): string {
  if (!header) return 'export.csv'
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header)
  return match ? decodeURIComponent(match[1]) : 'export.csv'
}
