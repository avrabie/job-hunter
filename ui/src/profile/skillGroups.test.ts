import { describe, expect, it } from 'vitest'
import cases from './skillGroups.cases.json'
import {
  SKILL_GROUP_LABELS,
  SKILL_GROUPS,
  groupOf,
  groupSkills,
} from './skillGroups.ts'
import type { Skill } from './types.ts'

const skill = (name: string, level: Skill['level'] = null): Skill => ({
  name,
  level,
  origin: 'MANUAL',
  sources: [],
})

describe('groupOf', () => {
  it.each(cases)('$name', ({ name, group }) => {
    expect(groupOf(name)).toBe(group)
  })
})

describe('labels', () => {
  it('has an English label for every group, in order', () => {
    expect(SKILL_GROUPS.map((g) => SKILL_GROUP_LABELS[g])).toEqual([
      'Programming languages',
      'Frameworks',
      'Messaging',
      'Security & identity',
      'Cloud & containers',
      'Tests & observability',
      'Databases',
      'Tools',
      'Other',
    ])
  })
})

describe('groupSkills', () => {
  it('orders groups, sorts by level then name and keeps original indexes', () => {
    const skills = [
      skill('Kafka'),
      skill('kotlin', 'ADVANCED'),
      skill('Java', 'EXPERT'),
      skill('Basket weaving'),
      skill('Python', 'ADVANCED'),
      skill('Rust'),
    ]
    const grouped = groupSkills(skills)
    expect(grouped.map((g) => g.group)).toEqual([
      'LANGUAGES',
      'MESSAGING',
      'OTHER',
    ])
    expect(grouped[0]!.items.map((i) => i.skill.name)).toEqual([
      'Java',
      'kotlin',
      'Python',
      'Rust',
    ])
    expect(grouped[0]!.items.map((i) => i.index)).toEqual([2, 1, 4, 5])
    expect(grouped[0]!.items[0]!.skill).toBe(skills[2])
    expect(skills[0]!.name).toBe('Kafka')
  })
})
