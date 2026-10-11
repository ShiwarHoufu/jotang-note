/** 后端时间是 ISO-8601 无时区串（如 2026-10-11T21:30:00），展示成 yyyy-MM-dd HH:mm */
export function formatTime(iso: string): string {
  return iso.replace('T', ' ').slice(0, 16)
}

/** 文件大小 humanize */
export function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
