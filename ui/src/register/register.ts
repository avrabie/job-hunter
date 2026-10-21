export interface Registration {
  email: string
  password: string
  firstName?: string
  lastName?: string
}

export type RegisterResult =
  | { kind: 'created' }
  | { kind: 'taken' }
  | { kind: 'invalid'; fields: Record<string, string> }
  | { kind: 'unavailable' }

/**
 * Creates an account through the auth service. The password goes straight into the request body and is not kept,
 * logged or put in any URL; error responses are mapped to a result without the response body being shown.
 */
export async function register(input: Registration): Promise<RegisterResult> {
  let response: Response
  try {
    response = await fetch('/auth/api/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  } catch {
    return { kind: 'unavailable' }
  }
  if (response.status === 201) return { kind: 'created' }
  if (response.status === 409) return { kind: 'taken' }
  if (response.status === 400) {
    try {
      const body = (await response.json()) as {
        fields?: Record<string, string>
      }
      return { kind: 'invalid', fields: body.fields ?? {} }
    } catch {
      return { kind: 'invalid', fields: {} }
    }
  }
  return { kind: 'unavailable' }
}
