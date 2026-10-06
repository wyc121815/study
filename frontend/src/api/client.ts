// 极简 fetch 封装。
//
// 默认走相对路径,由 vite 的 proxy 转发到后端(见 vite.config.ts)。
// 如需直连后端,可在 .env.local 中设置 VITE_API_BASE_URL=http://localhost:8080

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    throw new Error(`请求失败: ${response.status} ${response.statusText}`)
  }

  return (await response.json()) as T
}

