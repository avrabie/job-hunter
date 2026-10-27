import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/authContext.ts'
import { fakeAuth, signedIn } from '../test/fakeAuth.ts'
import { RavProofCard } from './RavProofCard.tsx'

const ID = '0b6f2c1e-7d3a-4e8b-9f1a-2c3d4e5f6a7b'
const URL_PATH = `/api/applications/${ID}/rav`

// Only the proof fields the backend has: the position and the original link. The rest is null.
const proof = {
  companyAddress: null,
  position: 'Backend Engineer',
  contactPerson: null,
  contactPhone: null,
  contactEmail: null,
  postalCode: null,
  postingUrl: 'https://example.org/jobs/1',
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

function stubRav(response: () => Response) {
  const calls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      calls.push(url)
      return response()
    }),
  )
  return calls
}

function mount() {
  return render(
    <AuthContext.Provider value={fakeAuth(signedIn())}>
      <RavProofCard applicationId={ID} />
    </AuthContext.Provider>,
  )
}

beforeEach(() => {
  ;(['log', 'info', 'warn', 'error', 'debug'] as const).forEach((m) =>
    vi.spyOn(console, m).mockImplementation(() => {}),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('RAV proof card', () => {
  it('loads the fields when shown and shows a missing field as the text null', async () => {
    const calls = stubRav(() => json(200, proof))
    const user = userEvent.setup()
    mount()

    await user.click(screen.getByRole('button', { name: 'Show RAV fields' }))

    expect(await screen.findByText('Backend Engineer')).toBeVisible()
    expect(screen.getByText('https://example.org/jobs/1')).toBeVisible()
    const row = screen.getByText('Contact phone').closest('div')!
    expect(row).toHaveTextContent('null')
    expect(calls).toEqual([URL_PATH])
  })

  it('copies a value to the clipboard and says which field was copied', async () => {
    stubRav(() => json(200, proof))
    const user = userEvent.setup()
    const writeText = vi
      .spyOn(navigator.clipboard, 'writeText')
      .mockResolvedValue()
    mount()
    await user.click(screen.getByRole('button', { name: 'Show RAV fields' }))
    await screen.findByText('Backend Engineer')

    await user.click(screen.getByRole('button', { name: 'Copy Position' }))

    await waitFor(() =>
      expect(writeText).toHaveBeenCalledWith('Backend Engineer'),
    )
    expect(await screen.findByText('Copied Position.')).toBeVisible()
  })

  it('has no copy action for a field that is null', async () => {
    stubRav(() => json(200, proof))
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: 'Show RAV fields' }))
    await screen.findByText('Backend Engineer')

    expect(
      screen.getByRole('button', { name: 'Copy Contact email' }),
    ).toBeDisabled()
    expect(
      screen.getByRole('button', { name: 'Copy Postal code (Postleitzahl)' }),
    ).toBeDisabled()
    expect(
      screen.getByRole('button', { name: 'Copy Original posting URL' }),
    ).toBeEnabled()
  })

  it('shows a fixed message when the application is not applied and never the body', async () => {
    stubRav(() => json(409, { error: 'not_applied', secret: 'SECRET-BODY' }))
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: 'Show RAV fields' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Mark the application as applied to see these fields.',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
  })

  it('shows a fixed message for a server error', async () => {
    stubRav(() => json(500, { secret: 'SECRET-BODY' }))
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: 'Show RAV fields' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request failed (status 500).',
    )
    expect(screen.queryByText(/SECRET-BODY/)).not.toBeInTheDocument()
  })

  it('hides the fields again on the second click', async () => {
    stubRav(() => json(200, proof))
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: 'Show RAV fields' }))
    await screen.findByText('Backend Engineer')

    await user.click(screen.getByRole('button', { name: 'Hide RAV fields' }))

    expect(screen.queryByText('Backend Engineer')).not.toBeInTheDocument()
  })
})
