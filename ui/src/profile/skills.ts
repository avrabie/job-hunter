import {
  sameRepository,
  type OpenSourceProject,
  type ProjectAnalysis,
  type Skill,
  type SkillSource,
} from './types.ts'

const MAX_EVIDENCE = 300

type Repo = Pick<OpenSourceProject, 'provider' | 'fullName'>

const sameName = (a: string, b: string) =>
  a.trim().toLowerCase() === b.trim().toLowerCase()

/** A skill saved before sources existed has none; the server reads it the same way. */
export const sourcesOf = (skill: Skill): SkillSource[] => skill.sources ?? []

/**
 * Takes the sources of one repository off every skill, then drops the analysis skills left with no source. Other
 * skills and sources stay as they are; a new array is returned.
 */
export function removeProjectSources(skills: Skill[], project: Repo): Skill[] {
  return skills
    .map((skill) => ({
      ...skill,
      sources: sourcesOf(skill).filter((s) => !sameRepository(s, project)),
    }))
    .filter((skill) => skill.origin !== 'ANALYSIS' || skill.sources.length > 0)
}

/**
 * Called when the picked projects change: a project that was removed, or whose analysis was removed, takes its
 * sources off the skills. Other changes (notes, order, a new analysis) leave the skills as they are.
 */
export function skillsAfterProjectChange(
  skills: Skill[],
  before: OpenSourceProject[],
  after: OpenSourceProject[],
): Skill[] {
  let next = skills
  for (const old of before) {
    const now = after.find((p) => sameRepository(p, old))
    if (now === undefined || (old.analysis && !now.analysis))
      next = removeProjectSources(next, old)
  }
  return next
}

/**
 * Replaces the sources of one repository with the technologies of its analysis. A skill with the same name (any
 * letter case) gets the repository as a source and keeps its origin and level; any other technology becomes a new
 * `ANALYSIS` skill with no level. Earlier sources of the repository are removed first.
 */
export function applyAnalysis(
  skills: Skill[],
  project: OpenSourceProject,
  analysis: ProjectAnalysis,
): Skill[] {
  const next = removeProjectSources(skills, project)
  for (const tech of analysis.techStack) {
    const source: SkillSource = {
      provider: project.provider,
      fullName: project.fullName,
      url: project.url,
      // The server accepts at most 300 characters of evidence per source.
      evidence: `${tech.evidencePath}: ${tech.evidence}`.slice(0, MAX_EVIDENCE),
    }
    const index = next.findIndex((s) => sameName(s.name, tech.name))
    if (index === -1) {
      next.push({
        name: tech.name.trim(),
        level: null,
        origin: 'ANALYSIS',
        sources: [source],
      })
    } else {
      const skill = next[index]!
      const sources = sourcesOf(skill)
      if (sources.some((s) => sameRepository(s, project))) continue
      next[index] = { ...skill, sources: [...sources, source] }
    }
  }
  return next
}
