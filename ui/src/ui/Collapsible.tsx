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
  /** `fallback` is the section's own default, used until "Expand all" or "Collapse all" is pressed. */
  isOpen: (id: string, fallback: boolean) => boolean
  setOpen: (id: string, open: boolean) => void
  expandAll: () => void
  collapseAll: () => void
}

const GroupContext = createContext<GroupApi | null>(null)

/**
 * Holds the open state of the `Collapsible`s inside it that have an `id`, so `ExpandCollapseAll` can open or close them
 * together. "Expand all" changes the default and forgets each section's own choice; sections need not register.
 * Without `defaultOpen`, each section starts at its own `defaultOpen` until the first "Expand all" or "Collapse all".
 */
export function CollapsibleGroup({
  defaultOpen,
  children,
}: {
  defaultOpen?: boolean
  children: ReactNode
}) {
  const [all, setAll] = useState<boolean | null>(defaultOpen ?? null)
  const [own, setOwn] = useState<Record<string, boolean>>({})

  const api = useMemo<GroupApi>(
    () => ({
      isOpen: (id, fallback) => own[id] ?? all ?? fallback,
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
  titleAs: Title = 'span',
  titleId,
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
  /**
   * The element of the title. A heading inside the summary keeps the page outline (and a `section` labelled by it)
   * while the section is closed; `summary` allows one heading as its content.
   */
  titleAs?: 'span' | 'h2' | 'h3'
  titleId?: string
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
  // The default counts at mount only, so a section whose default depends on its content (such as "closed when it
  // holds many entries") does not snap shut while the user adds to it.
  const [initial] = useState(defaultOpen)
  const grouped = group !== null && id !== undefined
  const isOpen = open ?? (grouped ? group.isOpen(id, initial) : ownOpen)

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
        <Title id={titleId} className="collapsible__title">
          {title}
        </Title>
        {summary !== undefined && (
          <span className="collapsible__meta">{summary}</span>
        )}
      </summary>
      <div className="collapsible__body">{children}</div>
    </details>
  )
}
