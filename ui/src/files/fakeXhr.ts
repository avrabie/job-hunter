import { vi } from 'vitest'

/** A stand-in for `XMLHttpRequest` for tests: the test decides when progress, answers or failures happen. */
export class FakeXhr {
  static instances: FakeXhr[] = []

  method = ''
  url = ''
  headers: Record<string, string> = {}
  body: unknown = undefined
  aborted = false
  status = 0
  responseText = ''
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  upload: { onprogress: ((e: ProgressEvent) => void) | null } = {
    onprogress: null,
  }

  constructor() {
    FakeXhr.instances.push(this)
  }

  open(method: string, url: string) {
    this.method = method
    this.url = url
  }

  setRequestHeader(name: string, value: string) {
    this.headers[name] = value
  }

  send(body: unknown) {
    this.body = body
  }

  abort() {
    this.aborted = true
    this.onabort?.()
  }

  progress(loaded: number, total: number) {
    this.upload.onprogress?.({
      lengthComputable: true,
      loaded,
      total,
    } as ProgressEvent)
  }

  answer(status: number, text = '') {
    this.status = status
    this.responseText = text
    this.onload?.()
  }

  fail() {
    this.onerror?.()
  }
}

/** Replaces `XMLHttpRequest` with `FakeXhr` for the test and clears the recorded requests. */
export function stubXhr() {
  FakeXhr.instances = []
  vi.stubGlobal('XMLHttpRequest', FakeXhr)
  return FakeXhr
}
