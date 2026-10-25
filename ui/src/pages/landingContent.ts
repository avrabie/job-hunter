/**
 * The copy of the landing page, kept apart from the markup so a wording change is a one-file edit. Every
 * capability here describes a feature that is shipped (see `docs/architecture.md` and `openspec/specs`): add a
 * card only when its feature is live, and keep the claims as narrow as the feature.
 */

export type IconName =
  'search' | 'list' | 'letter' | 'document' | 'globe' | 'link'

export interface Capability {
  icon: IconName
  title: string
  text: string
}

export interface Step {
  title: string
  text: string
}

export const HERO = {
  title: 'Find Swiss software jobs that fit you',
  lead: 'Job Hunter matches Swiss software-engineering postings to your profile, keeps track of the jobs you pick and drafts letters and CVs for you to review.',
} as const

export const CAPABILITIES: readonly Capability[] = [
  {
    icon: 'search',
    title: 'Find and match Swiss software jobs',
    text: 'Postings are collected from company job boards and matched to your profile, with the reasons for each match.',
  },
  {
    icon: 'list',
    title: 'Job tracking',
    text: 'Move the jobs you care about through Interested, Applying, Applied and Rejected. Each keeps a frozen copy of the job text, even after the ad goes offline.',
  },
  {
    icon: 'letter',
    title: 'Motivation letters',
    text: 'Claude drafts a letter from the facts in your profile. Review it, comment on each paragraph, regenerate, edit it yourself and download it as Markdown.',
  },
  {
    icon: 'document',
    title: 'CV export',
    text: 'Export your CV as PDF, HTML or Markdown, with a choice of templates, including Professional with your photo.',
  },
  {
    icon: 'globe',
    title: 'Public CV page',
    text: 'Publish your CV on a page at your own link, and unpublish it whenever you like.',
  },
  {
    icon: 'link',
    title: 'Import a job from a URL',
    text: 'Paste a link to a job ad: the job is read and added to your own job tracking, private to you.',
  },
]

export const STEPS: readonly Step[] = [
  {
    title: 'Build your profile',
    text: 'Add your experience, skills and languages. They drive your matches, your letters and your CV.',
  },
  {
    title: 'Find and track jobs',
    text: 'Look through your matches and the postings, or import a job from a link, and track the ones you want.',
  },
  {
    title: 'Draft and export',
    text: 'Draft a motivation letter and export your CV. You review everything before you send anything yourself.',
  },
]

export const PRIVACY_POINTS: readonly string[] = [
  'You review everything. Nothing is ever sent to employers automatically.',
  'Letters use only facts from your profile.',
  'Your data is yours and is not shared.',
  'Your public CV is visible only if you publish it.',
]
