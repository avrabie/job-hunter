import { Repeater, Select, TextInput } from './fields.tsx'
import { sourcesOf } from './skills.ts'
import { SKILL_LEVELS, blankSkill, type Skill } from './types.ts'

/** Skills with their level and the repositories that back them. */
export function SkillsSection({
  skills,
  onChange,
  errors,
}: {
  skills: Skill[]
  onChange: (skills: Skill[]) => void
  errors: Record<string, string>
}) {
  return (
    <section>
      <h2>Skills</h2>
      <Repeater
        title="Skill"
        items={skills}
        onChange={onChange}
        blank={blankSkill}
      >
        {(item, patch, i) => (
          <>
            <TextInput
              label="Skill name"
              value={item.name}
              error={errors[`skills[${i}].name`]}
              path={`skills[${i}].name`}
              onChange={(name) => patch({ name })}
            />
            <Select
              label="Skill level"
              value={item.level}
              options={SKILL_LEVELS}
              empty="Not set"
              error={errors[`skills[${i}].level`]}
              path={`skills[${i}].level`}
              onChange={(level) => patch({ level })}
            />
            {item.origin === 'ANALYSIS' && (
              <p className="hint">from repository analysis</p>
            )}
            {sourcesOf(item).length > 0 && (
              <ul aria-label={`Sources of Skill ${i + 1}`}>
                {sourcesOf(item).map((s, j) => (
                  <li key={`${s.provider}:${s.fullName}`}>
                    <a href={s.url} target="_blank" rel="noreferrer">
                      {s.fullName}
                    </a>{' '}
                    <span className="hint">{s.evidence}</span>{' '}
                    <button
                      type="button"
                      className="secondary"
                      aria-label={`Remove source ${j + 1} of Skill ${i + 1}`}
                      onClick={() =>
                        patch({
                          sources: sourcesOf(item).filter((_, k) => k !== j),
                        })
                      }
                    >
                      Remove source
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </Repeater>
    </section>
  )
}
