/**
 * 角色判断的唯一入口。
 *
 * <p>角色字符串经过后端配置、JWT、HTTP 头好几道手，任何一处出现大小写差异都不该
 * 让权限判断失效，所以不要在页面里直接写 {@code role === 'ADMIN'}。</p>
 */

export const ROLE_ADMIN = 'ADMIN'
export const ROLE_USER = 'USER'

/** 归一化成大写去空白；认不出的角色原样返回，方便排查。 */
export function normalizeRole(role?: string | null): string {
  if (!role) return ''
  const normalized = role.trim().toUpperCase()
  return normalized === ROLE_ADMIN || normalized === ROLE_USER ? normalized : role
}

/** 是否是管理员，忽略大小写与首尾空白。 */
export function isAdminRole(role?: string | null): boolean {
  return normalizeRole(role) === ROLE_ADMIN
}

export function roleLabel(role?: string | null): string {
  const normalized = normalizeRole(role)
  if (normalized === ROLE_ADMIN) return 'ADMIN（管理）'
  if (normalized === ROLE_USER) return 'USER（只读）'
  return normalized || '—'
}
