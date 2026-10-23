import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { TagInput } from './TagInput.tsx'

/** Holds the tags like the page does, and reports each change. */
function Harness({
  initial,
  suggestions = [],
  onChange,
}: {
  initial: string[]
  suggestions?: string[]
  onChange: (tags: string[]) => void
}) {
  const [tags, setTags] = useState(initial)
  return (
    <TagInput
      label="Technologies"
      value={tags}
      suggestions={suggestions}
      path="experience[0].technologies"
      onChange={(next) => {
        setTags(next)
        onChange(next)
      }}
    />
  )
}

function renderTags(initial: string[], suggestions: string[] = []) {
  const onChange = vi.fn()
  render(
    <Harness initial={initial} suggestions={suggestions} onChange={onChange} />,
  )
  return { onChange, input: screen.getByLabelText('Technologies') }
}

describe('Technology tags', () => {
  it('adds a tag on Enter and ignores a case-insensitive duplicate', async () => {
    const { onChange, input } = renderTags([])
    await userEvent.type(input, 'Kotlin{Enter}')
    await userEvent.type(input, 'kotlin{Enter}')
    expect(onChange).toHaveBeenLastCalledWith(['Kotlin'])
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(input).toHaveValue('')
  })

  it('adds a tag on a comma and does not type the comma', async () => {
    const { onChange, input } = renderTags([])
    await userEvent.type(input, ' Spring Boot ,')
    expect(onChange).toHaveBeenLastCalledWith(['Spring Boot'])
    expect(input).toHaveValue('')
  })

  it('removes a tag with its own remove control and keeps the others in order', async () => {
    const { onChange } = renderTags(['Kotlin', 'gRPC', 'Go'])
    await userEvent.click(screen.getByRole('button', { name: 'Remove gRPC' }))
    expect(onChange).toHaveBeenLastCalledWith(['Kotlin', 'Go'])
    expect(screen.queryByText('gRPC')).not.toBeInTheDocument()
  })

  it('removes the last tag on Backspace in the empty input', async () => {
    const { onChange, input } = renderTags(['Kotlin', 'Go'])
    await userEvent.click(input)
    await userEvent.keyboard('{Backspace}')
    expect(onChange).toHaveBeenLastCalledWith(['Kotlin'])
  })

  it('offers suggestions that are not already tags', () => {
    const { input } = renderTags(['Kotlin'], ['PostgreSQL', 'kotlin', 'Go'])
    const list = document.getElementById(input.getAttribute('list')!)!
    const offered = Array.from(list.querySelectorAll('option')).map(
      (o) => o.value,
    )
    expect(offered).toEqual(['PostgreSQL', 'Go'])
  })
})
