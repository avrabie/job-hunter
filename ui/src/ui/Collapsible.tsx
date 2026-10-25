import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

interface GroupApi {
  isOpen: (id: string) => boolean
  setOpen: (id: string, open: boolean) => void
  expandAll: () => void
  collapseAll: () => void
}

const GroupContext = createContext<GroupApi | null>(null)

/**
 * Holds the open state of the `Collapsible`s inside it that have an `id`, so `ExpandCollapseAll` can open or close them
 * together. "Expand all" changes the default and forgets each section's own choice; sections need not register.
 */
export function CollapsibleGroup({
  defaultOpen = false,
  children,
}: {
  defaultOpen?: boolean
  children: ReactNode
}) {
  const [all, setAll] = useState(defaultOpen)
  const [own, setOwn] = useState<Record<string, boolean>>({})

  const api = useMemo<GroupApi>(
    () => ({
      isOpen: (id) => own[id] ?? all,
      setOpen: (id, open) =>
        setOwn((prev) => (prev[id] === open ? prev : { ...prev, [id]: open })),
      expandAll: () => {
        setAll(true)
        setOwn({})
      },
      collapseAll: () => {
        setAll(false)
        setOwn({})
      },
    }),
    [all, own],
  )
  return <GroupContext.Provider value={api}>{children}</GroupContext.Provider>
}

/** The "Expand all" and "Collapse all" buttons of the enclosing `CollapsibleGroup`. */
export function ExpandCollapseAll() {
  const group = useContext(GroupContext)
  if (!group) throw new Error('ExpandCollapseAll needs a CollapsibleGroup')
  return (
    <div className="collapsible-controls">
      <button type="button" className="button--ghost" onClick={group.expandAll}>
        Expand all
      </button>
      <button
        type="button"
        className="button--ghost"
        onClick={group.collapseAll}
      >
        Collapse all
      </button>
    </div>
  )
}

/**
 * A section that opens and closes, built on `details`/`summary` so the browser gives it keyboard operation and the
 * expanded state. Open state, in order of precedence: the `open` prop (controlled), the enclosing group (when `id` is
 * set), or its own state starting at `defaultOpen`. `forceOpen` opens it whenever it turns true, for a section that
 * holds a field with an error.
 */
export function Collapsible({
  title,
  summary,
  id,
  open,
  defaultOpen = false,
  forceOpen = false,
  onOpenChange,
  className,
  children,
}: {
  title: ReactNode
  /** One line shown next to the title, such as a count or a status. */
  summary?: ReactNode
  id?: string
  open?: boolean
  defaultOpen?: boolean
  forceOpen?: boolean
  onOpenChange?: (open: boolean) => void
  className?: string
  children: ReactNode
}) {
  const group = useContext(GroupContext)
  const [ownOpen, setOwnOpen] = useState(defaultOpen)
  const grouped = group !== null && id !== undefined
  const isOpen = open ?? (grouped ? group.isOpen(id) : ownOpen)

  const change = useCallback(
    (next: boolean) => {
      if (open === undefined) {
        if (grouped) group.setOpen(id, next)
        else setOwnOpen(next)
      }
      onOpenChange?.(next)
    },
    [open, grouped, group, id, onOpenChange],
  )

  useEffect(() => {
    if (forceOpen && !isOpen) change(true)
    // Only a change of `forceOpen` opens the section; closing it afterwards stays the user's choice.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [forceOpen])

  return (
    <details
      id={id}
      className={className ? `collapsible ${className}` : 'collapsible'}
      open={isOpen}
      // The browser toggles `open` itself (click, Enter, Space, find in page); the state follows the element.
      onToggle={(e) => {
        const now = e.currentTarget.open
        if (now !== isOpen) change(now)
      }}
    >
      <summary className="collapsible__summary">
        <span className="collapsible__title">{title}</span>
        {summary !== undefined && (
          <span className="collapsible__meta">{summary}</span>
        )}
      </summary>
      <div className="collapsible__body">{children}</div>
    </details>
  )
}
