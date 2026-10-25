import { useEffect, useRef, useState } from 'react'
import { Collapsible } from '../ui/Collapsible.tsx'
import { revealAndFocus } from '../ui/revealAndFocus.ts'
import { Select, TextInput } from './fields.tsx'
import { ProfileSection } from './ProfileSection.tsx'
import { countOf, hasErrorAt, plural } from './sectionSummary.ts'
import { SKILL_GROUP_LABELS, groupSkills } from './skillGroups.ts'
import { sourcesOf } from './skills.ts'
import { SKILL_LEVELS, blankSkill, type Skill } from './types.ts'

/** With more skills than this, the groups start closed so the section opens on the list of groups and counts. */
const MANY_SKILLS = 20

/**
 * Skills grouped by technology type (the groups of the CV export), each group a collapsible section with its count,
 * and a filter by name across the groups. Grouping and sorting are display only: every edit, removal and addition
 * changes the original array at the skill's own index, so the saved order never changes.
 */
export function SkillsSection({
  skills,
  onChange,
  errors,
}: {
  skills: Skill[]
  onChange: (skills: Skill[]) => void
  errors: Record<string, string>
}) {
  const [filter, setFilter] = useState('')
  // Decided once, like the other sections' defaults, so the groups do not close when the 21st skill is added.
  const [groupsOpen] = useState(skills.length <= MANY_SKILLS)
  // The skill just added, to focus its name once it is on the page.
  const focusIndex = useRef<number | null>(null)
  /*
   * The skills as they were when focus entered the list. While the user is in the list, rows are placed by this
   * snapshot: otherwise typing a name or picking a level would move the row to another group or position, unmount
   * the focused field and lose the typing. The rows regroup when focus leaves the list. A snapshot of another length
   * (a skill was added or removed meanwhile) is not used.
   */
  const [frozen, setFrozen] = useState<Skill[] | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  const layout = frozen && frozen.length === skills.length ? frozen : skills
  const query = filter.trim().toLowerCase()
  const errorAt = (index: number) => hasErrorAt(errors, [`skills[${index}].`])
  // A skill with a message stays visible whatever the filter, so a failed save can show and focus it.
  const shown = (index: number) =>
    query === '' ||
    layout[index]!.name.toLowerCase().includes(query) ||
    errorAt(index)
  const groups = groupSkills(layout)
    .map(({ group, items }) => ({
      group,
      items: items.filter(({ index }) => shown(index)),
    }))
    .filter(({ items }) => items.length > 0)

  useEffect(() => {
    const index = focusIndex.current
    if (index === null || index >= skills.length) return
    focusIndex.current = null
    const name = listRef.current?.querySelector<HTMLElement>(
      `[data-path="skills[${index}].name"]`,
    )
    // Its group may be closed (many skills, or "Collapse all"); revealing opens it.
    if (name) revealAndFocus(name)
  }, [skills.length])

  const patch = (index: number, change: Partial<Skill>) =>
    onChange(skills.map((s, j) => (j === index ? { ...s, ...change } : s)))

  const remove = (index: number) => {
    setFrozen(null)
    onChange(skills.filter((_, j) => j !== index))
  }

  const add = () => {
    setFrozen(null)
    // A filter would hide the new, empty skill.
    setFilter('')
    focusIndex.current = skills.length
    onChange([...skills, blankSkill()])
  }

  return (
    <ProfileSection
      name="skills"
      title="Skills"
      summary={countOf(skills.length, 'skill', 'skills')}
      hasError={hasErrorAt(errors, ['skills['])}
    >
      <div className="skills-toolbar">
        <label className="skills-toolbar__filter">
          Filter skills
          <input
            type="search"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </label>
        <button type="button" className="secondary" onClick={add}>
          Add skill
        </button>
      </div>
      {skills.length === 0 && <p className="hint">No skills yet.</p>}
      {skills.length > 0 && groups.length === 0 && (
        <p className="hint">{`No skill matches "${filter.trim()}".`}</p>
      )}
      <div
        ref={listRef}
        onFocus={() => {
          if (!frozen || frozen.length !== skills.length) setFrozen(skills)
        }}
        onBlur={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget as Node | null))
            setFrozen(null)
        }}
      >
        {groups.map(({ group, items }) => (
          <Collapsible
            key={group}
            id={`skills-${group}`}
            className="skill-group"
            title={SKILL_GROUP_LABELS[group]}
            titleAs="h3"
            summary={plural(items.length, 'skill', 'skills')}
            defaultOpen={groupsOpen}
            // A filter opens the groups it leaves, a message opens the group that holds it.
            forceOpen={
              query !== '' || items.some(({ index }) => errorAt(index))
            }
          >
            {items.map(({ index }) => (
              <SkillRow
                key={index}
                skill={skills[index]!}
                index={index}
                errors={errors}
                onPatch={(change) => patch(index, change)}
                onRemove={() => remove(index)}
              />
            ))}
          </Collapsible>
        ))}
      </div>
    </ProfileSection>
  )
}

/**
 * One skill, edited as before grouping. Labels number the skill by its place in the saved list ("Skill 3"), which
 * is also how the server names its fields (`skills[2].name`).
 */
function SkillRow({
  skill,
  index,
  errors,
  onPatch,
  onRemove,
}: {
  skill: Skill
  index: number
  errors: Record<string, string>
  onPatch: (change: Partial<Skill>) => void
  onRemove: () => void
}) {
  const at = `skills[${index}]`
  const label = `Skill ${index + 1}`
  const sources = sourcesOf(skill)
  return (
    <fieldset className="skill-row">
      <legend>{label}</legend>
      <TextInput
        label="Skill name"
        value={skill.name}
        error={errors[`${at}.name`]}
        path={`${at}.name`}
        onChange={(name) => onPatch({ name })}
      />
      <Select
        label="Skill level"
        value={skill.level}
        options={SKILL_LEVELS}
        empty="Not set"
        error={errors[`${at}.level`]}
        path={`${at}.level`}
        onChange={(level) => onPatch({ level })}
      />
      {skill.origin === 'ANALYSIS' && (
        <p className="hint">from repository analysis</p>
      )}
      {sources.length > 0 && (
        <ul aria-label={`Sources of ${label}`}>
          {sources.map((s, j) => (
            <li key={`${s.provider}:${s.fullName}`}>
              <a href={s.url} target="_blank" rel="noreferrer">
                {s.fullName}
              </a>{' '}
              <span className="hint">{s.evidence}</span>{' '}
              <button
                type="button"
                className="secondary"
                aria-label={`Remove source ${j + 1} of ${label}`}
                onClick={() =>
                  onPatch({ sources: sources.filter((_, k) => k !== j) })
                }
              >
                Remove source
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="actions compact">
        <button
          type="button"
          className="secondary"
          aria-label={`Remove ${label}`}
          onClick={onRemove}
        >
          Remove
        </button>
      </div>
    </fieldset>
  )
}
