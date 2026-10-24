import { apiFetch, ApiError, type TokenSource } from '../api/apiFetch.ts'
import { messageForError, messageForStatus } from '../api/messages.ts'
import { toRequest, type CvFormat, type ExportOptions } from './options.ts'

export type ExportErrorCode =
  | 'invalid_request'
  | 'name_required'
  | 'too_many_exports'
  | 'busy'
  | 'render_failed'
  | 'photo_invalid'

export type ExportResult =
  | { kind: 'exported'; blob: Blob; fileName: string }
  | { kind: 'unauthorized' }
  | { kind: 'failed'; code: ExportErrorCode | null; message: string }

const PATH = '/api/cv/export'

// Fixed texts for the error codes of the backend; the body is never shown.
const EXPORT_MESSAGES: Record<ExportErrorCode, string> = {
  invalid_request: 'The export options were not accepted. Please try again.',
  name_required:
    'Add your full name in the Contact section of your profile first.',
  too_many_exports:
    'You have reached the limit of exports for this hour. Please try again later.',
  busy: 'The server is busy creating PDFs right now. Please try again in a moment.',
  render_failed: 'The CV could not be created. Please try again later.',
  photo_invalid:
    'The chosen photo cannot be used. Choose another JPEG file or "No photo".',
}

// Used only when a failure has no readable code.
const STATUS_CODES: Record<number, ExportErrorCode> = {
  400: 'invalid_request',
  409: 'name_required',
  429: 'too_many_exports',
  500: 'render_failed',
  503: 'busy',
}

const EXTENSIONS: Record<CvFormat, string> = {
  MARKDOWN: 'md',
  HTML: 'html',
  PDF: 'pdf',
}

/**
 * Asks the backend for the CV with these options. The file comes back as a blob with the name the server gives it.
 * Nothing is stored by this call; the caller decides what to do with the blob. Pass `signal` to cancel a request
 * that is no longer wanted (the preview does this when the options change).
 */
export async function exportCv(
  auth: TokenSource,
  options: ExportOptions,
  signal?: AbortSignal,
): Promise<ExportResult> {
  try {
    const res = await apiFetch(auth, PATH, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(toRequest(options)),
      signal: signal ?? null,
    })
    if (!res.ok) {
      const code = await failureCode(res)
      return {
        kind: 'failed',
        code,
        message: code ? EXPORT_MESSAGES[code] : messageForStatus(res.status),
      }
    }
    return {
      kind: 'exported',
      blob: await res.blob(),
      fileName: fileNameOf(
        res.headers.get('Content-Disposition'),
        options.format,
      ),
    }
  } catch (e) {
    if (e instanceof ApiError && e.kind === 'unauthorized')
      return { kind: 'unauthorized' }
    return { kind: 'failed', code: null, message: messageForError(e) }
  }
}

async function failureCode(res: Response): Promise<ExportErrorCode | null> {
  try {
    const body = (await res.json()) as { error?: unknown }
    if (typeof body.error === 'string' && body.error in EXPORT_MESSAGES)
      return body.error as ExportErrorCode
  } catch {
    // No readable body: the status decides.
  }
  return STATUS_CODES[res.status] ?? null
}

/** The server's name when it sends a plain one; otherwise a name built from the format. */
function fileNameOf(header: string | null, format: CvFormat): string {
  const match = header?.match(/filename="([A-Za-z0-9._-]{1,120})"/)
  return match?.[1] ?? `cv.${EXTENSIONS[format]}`
}
