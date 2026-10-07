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
  queryEnabled: boolean
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
  queryEnabled?: boolean
}

export interface ConnectionTestResult {
  success: boolean
  message: string
  costMillis: number
  serverInfo?: string | null
}

/** 结果集列信息 */
export interface SqlResultColumn {
  name: string
  label: string
  typeName: string
}

/** SQL 执行结果，行数据按列顺序排列 */
export interface SqlQueryResult {
  columns: SqlResultColumn[]
  rows: (string | number | boolean | null)[][]
  rowCount: number
  truncated: boolean
  elapsedMillis: number
  statementType: string
  /** 是否命中 Redis 缓存 */
  cached: boolean
}

export interface SqlQueryPayload {
  connectionId: number
  sql: string
  maxRows?: number
}

/** 查询用数据源：只暴露开放了查询的连接 */
export interface DataSource {
  id: number
  name: string
  dbType: string
  dbTypeLabel: string
  host: string
  port: number
  databaseName?: string | null
  username: string
  remark?: string | null
}

export interface QueryHistoryItem {
  id: number
  userId?: number | null
  username?: string | null
  connectionId?: number | null
  connectionName?: string | null
  sql: string
  source: string
  statementType?: string | null
  rowCount?: number | null
  elapsedMillis?: number | null
  success: boolean
  message?: string | null
  createdAt: string
}

export interface Metric {
  id: number
  name: string
  description?: string | null
  datasourceId: number
  datasourceName?: string | null
  datasourceLabel?: string | null
  sql: string
  status: string
  createdBy?: number | null
  createdByName?: string | null
  createdAt: string
  updatedAt: string
}

export interface MetricPayload {
  name: string
  description?: string
  datasourceId: number
  sql: string
}

/** 指标热度排行条目，次数来自 Redis */
export interface MetricRankItem {
  metricId: number
  name: string
  description?: string | null
  datasourceName?: string | null
  runs: number
}

export interface ManagedUser {
  id: number
  username: string
  nickname: string
  role: string
  status: number
  lockedUntil?: string | null
  createdAt: string
  updatedAt: string
}

export interface UserCreatePayload {
  username: string
  password: string
  nickname: string
  role: string
}

export interface UserUpdatePayload {
  nickname: string
  role: string
  status: number
}
