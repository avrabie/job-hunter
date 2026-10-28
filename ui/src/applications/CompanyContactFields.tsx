import { FormField } from '../ui/FormField.tsx'
import type { ContactDetails } from './applicationsApi.ts'

export const CONTACT_KEYS = [
  'street',
  'postalCode',
  'city',
  'person',
  'phone',
  'email',
] as const

export type ContactKey = (typeof CONTACT_KEYS)[number]

/** The six details as the form holds them: an empty string is a field with no value (sent as blank, which clears it). */
export type ContactInput = Record<ContactKey, string>

const LABEL: Record<ContactKey, string> = {
  street: 'Street',
  postalCode: 'Postal code',
  city: 'City',
  person: 'Contact person',
  phone: 'Contact phone',
  email: 'Contact email',
}

const MAX: Record<ContactKey, number> = {
  street: 200,
  postalCode: 10,
  city: 200,
  person: 200,
  phone: 40,
  email: 254,
}

/** The form values of stored details; an unknown detail is shown empty, never as "null". */
export function contactInput(
  contact: ContactDetails | undefined,
): ContactInput {
  return {
    street: contact?.street ?? '',
    postalCode: contact?.postalCode ?? '',
    city: contact?.city ?? '',
    person: contact?.person ?? '',
    phone: contact?.phone ?? '',
    email: contact?.email ?? '',
  }
}

/** The company address and the application contact, six editable inputs. Used by the import review and the application page. */
export function CompanyContactFields({
  value,
  onChange,
}: {
  value: ContactInput
  onChange: (key: ContactKey, text: string) => void
}) {
  return (
    <>
      {CONTACT_KEYS.map((key) => (
        <FormField key={key} label={LABEL[key]}>
          {(control) => (
            <input
              {...control}
              type="text"
              inputMode={
                key === 'email' ? 'email' : key === 'phone' ? 'tel' : undefined
              }
              autoComplete="off"
              maxLength={MAX[key]}
              value={value[key]}
              onChange={(e) => onChange(key, e.target.value)}
            />
          )}
        </FormField>
      ))}
    </>
  )
}
