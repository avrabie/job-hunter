import type { Skill } from './types.ts'

/** The skill groups in display order; OTHER is last. Ported from `SkillGroups.kt`, which is the reference. */
export const SKILL_GROUPS = [
  'LANGUAGES',
  'FRAMEWORKS',
  'MESSAGING',
  'SECURITY',
  'CLOUD',
  'TESTING',
  'DATABASES',
  'TOOLS',
  'OTHER',
] as const
export type SkillGroup = (typeof SKILL_GROUPS)[number]

export const SKILL_GROUP_LABELS: Record<SkillGroup, string> = {
  LANGUAGES: 'Programming languages',
  FRAMEWORKS: 'Frameworks',
  MESSAGING: 'Messaging',
  SECURITY: 'Security & identity',
  CLOUD: 'Cloud & containers',
  TESTING: 'Tests & observability',
  DATABASES: 'Databases',
  TOOLS: 'Tools',
  OTHER: 'Other',
}

const KEYWORDS: Record<Exclude<SkillGroup, 'OTHER'>, string[]> = {
  LANGUAGES: [
    'java',
    'kotlin',
    'python',
    'c',
    'c++',
    'c#',
    'rust',
    'go',
    'scala',
    'typescript',
    'javascript',
    'sql',
    'pl/sql',
    'julia',
    'r',
    'php',
    'ruby',
    'swift',
    'bash',
  ],
  FRAMEWORKS: [
    'spring',
    'spring boot',
    'webflux',
    'hibernate',
    'jpa',
    'react',
    'angular',
    'vue',
    'django',
    'flask',
    'quarkus',
    'micronaut',
    'jax-rs',
    'jsf',
    'jsp',
  ],
  MESSAGING: ['kafka', 'activemq', 'rabbitmq', 'jms', 'mqtt', 'nats', 'pulsar'],
  SECURITY: [
    'oauth2',
    'oidc',
    'jwt',
    'saml',
    'mtls',
    'scim',
    'keycloak',
    'tls',
    'sso',
  ],
  CLOUD: [
    'azure',
    'aws',
    'gcp',
    'docker',
    'podman',
    'kubernetes',
    'rancher',
    'terraform',
    'helm',
    'hetzner',
    'openshift',
    'pcf',
    'ansible',
    'linux',
    'rhel',
  ],
  TESTING: [
    'junit',
    'mockito',
    'testcontainers',
    'selenium',
    'cypress',
    'jest',
    'playwright',
    'gatling',
    'jmeter',
    'elk',
    'opentelemetry',
    'otel',
    'prometheus',
    'grafana',
  ],
  DATABASES: [
    'oracle',
    'postgresql',
    'postgres',
    'mysql',
    'mariadb',
    'mongodb',
    'cassandra',
    'redis',
    'h2',
    'sqlite',
    'elasticsearch',
    'neo4j',
  ],
  TOOLS: [
    'git',
    'gitlab',
    'github',
    'maven',
    'gradle',
    'jenkins',
    'jira',
    'intellij',
    'ci/cd',
  ],
}

/** One pattern per keyword: no letter, digit, `_`, `+` or `#` directly before or after it. */
const MATCHERS: [SkillGroup, RegExp[]][] = SKILL_GROUPS.map((group) => [
  group,
  (group === 'OTHER' ? [] : KEYWORDS[group]).map(
    (keyword) =>
      new RegExp(
        `(?<![\\p{L}\\p{N}_+#])${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![\\p{L}\\p{N}_+#])`,
        'u',
      ),
  ),
])

/** The group of a skill name: whole-token keyword match, first group in display order wins, else OTHER. */
export function groupOf(skillName: string): SkillGroup {
  const name = skillName.trim().toLowerCase().split(/\s+/).join(' ')
  return (
    MATCHERS.find(([, patterns]) => patterns.some((p) => p.test(name)))?.[0] ??
    'OTHER'
  )
}

export interface GroupedSkill {
  skill: Skill
  /** Position in the array passed to `groupSkills`, so the UI can edit the original array in place. */
  index: number
}

export interface SkillGroupEntry {
  group: SkillGroup
  items: GroupedSkill[]
}

const LEVEL_RANK: Record<string, number> = {
  EXPERT: 0,
  ADVANCED: 1,
  INTERMEDIATE: 2,
  BEGINNER: 3,
}

function rank(skill: Skill): number {
  return skill.level === null ? 4 : (LEVEL_RANK[skill.level] ?? 4)
}

/** Groups in display order, empty groups left out; inside a group by level (highest first, none last) then name. */
export function groupSkills(skills: Skill[]): SkillGroupEntry[] {
  const buckets = new Map<SkillGroup, GroupedSkill[]>()
  skills.forEach((skill, index) => {
    const group = groupOf(skill.name)
    const items = buckets.get(group) ?? []
    items.push({ skill, index })
    buckets.set(group, items)
  })
  return SKILL_GROUPS.flatMap((group) => {
    const items = buckets.get(group)
    if (!items) return []
    items.sort(
      (a, b) =>
        rank(a.skill) - rank(b.skill) ||
        a.skill.name.localeCompare(b.skill.name, undefined, {
          sensitivity: 'base',
        }) ||
        a.index - b.index,
    )
    return [{ group, items }]
  })
}
