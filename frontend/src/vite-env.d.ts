/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口地址前缀;留空则走 vite 代理 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

