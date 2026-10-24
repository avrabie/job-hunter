import { Link } from 'react-router'
import type { FileInfo } from './filesApi.ts'
import { isJpegFile } from './jpeg.ts'

/**
 * Picks one JPEG file from the file cabinet, or none. Only names are shown. The caller passes the list, so the page
 * decides when to load it. Generic: it knows nothing about the export or the public CV.
 */
export function JpegPhotoPicker({
  id = 'photo-picker',
  files,
  selected,
  onChange,
}: {
  id?: string
  files: FileInfo[]
  selected: string | null
  onChange: (fileId: string | null) => void
}) {
  const jpegs = files.filter(isJpegFile)
  if (jpegs.length === 0)
    return (
      <p>
        There are no JPEG files in your file cabinet. Upload one on your{' '}
        <Link to="/profile">profile page</Link> to use it as a photo.
      </p>
    )
  return (
    <div className="export-field">
      <label htmlFor={id}>Photo</label>
      <select
        id={id}
        value={selected ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
      >
        <option value="">No photo</option>
        {jpegs.map((file) => (
          <option key={file.id} value={file.id}>
            {file.name}
          </option>
        ))}
      </select>
    </div>
  )
}
