import { describe, expect, it } from 'vitest'
import {
  applyAnalysis,
  removeProjectSources,
  skillsAfterProjectChange,
} from './skills.ts'
import type {
  OpenSourceProject,
  ProjectAnalysis,
  Skill,
  SkillSource,
} from './types.ts'

const project: OpenSourceProject = {
  provider: 'GITHUB',
  fullName: 'octocat/hello-world',
  name: 'hello-world',
  url: 'https://github.com/octocat/hello-world',
  description: null,
  language: 'Kotlin',
  topics: [],
  stars: 42,
  note: null,
  analysis: null,
}

const other: OpenSourceProject = {
  ...project,
  fullName: 'octocat/other',
  name: 'other',
  url: 'https://github.com/octocat/other',
}

const sourceOf = (
  repo: OpenSourceProject,
  evidence = 'build.gradle.kts: plugin',
): SkillSource => ({
  provider: repo.provider,
  fullName: repo.fullName,
  url: repo.url,
  evidence,
})

const analysisOf = (
  ...techs: [name: string, evidencePath: string, evidence: string][]
): ProjectAnalysis => ({
  summary: 'A sample.',
  architecture: 'One module.',
  techStack: techs.map(([name, evidencePath, evidence]) => ({
    name,
    category: 'FRAMEWORK',
    evidencePath,
    evidence,
  })),
  files: ['README.md', 'build.gradle.kts'],
  truncated: false,
  analyzedAt: '2026-10-24T12:00:00Z',
})

describe('applyAnalysis', () => {
  it('adds a new skill with origin ANALYSIS, no level and one source', () => {
    const analysis = analysisOf([
      'Spring Boot',
      'build.gradle.kts',
      'spring-boot gradle plugin and starter dependencies',
    ])
    const skills = applyAnalysis([], project, analysis)
    expect(skills).toEqual([
      {
        name: 'Spring Boot',
        level: null,
        origin: 'ANALYSIS',
        sources: [
          {
            provider: 'GITHUB',
            fullName: 'octocat/hello-world',
            url: 'https://github.com/octocat/hello-world',
            evidence:
              'build.gradle.kts: spring-boot gradle plugin and starter dependencies',
          },
        ],
      },
    ])
  })

  it('gives an existing skill the source and keeps its level and origin, with no second skill', () => {
    const before: Skill[] = [
      { name: 'kotlin', level: 'EXPERT', origin: 'MANUAL', sources: [] },
    ]
    const skills = applyAnalysis(
      before,
      project,
      analysisOf(['Kotlin', 'build.gradle.kts', 'kotlin jvm plugin']),
    )
    expect(skills).toHaveLength(1)
    expect(skills[0]).toMatchObject({
      name: 'kotlin',
      level: 'EXPERT',
      origin: 'MANUAL',
    })
    expect(skills[0]!.sources).toEqual([
      sourceOf(project, 'build.gradle.kts: kotlin jvm plugin'),
    ])
  })

  it('replaces the earlier sources of the same repository on re-analysis', () => {
    const before: Skill[] = [
      {
        name: 'Maven',
        level: null,
        origin: 'ANALYSIS',
        sources: [sourceOf(project)],
      },
      {
        name: 'Kotlin',
        level: null,
        origin: 'ANALYSIS',
        sources: [sourceOf(project), sourceOf(other)],
      },
    ]
    const skills = applyAnalysis(
      before,
      project,
      analysisOf(['Kotlin', 'build.gradle.kts', 'kotlin jvm plugin']),
    )
    expect(skills).toHaveLength(1)
    expect(skills[0]!.name).toBe('Kotlin')
    expect(skills[0]!.sources).toEqual([
      sourceOf(other),
      sourceOf(project, 'build.gradle.kts: kotlin jvm plugin'),
    ])
  })

  it('does not add a second source for a technology named twice', () => {
    const skills = applyAnalysis(
      [],
      project,
      analysisOf(
        ['Kotlin', 'build.gradle.kts', 'a'],
        ['kotlin', 'README.md', 'b'],
      ),
    )
    expect(skills).toHaveLength(1)
    expect(skills[0]!.sources).toHaveLength(1)
  })
})

describe('removeProjectSources', () => {
  it('removes the sources of a project and the analysis skills left without any', () => {
    const before: Skill[] = [
      {
        name: 'Spring Boot',
        level: null,
        origin: 'ANALYSIS',
        sources: [sourceOf(project)],
      },
      {
        name: 'Kotlin',
        level: 'EXPERT',
        origin: 'MANUAL',
        sources: [sourceOf(project)],
      },
      {
        name: 'Java',
        level: null,
        origin: 'ANALYSIS',
        sources: [sourceOf(project), sourceOf(other)],
      },
    ]
    expect(removeProjectSources(before, project)).toEqual([
      { name: 'Kotlin', level: 'EXPERT', origin: 'MANUAL', sources: [] },
      {
        name: 'Java',
        level: null,
        origin: 'ANALYSIS',
        sources: [sourceOf(other)],
      },
    ])
  })

  it('leaves the skills of other repositories and manual skills without sources alone', () => {
    const before: Skill[] = [
      { name: 'PostgreSQL', level: null, origin: 'MANUAL', sources: [] },
    ]
    expect(removeProjectSources(before, project)).toEqual(before)
  })

  it('removing a project and then its analysis leaves the rest of the skills', () => {
    const before: Skill[] = [
      { name: 'Kotlin', level: 'EXPERT', origin: 'MANUAL', sources: [] },
      {
        name: 'Spring Boot',
        level: null,
        origin: 'ANALYSIS',
        sources: [sourceOf(project)],
      },
    ]
    const after = removeProjectSources(before, project)
    expect(after.map((s) => s.name)).toEqual(['Kotlin'])
  })
})

describe('skillsAfterProjectChange', () => {
  const analysed: OpenSourceProject = {
    ...project,
    analysis: {
      summary: 's',
      architecture: 'a',
      techStack: [],
      files: [],
      truncated: false,
      analyzedAt: '2026-10-24T12:00:00Z',
    },
  }
  const skills: Skill[] = [
    {
      name: 'Spring Boot',
      level: null,
      origin: 'ANALYSIS',
      sources: [sourceOf(project)],
    },
  ]

  it('removes the sources of a project that was removed', () => {
    expect(skillsAfterProjectChange(skills, [project], [other])).toEqual([])
  })

  it('removes the sources of a project whose analysis was removed', () => {
    expect(
      skillsAfterProjectChange(
        skills,
        [analysed],
        [{ ...analysed, analysis: null }],
      ),
    ).toEqual([])
  })

  it('keeps the skills when only the note or the order changed', () => {
    expect(
      skillsAfterProjectChange(
        skills,
        [analysed, other],
        [other, { ...analysed, note: 'changed' }],
      ),
    ).toEqual(skills)
  })
})
