import { describe, expect, it } from 'vitest'
import type { FileInfo } from './filesApi.ts'
import { isJpegFile } from './jpeg.ts'

const file = (contentType: string): FileInfo => ({
  id: '00000000-0000-0000-0000-000000000001',
  name: 'ada-example-portrait.jpg',
  kind: 'OTHER',
  contentType,
  sizeBytes: 1024,
  uploadedAt: '2026-10-01T10:00:00Z',
  master: false,
})

describe('isJpegFile', () => {
  it('accepts a file recorded as a JPEG', () => {
    expect(isJpegFile(file('image/jpeg'))).toBe(true)
  })

  it.each([
    'image/png',
    'application/pdf',
    'text/plain',
    'application/octet-stream',
  ])('rejects a %s file', (contentType) => {
    expect(isJpegFile(file(contentType))).toBe(false)
  })
})
