import type { ProfileSectionName } from '../cv/cvApi.ts'
import type { CodeProvider, OpenSourceProject, Profile } from './types.ts'
import type { AnalysisResult } from './analysisApi.ts'
import { CollapsibleGroup, ExpandCollapseAll } from '../ui/Collapsible.tsx'
import { AboutSection } from './AboutSection.tsx'
import { ContactSection } from './ContactSection.tsx'
import { EducationSection } from './EducationSection.tsx'
import { ExperienceSection } from './ExperienceSection.tsx'
import { LanguagesSection } from './LanguagesSection.tsx'
import { OpenSourceSection } from './OpenSourceSection.tsx'
import { PreferencesSection } from './PreferencesSection.tsx'
import { type PublicCvState } from './publicCvNotice.ts'
import { PublicationsSection } from './PublicationsSection.tsx'
import type { RepositoriesResult } from './repositoriesApi.ts'
import { FillFromCv, type FillState } from './FillFromCv.tsx'
import { SkillsSection } from './SkillsSection.tsx'
import { skillsAfterProjectChange } from './skills.ts'

export type { FillState }

interface Props {
  profile: Profile
  onChange: (profile: Profile) => void
  /** Server messages by field path, for example `experience[0].title`. */
  errors: Record<string, string>
  /** Called with the path of a field the user has just left. */
  onLeave?: (path: string) => void
  /** Whether the user has a master CV; without one the section buttons are disabled. */
  canFillFromCv: boolean
  /** Asks for one section to be read from the CV. */
  onFillSection: (section: ProfileSectionName) => void
  /** Per section: whether its request is running and the last fixed message. */
  fillState: Record<ProfileSectionName, FillState>
  /** Lists the public repositories of one code account. */
  onLoadRepositories: (
    provider: CodeProvider,
    username: string,
  ) => Promise<RepositoriesResult>
  /** The projects in the last loaded or saved profile; only these can be analysed. */
  savedProjects: OpenSourceProject[]
  /** Analyses one saved project; the page applies a success to the project and the skills. */
  onAnalyze: (project: OpenSourceProject) => Promise<AnalysisResult>
  /** Whether the public CV is live; shown next to the Contact section. */
  publicCv: PublicCvState
}

/**
 * All sections of the profile as editable fields, each a collapsible section under one "Expand all / Collapse all".
 * Holds no profile state of its own; which sections are open is display only.
 */
export function ProfileForm({
  profile,
  onChange,
  errors,
  onLeave,
  canFillFromCv,
  onFillSection,
  fillState,
  onLoadRepositories,
  savedProjects,
  onAnalyze,
  publicCv,
}: Props) {
  const set = <K extends keyof Profile>(key: K, value: Profile[K]) =>
    onChange({ ...profile, [key]: value })
  const location = profile.location ?? { city: null, canton: null }
  // Offered while a technologies field is typed in: the skills and the tags already used in positions.
  const suggestions = [
    ...profile.skills.map((s) => s.name),
    ...profile.experience.flatMap((e) => e.technologies),
  ]

  /** The "Fill from CV" control of a section that can be filled from the CV. */
  const fill = (section: ProfileSectionName) => (
    <FillFromCv
      canFillFromCv={canFillFromCv}
      onFill={() => onFillSection(section)}
      state={fillState[section]}
    />
  )

  return (
    <CollapsibleGroup>
      <div
        onBlur={(e) => {
          const path = (e.target as HTMLElement).dataset['path']
          if (path) onLeave?.(path)
        }}
      >
        <ExpandCollapseAll />
        <ContactSection
          contact={profile.contact}
          onChange={(v) => set('contact', v)}
          errors={errors}
          publicCv={publicCv}
        />

        <AboutSection
          location={location}
          workAuthorisation={profile.workAuthorisation}
          summary={profile.summary}
          onLocationChange={(v) => set('location', v)}
          onWorkAuthorisationChange={(v) => set('workAuthorisation', v)}
          onSummaryChange={(v) => set('summary', v)}
          errors={errors}
        />

        <LanguagesSection
          languages={profile.languages}
          onChange={(v) => set('languages', v)}
          errors={errors}
        />

        <ExperienceSection
          fill={fill('experience')}
          experience={profile.experience}
          onChange={(v) => set('experience', v)}
          errors={errors}
          suggestions={suggestions}
        />

        <EducationSection
          fill={fill('education')}
          education={profile.education}
          onChange={(v) => set('education', v)}
          errors={errors}
        />

        <PublicationsSection
          fill={fill('publications')}
          publications={profile.publications}
          onChange={(v) => set('publications', v)}
          errors={errors}
        />

        <OpenSourceSection
          accounts={profile.codeAccounts}
          onAccountsChange={(v) => set('codeAccounts', v)}
          projects={profile.openSourceProjects}
          onProjectsChange={(v) =>
            onChange({
              ...profile,
              openSourceProjects: v,
              skills: skillsAfterProjectChange(
                profile.skills,
                profile.openSourceProjects,
                v,
              ),
            })
          }
          saved={savedProjects}
          onAnalyze={onAnalyze}
          errors={errors}
          onLoad={onLoadRepositories}
        />

        <SkillsSection
          skills={profile.skills}
          onChange={(v) => set('skills', v)}
          errors={errors}
        />

        <PreferencesSection
          prefs={profile.preferences}
          onChange={(v) => set('preferences', v)}
          errors={errors}
        />
      </div>
    </CollapsibleGroup>
  )
}
