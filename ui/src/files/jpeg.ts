import type { FileInfo } from './filesApi.ts'

/** Whether the cabinet recorded the file as a JPEG. The server checks the bytes again when the file is used. */
export function isJpegFile(file: FileInfo): boolean {
  return file.contentType === 'image/jpeg'
}
