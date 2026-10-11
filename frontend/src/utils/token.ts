/** token 的本地存取。单独成模块是为了让 http 层不依赖 Pinia store，避免循环引用 */
const TOKEN_KEY = 'jotang.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
