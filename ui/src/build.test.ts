import { build, type Rollup } from 'vite'
import { expect, it } from 'vitest'

/**
 * The served page sends a Content-Security-Policy without 'unsafe-inline'. This builds the app in memory and fails
 * if the output needs an inline script, an inline style or an event-handler attribute.
 */
it('builds a page that needs no inline script or style', async () => {
  const result = (await build({
    logLevel: 'silent',
    build: { write: false },
  })) as Rollup.RollupOutput
  const html = result.output.find((o) => o.fileName === 'index.html')
  expect(html && html.type === 'asset').toBe(true)
  const page = String((html as Rollup.OutputAsset).source)

  const scripts = [...page.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)]
  expect(scripts.length).toBeGreaterThan(0)
  for (const [, attrs, body] of scripts) {
    expect(attrs).toMatch(/\bsrc="/)
    expect(body.trim()).toBe('')
  }
  expect(page).not.toMatch(/<style\b/i)
  expect(page).not.toMatch(/\sstyle=/i)
  expect(page).not.toMatch(/\son[a-z]+\s*=/i)
  expect(page).not.toMatch(/javascript:/i)
}, 60_000)
