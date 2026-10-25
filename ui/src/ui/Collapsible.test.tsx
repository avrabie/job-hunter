import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import {
  Collapsible,
  CollapsibleGroup,
  ExpandCollapseAll,
} from './Collapsible.tsx'
import { revealAndFocus } from './revealAndFocus.ts'

const details = (name: string) =>
  screen.getByText(name).closest('details') as HTMLDetailsElement

describe('Collapsible', () => {
  it('starts closed with its title and summary in the summary element', () => {
    render(
      <Collapsible title="Skills" summary="12 skills">
        <p>inside</p>
      </Collapsible>,
    )
    const summary = screen.getByText('Skills').closest('summary')
    expect(summary).toHaveTextContent('Skills12 skills')
    expect(details('Skills').open).toBe(false)
    expect(screen.queryByText('inside')).not.toBeVisible()
  })

  // Browsers turn Enter and Space on a focused summary into a click; jsdom and user-event do not emulate that step,
  // so this checks the parts that are ours: the summary is a tab stop with no role or key handling of its own, and
  // its activation toggles the section and reports the state.
  it('is a keyboard tab stop whose activation toggles it', async () => {
    const onOpenChange = vi.fn()
    render(
      <Collapsible title="Skills" onOpenChange={onOpenChange}>
        <p>inside</p>
      </Collapsible>,
    )
    const summary = screen.getByText('Skills').closest('summary')!
    await userEvent.tab()
    expect(summary).toHaveFocus()
    expect(summary).not.toHaveAttribute('role')
    expect(summary).not.toHaveAttribute('tabindex')
    await userEvent.click(summary)
    await waitFor(() => expect(onOpenChange).toHaveBeenLastCalledWith(true))
    expect(details('Skills').open).toBe(true)
    expect(screen.getByText('inside')).toBeVisible()
    await userEvent.click(summary)
    await waitFor(() => expect(onOpenChange).toHaveBeenLastCalledWith(false))
    expect(details('Skills').open).toBe(false)
  })

  it('follows a controlled open prop', () => {
    const { rerender } = render(
      <Collapsible title="Skills" open={false}>
        x
      </Collapsible>,
    )
    expect(details('Skills').open).toBe(false)
    rerender(
      <Collapsible title="Skills" open>
        x
      </Collapsible>,
    )
    expect(details('Skills').open).toBe(true)
  })

  it('opens when forceOpen turns true, and can be closed again', async () => {
    const { rerender } = render(
      <Collapsible title="Contact">
        <input aria-label="Email" />
      </Collapsible>,
    )
    rerender(
      <Collapsible title="Contact" forceOpen>
        <input aria-label="Email" />
      </Collapsible>,
    )
    expect(details('Contact').open).toBe(true)
    await userEvent.click(screen.getByText('Contact'))
    await waitFor(() => expect(details('Contact').open).toBe(false))
  })
})

describe('error opens the section', () => {
  function Form() {
    const [error, setError] = useState<string>()
    return (
      <form
        onSubmit={(e) => {
          e.preventDefault()
          setError('Required')
          revealAndFocus(screen.getByLabelText('Phone'))
        }}
      >
        <Collapsible title="Outer">
          <Collapsible title="Contact">
            <label>
              Phone
              <input />
            </label>
            {error && <span role="alert">{error}</span>}
          </Collapsible>
        </Collapsible>
        <button type="submit">Save</button>
      </form>
    )
  }

  it('opens every closed section around the invalid field and focuses it', async () => {
    render(<Form />)
    expect(details('Contact').open).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(details('Outer').open).toBe(true)
    expect(details('Contact').open).toBe(true)
    expect(screen.getByLabelText('Phone')).toHaveFocus()
    expect(screen.getByRole('alert')).toHaveTextContent('Required')
  })

  it('keeps the section open after the toggle events have settled', async () => {
    render(<Form />)
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    await new Promise((r) => setTimeout(r, 20))
    expect(details('Contact').open).toBe(true)
  })
})

describe('CollapsibleGroup', () => {
  function Group() {
    return (
      <CollapsibleGroup>
        <ExpandCollapseAll />
        <Collapsible id="a" title="Alpha">
          a
        </Collapsible>
        <Collapsible id="b" title="Beta">
          b
        </Collapsible>
      </CollapsibleGroup>
    )
  }

  it('expands and collapses all sections', async () => {
    render(<Group />)
    expect(details('Alpha').open).toBe(false)
    expect(details('Beta').open).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Expand all' }))
    expect(details('Alpha').open).toBe(true)
    expect(details('Beta').open).toBe(true)
    await userEvent.click(screen.getByRole('button', { name: 'Collapse all' }))
    expect(details('Alpha').open).toBe(false)
    expect(details('Beta').open).toBe(false)
  })

  it('remembers a section opened by hand until the next expand or collapse all', async () => {
    render(<Group />)
    await userEvent.click(screen.getByText('Beta'))
    await waitFor(() => expect(details('Beta').open).toBe(true))
    expect(details('Alpha').open).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Collapse all' }))
    await waitFor(() => expect(details('Beta').open).toBe(false))
  })

  it('starts open when the group says so', () => {
    render(
      <CollapsibleGroup defaultOpen>
        <Collapsible id="a" title="Alpha">
          a
        </Collapsible>
      </CollapsibleGroup>,
    )
    expect(details('Alpha').open).toBe(true)
  })
})
