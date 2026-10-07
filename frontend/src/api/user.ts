import { http } from './client'
import type { ManagedUser, PageResult, UserCreatePayload, UserUpdatePayload } from '../types'

export interface UserQuery {
  keyword?: string
  page?: number
  size?: number
}

export function fetchUsers(query: UserQuery = {}): Promise<PageResult<ManagedUser>> {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 10))
  return http.get<PageResult<ManagedUser>>(`/api/users?${params.toString()}`)
}

export function createUser(payload: UserCreatePayload): Promise<ManagedUser> {
  return http.post<ManagedUser>('/api/users', payload)
}

export function updateUser(id: number, payload: UserUpdatePayload): Promise<ManagedUser> {
  return http.put<ManagedUser>(`/api/users/${id}`, payload)
}

export function resetUserPassword(id: number, newPassword: string): Promise<null> {
  return http.post<null>(`/api/users/${id}/password`, { newPassword })
}
