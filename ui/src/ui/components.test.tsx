import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Alert } from './Alert.tsx'
import { Badge } from './Badge.tsx'
import { Card } from './Card.tsx'
import { EmptyState } from './EmptyState.tsx'
import { FormField } from './FormField.tsx'
import { PageHeader } from './PageHeader.tsx'

describe('PageHeader', () => {
  it('renders the one h1 with the lead and actions', () => {
    render(
      <PageHeader
        title="Matches"
        lead="Postings that fit your profile."
        actions={<button type="button">Refresh</button>}
      />,
    )
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(screen.getByRole('heading', { name: 'Matches' })).toBeInTheDocument()
    expect(
      screen.getByText('Postings that fit your profile.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
  })
})

describe('Card', () => {
  it('is a region named by its heading when it has a title', () => {
    render(
      <Card
        title="Job tracking"
        headingLevel={3}
        actions={<a href="/x">Open</a>}
      >
        3 jobs
      </Card>,
    )
    const region = screen.getByRole('region', { name: 'Job tracking' })
    expect(region).toHaveTextContent('3 jobs')
    expect(
      screen.getByRole('heading', { level: 3, name: 'Job tracking' }),
    ).toBeInTheDocument()
  })

  it('is a plain box without a title', () => {
    render(<Card>plain</Card>)
    expect(screen.queryByRole('region')).toBeNull()
    expect(screen.getByText('plain')).toHaveClass('card')
  })
})

describe('Badge', () => {
  it('shows its text with a tone class', () => {
    render(<Badge tone="success">Applied</Badge>)
    expect(screen.getByText('Applied')).toHaveClass('badge', 'badge--success')
  })
})

describe('Alert', () => {
  it('uses role alert for errors', () => {
    render(<Alert tone="danger">Could not save.</Alert>)
    expect(screen.getByRole('alert')).toHaveTextContent('Could not save.')
  })

  it('uses role status for the other tones', () => {
    render(
      <Alert tone="success" title="Saved">
        Version 3.
      </Alert>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('SavedVersion 3.')
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

describe('EmptyState', () => {
  it('shows the title, text and action', () => {
    render(
      <EmptyState
        title="No tracked jobs yet"
        action={<a href="/postings">Browse postings</a>}
      >
        Mark a posting as interesting to track it.
      </EmptyState>,
    )
    expect(screen.getByText('No tracked jobs yet')).toBeInTheDocument()
    expect(screen.getByText(/Mark a posting/)).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Browse postings' }),
    ).toBeInTheDocument()
  })
})

describe('FormField', () => {
  it('labels the control and describes it by the hint', () => {
    render(
      <FormField label="Handle" hint="Lower-case letters and digits.">
        {(control) => <input {...control} />}
      </FormField>,
    )
    const input = screen.getByLabelText('Handle')
    expect(input).toHaveAccessibleDescription('Lower-case letters and digits.')
    expect(input).not.toHaveAttribute('aria-invalid')
  })

  it('marks the control invalid and announces the error', () => {
    render(
      <FormField
        label="Handle"
        hint="Lower-case."
        error="This handle is taken."
      >
        {(control) => <input {...control} />}
      </FormField>,
    )
    const input = screen.getByLabelText('Handle')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription(
      'Lower-case. This handle is taken.',
    )
    expect(screen.getByRole('alert')).toHaveTextContent('This handle is taken.')
  })
})
