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

export const CODE_PROVIDERS = ['GITHUB'] as const
export type CodeProvider = (typeof CODE_PROVIDERS)[number]

/** A code hosting account the user picks repositories from. At most one per provider. */
export interface CodeAccount {
  provider: CodeProvider
  username: string
}

export const TECH_CATEGORIES = [
  'LANGUAGE',
  'FRAMEWORK',
  'LIBRARY',
  'DATABASE',
  'TOOL',
  'PLATFORM',
  'OTHER',
] as const
export type TechCategory = (typeof TECH_CATEGORIES)[number]

/** One technology found in a repository, with the file it was read from. */
export interface Technology {
  name: string
  category: TechCategory
  evidencePath: string
  evidence: string
}

/** What the analysis of a repository found; stored with the picked project. */
export interface ProjectAnalysis {
  summary: string
  architecture: string
  techStack: Technology[]
  files: string[]
  truncated: boolean
  analyzedAt: string
}

/** A repository the user picked; a snapshot of its public facts plus the user's note. */
export interface OpenSourceProject {
  provider: CodeProvider
  fullName: string
  name: string
  url: string
  description: string | null
  language: string | null
  topics: string[]
  stars: number | null
  note: string | null
  analysis: ProjectAnalysis | null
}

/** Two repositories are the same when provider and full name match; the page and the skills use this. */
export const sameRepository = (
  a: Pick<OpenSourceProject, 'provider' | 'fullName'>,
  b: Pick<OpenSourceProject, 'provider' | 'fullName'>,
) => a.provider === b.provider && a.fullName === b.fullName

/** A repository as the listing endpoint returns it, before the user picks it. */
export interface Repository {
  provider: CodeProvider
  fullName: string
  name: string
  url: string
  description: string | null
  language: string | null
  topics: string[]
  stars: number | null
  pushedAt: string | null
}

/** `ANALYSIS` skills were added from a repository analysis; `MANUAL` ones were typed by the user. */
export const SKILL_ORIGINS = ['MANUAL', 'ANALYSIS'] as const
export type SkillOrigin = (typeof SKILL_ORIGINS)[number]

/** A repository that names a skill, with the file and note that show it. */
export interface SkillSource {
  provider: CodeProvider
  fullName: string
  url: string
  evidence: string
}

export interface Skill {
  name: string
  level: SkillLevel | null
  origin: SkillOrigin
  sources: SkillSource[]
}

export interface Preferences {
  targetRoles: string[]
  workloadMin: number | null
  workloadMax: number | null
  remoteModes: RemoteMode[]
  cantons: string[]
  minSalaryChf: number | null
}

/** Who the CV is about. Every field is optional to save; the full name is needed to export a CV. */
export interface Contact {
  fullName: string | null
  headline: string | null
  email: string | null
  phone: string | null
  website: string | null
  linkedin: string | null
}

export interface Profile {
  contact: Contact
  location: Location | null
  workAuthorisation: WorkAuthorisation | null
  languages: LanguageSkill[]
  summary: string | null
  experience: Experience[]
  education: Education[]
  publications: Publication[]
  codeAccounts: CodeAccount[]
  openSourceProjects: OpenSourceProject[]
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
    contact: {
      fullName: null,
      headline: null,
      email: null,
      phone: null,
      website: null,
      linkedin: null,
    },
    location: null,
    workAuthorisation: null,
    languages: [],
    summary: null,
    experience: [],
    education: [],
    publications: [],
    codeAccounts: [],
    openSourceProjects: [],
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

export const blankSkill = (): Skill => ({
  name: '',
  level: null,
  origin: 'MANUAL',
  sources: [],
})

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
    contact: {
      fullName: text(p.contact.fullName),
      headline: text(p.contact.headline),
      email: text(p.contact.email),
      phone: text(p.contact.phone),
      website: text(p.contact.website),
      linkedin: text(p.contact.linkedin),
    },
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
    codeAccounts: p.codeAccounts.map((a) => ({
      provider: a.provider,
      username: required(a.username),
    })),
    openSourceProjects: p.openSourceProjects.map((j) => ({
      provider: j.provider,
      fullName: required(j.fullName),
      name: required(j.name),
      url: required(j.url),
      description: text(j.description),
      language: text(j.language),
      topics: j.topics,
      stars: j.stars,
      note: text(j.note),
      analysis: j.analysis,
    })),
    // A skill from before origins and sources goes out without them; the server reads that as typed by the user.
    skills: p.skills.map((s) => ({
      name: required(s.name),
      level: s.level,
      origin: s.origin,
      sources: s.sources,
    })),
    preferences: {
      ...p.preferences,
      targetRoles: p.preferences.targetRoles.map(required),
      cantons: p.preferences.cantons.map(required),
    },
  }
}
