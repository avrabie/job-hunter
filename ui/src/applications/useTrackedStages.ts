import { useEffect, useState } from 'react'
import { useAuth } from '../auth/authContext.ts'
import { loadPostingStages, type ApplicationStage } from './applicationsApi.ts'

/** The user's application for one posting: its id and stage. */
export interface TrackedStage {
  applicationId: string
  stage: ApplicationStage
}

/**
 * The stages of the user's applications for the given postings, keyed by posting id. `undefined` while the lookup runs.
 * A failed lookup gives an empty map, so the buttons still work; the answer of the track call is authoritative.
 */
export function useTrackedStages(
  postingIds: string[],
): Map<string, TrackedStage> | undefined {
  const auth = useAuth()
  const key = postingIds.join(',')
  const [loaded, setLoaded] = useState<{
    key: string
    map: Map<string, TrackedStage>
  } | null>(null)

  useEffect(() => {
    if (!key) return
    let current = true
    void loadPostingStages(auth, key.split(',')).then((result) => {
      if (!current || result.kind === 'unauthorized') return
      const map = new Map<string, TrackedStage>()
      if (result.kind === 'loaded')
        for (const s of result.stages)
          map.set(s.postingId, {
            applicationId: s.applicationId,
            stage: s.stage,
          })
      setLoaded({ key, map })
    })
    return () => {
      current = false
    }
    // `auth` is a new object whenever the sign-in state changes; the lookup belongs to the ids only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  if (!loaded || loaded.key !== key) return undefined
  return loaded.map
}

/** The tracked stage of one posting: `undefined` while loading, `null` when not tracked. */
export function trackedOf(
  map: Map<string, TrackedStage> | undefined,
  postingId: string,
): TrackedStage | null | undefined {
  return map === undefined ? undefined : (map.get(postingId) ?? null)
}
