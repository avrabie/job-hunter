/** The Public CV settings the page holds, and the JSON the backend takes (the Contract in design.md). Everything starts off. */
export type PublicCvTemplate = 'CLASSIC' | 'MODERN' | 'COMPACT' | 'PROFESSIONAL'
export type PublicCvLanguage = 'EN' | 'DE'
export type PublicCvSectionKind =
  | 'PROFILE'
  | 'EXPERIENCE'
  | 'OPEN_SOURCE'
  | 'EDUCATION'
  | 'SKILLS'
  | 'LANGUAGES'
  | 'PUBLICATIONS'

/** The address of a public page is this path followed by the handle. */
export const PUBLIC_CV_PATH = '/cv/'

/** Every section, in the default order. */
export const PUBLIC_SECTION_KINDS: readonly PublicCvSectionKind[] = [
  'PROFILE',
  'EXPERIENCE',
  'OPEN_SOURCE',
  'EDUCATION',
  'SKILLS',
  'LANGUAGES',
  'PUBLICATIONS',
]

export const PUBLIC_SECTION_TITLES: Record<PublicCvSectionKind, string> = {
  PROFILE: 'Profile',
  EXPERIENCE: 'Experience',
  OPEN_SOURCE: 'Open source projects',
  EDUCATION: 'Education',
  SKILLS: 'Skills',
  LANGUAGES: 'Languages',
  PUBLICATIONS: 'Publications',
}

export interface PublicCvContact {
  email: boolean
  phone: boolean
  website: boolean
  linkedin: boolean
  codeAccounts: boolean
}

export interface PublicCvSettings {
  template: PublicCvTemplate
  language: PublicCvLanguage
  /** The ticked sections, in the order they are shown. */
  sections: PublicCvSectionKind[]
  headline: boolean
  analysis: boolean
  workAuthorisation: boolean
  location: boolean
  contact: PublicCvContact
  indexable: boolean
  /** The file cabinet JPEG shown on the page, or null for none. Only the Professional template shows it. */
  photoFileId: string | null
}

export const HANDLE_FORM_MESSAGE =
  'Use 3 to 30 characters: letters and digits, with single hyphens between them.'

const HANDLE_FORM = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

/** The problem with a handle as typed, or null when its form is right. The server still decides availability. */
export function handleProblem(handle: string): string | null {
  if (handle.length < 3 || handle.length > 30 || !HANDLE_FORM.test(handle))
    return HANDLE_FORM_MESSAGE
  return null
}

export function defaultSettings(): PublicCvSettings {
  return {
    template: 'CLASSIC',
    language: 'EN',
    sections: [],
    headline: false,
    analysis: false,
    workAuthorisation: false,
    location: false,
    contact: {
      email: false,
      phone: false,
      website: false,
      linkedin: false,
      codeAccounts: false,
    },
    indexable: false,
    photoFileId: null,
  }
}

/** Ticks a section (added at the end) or unticks it (removed); the other sections keep their order. */
export function setSection(
  sections: PublicCvSectionKind[],
  kind: PublicCvSectionKind,
  included: boolean,
): PublicCvSectionKind[] {
  const without = sections.filter((s) => s !== kind)
  return included ? [...without, kind] : without
}

/** Moves one ticked section up (delta -1) or down (delta +1); a move past either end changes nothing. */
export function moveSection(
  sections: PublicCvSectionKind[],
  index: number,
  delta: -1 | 1,
): PublicCvSectionKind[] {
  const target = index + delta
  if (target < 0 || target >= sections.length) return sections
  const next = [...sections]
  const moved = next[index]!
  next[index] = next[target]!
  next[target] = moved
  return next
}

/** The photo picker shows only for the Professional template. */
export function showsPublicPhotoPicker(
  settings: Pick<PublicCvSettings, 'template'>,
): boolean {
  return settings.template === 'PROFESSIONAL'
}
