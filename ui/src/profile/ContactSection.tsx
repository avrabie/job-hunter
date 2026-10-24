import { Link } from 'react-router'
import { publicCvAddress, type PublicCvState } from './publicCvNotice.ts'
import { TextInput } from './fields.tsx'
import type { Contact } from './types.ts'

/** Next to Contact: the live address with a link to the settings, or a link to them; nothing while unknown. */
function PublicCvNotice({ state }: { state: PublicCvState }) {
  if (state.state === 'live')
    return (
      <div className="notice">
        <p>
          Your public CV is live at {publicCvAddress(state.handle)}; saved
          changes show there at once.
        </p>
        <p>
          <Link to="/publish">Public CV</Link>
        </p>
      </div>
    )
  if (state.state === 'not-live')
    return (
      <p className="hint">
        <Link to="/publish">Public CV</Link>
      </p>
    )
  return null
}

/** Contact details, with a note about the public CV. */
export function ContactSection({
  contact,
  onChange,
  errors,
  publicCv,
}: {
  contact: Contact
  onChange: (contact: Contact) => void
  /** Server messages by field path, for example `contact.email`. */
  errors: Record<string, string>
  /** Whether the public CV is live; shown under the heading. */
  publicCv: PublicCvState
}) {
  return (
    <section id="contact" aria-labelledby="contact-heading">
      <h2 id="contact-heading">Contact</h2>
      <p className="hint">
        The full name is needed to export a CV.{' '}
        <Link to="/export">Export CV</Link>
      </p>
      <PublicCvNotice state={publicCv} />
      <TextInput
        label="Full name"
        value={contact.fullName}
        error={errors['contact.fullName']}
        path={'contact.fullName'}
        onChange={(fullName) => onChange({ ...contact, fullName })}
      />
      <TextInput
        label="Headline"
        value={contact.headline}
        error={errors['contact.headline']}
        path={'contact.headline'}
        onChange={(headline) => onChange({ ...contact, headline })}
      />
      <TextInput
        label="Email"
        value={contact.email}
        error={errors['contact.email']}
        path={'contact.email'}
        onChange={(email) => onChange({ ...contact, email })}
      />
      <TextInput
        label="Phone"
        value={contact.phone}
        error={errors['contact.phone']}
        path={'contact.phone'}
        onChange={(phone) => onChange({ ...contact, phone })}
      />
      <TextInput
        label="Website"
        value={contact.website}
        error={errors['contact.website']}
        path={'contact.website'}
        onChange={(website) => onChange({ ...contact, website })}
      />
      <TextInput
        label="LinkedIn"
        value={contact.linkedin}
        error={errors['contact.linkedin']}
        path={'contact.linkedin'}
        onChange={(linkedin) => onChange({ ...contact, linkedin })}
      />
    </section>
  )
}
