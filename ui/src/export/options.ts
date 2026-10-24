/** The export options the page holds, and the request body the backend takes (the Contract in design.md). */
export type CvFormat = 'MARKDOWN' | 'HTML' | 'PDF'
export type CvTemplate = 'CLASSIC' | 'MODERN' | 'COMPACT'
export type CvLanguage = 'EN' | 'DE'

export const SECTION_KINDS = [
  'PROFILE',
  'EXPERIENCE',
  'OPEN_SOURCE',
  'EDUCATION',
  'SKILLS',
  'LANGUAGES',
  'PUBLICATIONS',
] as const

export type CvSectionKind = (typeof SECTION_KINDS)[number]

export const SECTION_TITLES: Record<CvSectionKind, string> = {
  PROFILE: 'Profile',
  EXPERIENCE: 'Experience',
  OPEN_SOURCE: 'Open source projects',
  EDUCATION: 'Education',
  SKILLS: 'Skills',
  LANGUAGES: 'Languages',
  PUBLICATIONS: 'Publications',
}

export interface SectionChoice {
  kind: CvSectionKind
  included: boolean
}

export interface ExportOptions {
  format: CvFormat
  template: CvTemplate
  language: CvLanguage
  /** Every section once, in the order the CV shows them; `included` says whether it is ticked. */
  sections: SectionChoice[]
  includeOpenSource: boolean
  includeAnalysis: boolean
  includeWorkAuthorisation: boolean
}

/** The body of `POST /api/cv/export`. */
export interface ExportRequest {
  format: CvFormat
  template: CvTemplate
  language: CvLanguage
  sections: CvSectionKind[]
  includeOpenSource: boolean
  includeAnalysis: boolean
  includeWorkAuthorisation: boolean
}

export function defaultOptions(): ExportOptions {
  return {
    format: 'PDF',
    template: 'CLASSIC',
    language: 'EN',
    sections: SECTION_KINDS.map((kind) => ({ kind, included: true })),
    includeOpenSource: true,
    includeAnalysis: false,
    includeWorkAuthorisation: true,
  }
}

/** Moves one section up (delta -1) or down (delta +1); a move past either end changes nothing. */
export function moveSection(
  sections: SectionChoice[],
  index: number,
  delta: -1 | 1,
): SectionChoice[] {
  const target = index + delta
  if (target < 0 || target >= sections.length) return sections
  const next = [...sections]
  const moved = next[index]!
  next[index] = next[target]!
  next[target] = moved
  return next
}

/**
 * The request for these options. Only ticked sections are sent, in the order shown. Analysis summaries are sent only
 * when open source projects are included, because the page disables that option otherwise.
 */
export function toRequest(options: ExportOptions): ExportRequest {
  return {
    format: options.format,
    template: options.template,
    language: options.language,
    sections: options.sections.filter((s) => s.included).map((s) => s.kind),
    includeOpenSource: options.includeOpenSource,
    includeAnalysis: options.includeOpenSource && options.includeAnalysis,
    includeWorkAuthorisation: options.includeWorkAuthorisation,
  }
}
