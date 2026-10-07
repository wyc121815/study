/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 网关地址前缀；留空则走 vite 代理（见 vite.config.ts） */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
