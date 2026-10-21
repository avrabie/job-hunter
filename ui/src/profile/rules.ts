import { toRequestProfile, type Profile } from './types.ts'

/**
 * The cheap profile rules, checked in the browser while the user edits. They mirror `ProfileValidator` in the backend
 * (same paths, same messages); the server stays the authority and its messages win when both speak. Messages name the
 * rule and never contain the value.
 */
export function checkProfile(profile: Profile): Record<string, string> {
  const p = toRequestProfile(profile)
  const problems: Record<string, string> = {}
  const required = (path: string, value: string) => {
    if (value === '') problems[path] = 'must not be blank'
  }
  const order = (path: string, start: string | null, end: string | null) => {
    if (start && end && end < start)
      problems[path] = 'must not be before the start'
  }

  const canton = p.location?.canton
  if (canton != null && !/^[A-Z]{2}$/.test(canton))
    problems['location.canton'] = 'must be two uppercase letters'

  p.languages.forEach((l, i) => {
    if (!/^[a-z]{2}$/.test(l.code))
      problems[`languages[${i}].code`] = 'must be two lowercase letters'
  })

  p.experience.forEach((e, i) => {
    const at = `experience[${i}]`
    required(`${at}.employer`, e.employer)
    required(`${at}.title`, e.title)
    order(`${at}.end`, e.start, e.end)
    e.achievements.forEach((a, j) => required(`${at}.achievements[${j}]`, a))
  })

  p.education.forEach((e, i) => {
    const at = `education[${i}]`
    required(`${at}.institution`, e.institution)
    required(`${at}.degree`, e.degree)
    order(`${at}.end`, e.start, e.end)
  })

  p.skills.forEach((s, i) => required(`skills[${i}].name`, s.name))

  const prefs = p.preferences
  prefs.targetRoles.forEach((r, i) =>
    required(`preferences.targetRoles[${i}]`, r),
  )
  prefs.cantons.forEach((c, i) => {
    if (!/^[A-Z]{2}$/.test(c))
      problems[`preferences.cantons[${i}]`] = 'must be two uppercase letters'
  })
  const outside = (n: number | null) => n !== null && (n < 10 || n > 100)
  if (outside(prefs.workloadMin))
    problems['preferences.workloadMin'] = 'must be between 10 and 100'
  if (outside(prefs.workloadMax))
    problems['preferences.workloadMax'] = 'must be between 10 and 100'
  if (
    prefs.workloadMin !== null &&
    prefs.workloadMax !== null &&
    prefs.workloadMin > prefs.workloadMax
  )
    problems['preferences.workloadMax'] = 'must not be below the minimum'
  if (prefs.minSalaryChf !== null && prefs.minSalaryChf < 0)
    problems['preferences.minSalaryChf'] = 'must not be negative'

  return problems
}
