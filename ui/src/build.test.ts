// @vitest-environment node
import { readdirSync, readFileSync } from 'node:fs'
import { build, type Rollup } from 'vite'
import { beforeAll, describe, expect, it } from 'vitest'

/**
 * The served page sends a Content-Security-Policy without 'unsafe-inline' (`ui/nginx/security-headers.conf`). This
 * builds the app in memory and fails if the output needs an inline script, an inline style, an event-handler
 * attribute, or a stylesheet or font from another origin.
 */
describe('the production build', () => {
  let output: Rollup.RollupOutput['output']
  let page: string

  beforeAll(async () => {
    const result = (await build({
      logLevel: 'silent',
      build: { write: false },
    })) as Rollup.RollupOutput
    output = result.output
    const html = output.find((o) => o.fileName === 'index.html')
    expect(html && html.type === 'asset').toBe(true)
    page = String((html as Rollup.OutputAsset).source)
  }, 60_000)

  const assets = (ext: string) =>
    output
      .filter((o): o is Rollup.OutputAsset => o.type === 'asset')
      .filter((o) => o.fileName.endsWith(ext))
      .map((o) => ({ name: o.fileName, text: String(o.source) }))

  it('builds a page that needs no inline script or style', () => {
    const scripts = [
      ...page.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi),
    ]
    expect(scripts.length).toBeGreaterThan(0)
    for (const [, attrs, body] of scripts) {
      expect(attrs).toMatch(/\bsrc="/)
      expect(body.trim()).toBe('')
    }
    expect(page).not.toMatch(/<style\b/i)
    expect(page).not.toMatch(/\sstyle=/i)
    expect(page).not.toMatch(/\son[a-z]+\s*=/i)
    expect(page).not.toMatch(/javascript:/i)
  })

  it('links only same-origin stylesheets and no font service', () => {
    const links = [...page.matchAll(/<link\b[^>]*>/gi)].map(([l]) => l)
    const sheets = links.filter((l) => /rel="stylesheet"/i.test(l))
    expect(sheets.length).toBeGreaterThan(0)
    for (const link of links) {
      const href = /href="([^"]*)"/i.exec(link)?.[1] ?? ''
      expect(href, link).toMatch(/^\/(?!\/)/)
    }
  })

  it('has one stylesheet whose imports, urls and fonts stay on our origin', () => {
    const css = assets('.css')
    expect(css).toHaveLength(1)
    for (const { name, text } of css) {
      expect(text, name).not.toMatch(/@import/i)
      for (const [, raw] of text.matchAll(/url\(\s*([^)]*?)\s*\)/gi)) {
        const target = raw.replace(/^['"]|['"]$/g, '')
        // Inline SVG data is allowed by `img-src 'self' data:`; anything else must be a path on our origin.
        expect(target, name).toMatch(/^(data:image\/svg\+xml|\/(?!\/)|\.)/)
      }
      for (const [face] of text.matchAll(/@font-face\s*\{[^}]*\}/gi)) {
        expect(face).not.toMatch(/https?:|url\(\s*['"]?\/\//i)
      }
    }
  })

  it('ships fonts, if any, as files of its own', () => {
    const external = output.filter((o) =>
      /fonts\.(googleapis|gstatic)|use\.typekit|fonts\.bunny/i.test(
        o.type === 'asset' ? String(o.source) : o.code,
      ),
    )
    expect(external.map((o) => o.fileName)).toEqual([])
  })
})

// Sources rather than the bundle: a `style` prop compiles to a plain object property the bundle cannot tell apart.
describe('the sources', () => {
  const files = (readdirSync('src', { recursive: true }) as string[]).map(
    (f) => `src/${f}`,
  )
  const read = (f: string) => readFileSync(f, 'utf8')

  it('set no inline styles in components', () => {
    const tsx = files.filter(
      (f) => f.endsWith('.tsx') && !/\.test\.tsx$/.test(f),
    )
    expect(tsx.length).toBeGreaterThan(10)
    const offenders = tsx.filter((f) =>
      /\sstyle=\{|<style\b|dangerouslySetInnerHTML/.test(read(f)),
    )
    expect(offenders).toEqual([])
  })

  it('keep raw colour values in tokens.css only', () => {
    const css = files.filter((f) => f.endsWith('.css'))
    expect(css).toContain('src/styles/tokens.css')
    const raw =
      /#[0-9a-f]{3,8}\b|\b(rgba?|hsla?|hwb|lab|lch|oklab|oklch)\(|(?<![\w-])(white|black|red|green|blue|gray|grey)(?![\w-])/i
    const offenders = css
      .filter((f) => f !== 'src/styles/tokens.css')
      .flatMap((f) =>
        read(f)
          // Comments may name colours.
          .replace(/\/\*[\s\S]*?\*\//g, '')
          .split('\n')
          .filter((line) => raw.test(line))
          .map((line) => `${f}: ${line.trim()}`),
      )
    expect(offenders).toEqual([])
  })
})
