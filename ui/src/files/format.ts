const KB = 1024
const MB = KB * 1024
const GB = MB * 1024

/** A readable size: KB below 1 MB, MB below 1 GB, GB above. */
export function formatSize(bytes: number): string {
  if (bytes < MB) return `${Math.max(1, Math.round(bytes / KB))} KB`
  if (bytes < GB) return `${(bytes / MB).toFixed(1)} MB`
  return `${(bytes / GB).toFixed(2)} GB`
}
