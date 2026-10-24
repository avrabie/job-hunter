import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { FileInfo } from './filesApi.ts'
import { JpegPhotoPicker } from './JpegPhotoPicker.tsx'

const file = (id: string, name: string, contentType: string): FileInfo => ({
  id,
  name,
  kind: 'OTHER',
  contentType,
  sizeBytes: 1024,
  uploadedAt: '2026-10-01T10:00:00Z',
  master: false,
})

const PORTRAIT = file(
  '00000000-0000-0000-0000-00000000000a',
  'ada-example-portrait.jpg',
  'image/jpeg',
)
const PASSPORT = file(
  '00000000-0000-0000-0000-00000000000b',
  'ada-example-passport.jpg',
  'image/jpeg',
)
const CV = file(
  '00000000-0000-0000-0000-00000000000c',
  'ada-example-cv-en.pdf',
  'application/pdf',
)

function mount(
  props: { files: FileInfo[]; selected?: string | null },
  onChange = vi.fn(),
) {
  return render(
    <MemoryRouter>
      <JpegPhotoPicker
        files={props.files}
        selected={props.selected ?? null}
        onChange={onChange}
      />
    </MemoryRouter>,
  )
}

describe('JpegPhotoPicker', () => {
  it('lists only the JPEG files by name, with No photo first and selected by default', () => {
    mount({ files: [CV, PORTRAIT, PASSPORT] })

    const select = screen.getByLabelText('Photo')
    expect(select).toHaveValue('')
    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual([
      'No photo',
      'ada-example-portrait.jpg',
      'ada-example-passport.jpg',
    ])
  })

  it('shows the chosen file as selected', () => {
    mount({ files: [PORTRAIT], selected: PORTRAIT.id })
    expect(screen.getByLabelText('Photo')).toHaveValue(PORTRAIT.id)
  })

  it('reports the chosen file id, and null for No photo', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    mount({ files: [PORTRAIT], selected: PORTRAIT.id }, onChange)

    await user.selectOptions(screen.getByLabelText('Photo'), '')
    expect(onChange).toHaveBeenLastCalledWith(null)

    await user.selectOptions(screen.getByLabelText('Photo'), PORTRAIT.id)
    expect(onChange).toHaveBeenLastCalledWith(PORTRAIT.id)
  })

  it('says so when there are no JPEG files and points to the profile page', () => {
    mount({ files: [CV] })

    expect(screen.queryByLabelText('Photo')).not.toBeInTheDocument()
    expect(
      screen.getByText(/There are no JPEG files in your file cabinet/),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'profile page' })).toHaveAttribute(
      'href',
      '/profile',
    )
  })
})
