// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

// Read from disk: Vitest does not load stylesheets, not even with `?raw`.
const tokens = readFileSync(new URL('./tokens.css', import.meta.url), 'utf8')

/** The custom properties of the first block that `start` matches, as name to value. */
function block(css: string, start: RegExp): Record<string, string> {
  const at = css.search(start)
  if (at < 0) throw new Error(`no block ${start}`)
  const open = css.indexOf('{', css.indexOf(':root', at))
  const body = css.slice(open + 1, css.indexOf('}', open))
  return Object.fromEntries(
    [...body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map(([, k, v]) => [
      k,
      v.trim(),
    ]),
  )
}

function luminance(hex: string): number {
  expect(hex).toMatch(/^#[0-9a-f]{6}$/i)
  const n = parseInt(hex.slice(1), 16)
  const [r, g, b] = [n >> 16, (n >> 8) & 255, n & 255].map((c) => {
    const s = c / 255
    return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG 2 contrast ratio. */
function ratio(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

const light = block(tokens, /^:root\s*\{/m)
// The dark theme overrides only colours and shadows; everything else comes from the light block.
const dark = {
  ...light,
  ...block(tokens, /@media \(prefers-color-scheme: dark\)/),
}

const backgrounds = ['--color-bg', '--color-surface', '--color-surface-2']
const textPairs: [string, string][] = [
  ...[
    '--color-text',
    '--color-muted',
    '--color-primary',
    '--color-success',
    '--color-warning',
    '--color-danger',
  ].flatMap((fg) => backgrounds.map((bg): [string, string] => [fg, bg])),
  ['--color-primary-contrast', '--color-primary'],
  ['--color-primary-contrast', '--color-primary-hover'],
  ['--color-primary', '--color-primary-soft'],
  ['--color-text', '--color-primary-soft'],
  ['--color-success', '--color-success-soft'],
  ['--color-warning', '--color-warning-soft'],
  ['--color-danger', '--color-danger-soft'],
  ['--color-text', '--color-success-soft'],
  ['--color-text', '--color-warning-soft'],
  ['--color-text', '--color-danger-soft'],
]
// Non-text contrast (WCAG 1.4.11): control edges and the focus ring.
const uiPairs: [string, string][] = [
  '--color-border-strong',
  '--color-focus',
].flatMap((fg) => backgrounds.map((bg): [string, string] => [fg, bg]))

describe.each([
  ['light', light],
  ['dark', dark],
])('the %s theme', (_, theme) => {
  it.each(textPairs)('%s on %s reaches 4.5:1', (fg, bg) => {
    expect(ratio(theme[fg], theme[bg])).toBeGreaterThanOrEqual(4.5)
  })

  it.each(uiPairs)('%s on %s reaches 3:1', (fg, bg) => {
    expect(ratio(theme[fg], theme[bg])).toBeGreaterThanOrEqual(3)
  })
})

it('keeps the accent hue as the light primary', () => {
  expect(light['--color-primary']).toBe('#1f5fbf')
})

it('defines every dark colour in the light theme too', () => {
  const darkOnly = block(tokens, /@media \(prefers-color-scheme: dark\)/)
  for (const name of Object.keys(darkOnly)) expect(light).toHaveProperty(name)
})
