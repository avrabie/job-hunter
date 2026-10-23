/**
 * The profile as the backend sends and accepts it. Written by hand to mirror `net.s4v3.backend.profile.Profile`;
 * `fixtures/full-profile.json` is the same sample the backend tests use, and `profile.test.ts` fails if they drift.
 * Months are `YYYY-MM` strings. Personal data: never log it, never store it in the browser.
 */
export const WORK_AUTHORISATIONS = [
  'SWISS_CITIZEN',
  'PERMIT_C',
  'PERMIT_B',
  'PERMIT_L',
  'PERMIT_G',
  'NEEDS_SPONSORSHIP',
] as const
export type WorkAuthorisation = (typeof WORK_AUTHORISATIONS)[number]

export const LANGUAGE_LEVELS = [
  'A1',
  'A2',
  'B1',
  'B2',
  'C1',
  'C2',
  'NATIVE',
] as const
export type LanguageLevel = (typeof LANGUAGE_LEVELS)[number]

export const SKILL_LEVELS = [
  'BEGINNER',
  'INTERMEDIATE',
  'ADVANCED',
  'EXPERT',
] as const
export type SkillLevel = (typeof SKILL_LEVELS)[number]

export const REMOTE_MODES = ['ONSITE', 'HYBRID', 'REMOTE'] as const
export type RemoteMode = (typeof REMOTE_MODES)[number]

export interface Location {
  city: string | null
  canton: string | null
}

export interface LanguageSkill {
  code: string
  level: LanguageLevel
}

export interface Experience {
  employer: string
  title: string
  start: string
  end: string | null
  location: string | null
  achievements: string[]
  technologies: string[]
}

export interface Education {
  institution: string
  degree: string
  field: string | null
  start: string | null
  end: string | null
}

export interface Publication {
  title: string
  venue: string | null
  year: number | null
  authors: string | null
  url: string | null
}

export const PROJECT_TYPES = [
  'ASSIGNMENT',
  'PERSONAL',
  'OPEN_SOURCE',
  'WORK',
] as const
export type ProjectType = (typeof PROJECT_TYPES)[number]

export interface ProjectLink {
  label: string
  url: string
}

export interface Project {
  name: string
  type: ProjectType
  context: string | null
  start: string | null
  end: string | null
  description: string | null
  technologies: string[]
  results: string[]
  links: ProjectLink[]
  highlight: boolean
}

export interface Skill {
  name: string
  level: SkillLevel | null
}

export interface Preferences {
  targetRoles: string[]
  workloadMin: number | null
  workloadMax: number | null
  remoteModes: RemoteMode[]
  cantons: string[]
  minSalaryChf: number | null
}

export interface Profile {
  location: Location | null
  workAuthorisation: WorkAuthorisation | null
  languages: LanguageSkill[]
  summary: string | null
  experience: Experience[]
  education: Education[]
  publications: Publication[]
  projects: Project[]
  skills: Skill[]
  preferences: Preferences
}

/** A profile and the version it was read at; 0 means nothing is stored yet. */
export interface StoredProfile {
  version: number
  profile: Profile
}

export function emptyProfile(): Profile {
  return {
    location: null,
    workAuthorisation: null,
    languages: [],
    summary: null,
    experience: [],
    education: [],
    publications: [],
    projects: [],
    skills: [],
    preferences: {
      targetRoles: [],
      workloadMin: null,
      workloadMax: null,
      remoteModes: [],
      cantons: [],
      minSalaryChf: null,
    },
  }
}

export const blankExperience = (): Experience => ({
  employer: '',
  title: '',
  start: '',
  end: null,
  location: null,
  achievements: [],
  technologies: [],
})

export const blankEducation = (): Education => ({
  institution: '',
  degree: '',
  field: null,
  start: null,
  end: null,
})

export const blankPublication = (): Publication => ({
  title: '',
  venue: null,
  year: null,
  authors: null,
  url: null,
})

export const blankProject = (): Project => ({
  name: '',
  type: 'ASSIGNMENT',
  context: null,
  start: null,
  end: null,
  description: null,
  technologies: [],
  results: [],
  links: [],
  highlight: false,
})

export const blankProjectLink = (): ProjectLink => ({ label: '', url: '' })

export const blankSkill = (): Skill => ({ name: '', level: null })

export const blankLanguage = (): LanguageSkill => ({ code: '', level: 'B2' })

/**
 * Trims each tag, drops blank ones and drops a tag that differs from an earlier one only in case; the first spelling
 * wins. The server keeps tags as they are sent, so the page and the CV reader tidy them first.
 */
export function tidyTags(tags: string[]): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const raw of tags) {
    const tag = raw.trim()
    const key = tag.toLowerCase()
    if (tag === '' || seen.has(key)) continue
    seen.add(key)
    out.push(tag)
  }
  return out
}

/**
 * What goes on the wire: empty optional text becomes null, text is trimmed, and a location with nothing in it is
 * dropped. The page keeps the user's raw text while they type; this runs once per save.
 */
export function toRequestProfile(p: Profile): Profile {
  const text = (v: string | null) => {
    const t = v?.trim() ?? ''
    return t === '' ? null : t
  }
  const required = (v: string) => v.trim()
  const city = text(p.location?.city ?? null)
  const canton = text(p.location?.canton ?? null)
  return {
    location: city === null && canton === null ? null : { city, canton },
    workAuthorisation: p.workAuthorisation,
    languages: p.languages.map((l) => ({ ...l, code: required(l.code) })),
    summary: text(p.summary),
    experience: p.experience.map((e) => ({
      employer: required(e.employer),
      title: required(e.title),
      start: e.start,
      end: e.end === '' ? null : e.end,
      location: text(e.location),
      achievements: e.achievements.map(required),
      technologies: tidyTags(e.technologies),
    })),
    education: p.education.map((e) => ({
      institution: required(e.institution),
      degree: required(e.degree),
      field: text(e.field),
      start: e.start === '' ? null : e.start,
      end: e.end === '' ? null : e.end,
    })),
    publications: p.publications.map((b) => ({
      title: required(b.title),
      venue: text(b.venue),
      year: b.year,
      authors: text(b.authors),
      url: text(b.url),
    })),
    projects: p.projects.map((j) => ({
      name: required(j.name),
      type: j.type,
      context: text(j.context),
      start: j.start === '' ? null : j.start,
      end: j.end === '' ? null : j.end,
      description: text(j.description),
      technologies: tidyTags(j.technologies),
      results: j.results.map(required),
      // A link with both fields empty is a row the user left blank; it is not sent.
      links: j.links
        .filter((l) => required(l.label) !== '' || required(l.url) !== '')
        .map((l) => ({ label: required(l.label), url: required(l.url) })),
      highlight: j.highlight,
    })),
    skills: p.skills.map((s) => ({ name: required(s.name), level: s.level })),
    preferences: {
      ...p.preferences,
      targetRoles: p.preferences.targetRoles.map(required),
      cantons: p.preferences.cantons.map(required),
    },
  }
}
