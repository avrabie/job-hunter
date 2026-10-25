/** Marks a posting a user added by URL. A missing flag, as in older responses, counts as false. */
export function UserAddedBadge({ userAdded }: { userAdded?: boolean }) {
  if (userAdded !== true) return null
  return <span className="badge">Added by a user</span>
}
