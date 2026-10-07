/** 后端统一返回体 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  role: string
}

export interface LoginResponse {
  /** 访问令牌（JWT），用于调接口 */
  token: string
  tokenType: string
  /** 访问令牌有效期（秒） */
  expiresIn: number
  /** 刷新令牌，仅用于换取新的访问令牌 */
  refreshToken: string
  /** 刷新令牌有效期（秒） */
  refreshExpiresIn: number
  user: UserInfo
}

export interface DbTypeInfo {
  code: string
  label: string
  defaultPort: number
}

export type ConnectionStatus = 'UNKNOWN' | 'OK' | 'FAILED'

export interface Connection {
  id: number
  name: string
  dbType: string
  dbTypeLabel: string
  host: string
  port: number
  databaseName?: string | null
  username: string
  passwordMasked: string
  params?: string | null
  remark?: string | null
  status: ConnectionStatus
  lastTestAt?: string | null
  lastTestMessage?: string | null
  createdBy?: number | null
  createdByName?: string | null
  createdAt: string
  updatedAt: string
  hasPassword: boolean
}

export interface ConnectionPayload {
  name: string
  dbType: string
  host: string
  port: number
  databaseName?: string
  username: string
  password?: string
  params?: string
  remark?: string
}

export interface ConnectionTestResult {
  success: boolean
  message: string
  costMillis: number
  serverInfo?: string | null
}
