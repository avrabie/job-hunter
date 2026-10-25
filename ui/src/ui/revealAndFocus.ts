/**
 * Opens every closed `details` around [el] and focuses it. Content of a closed `details` cannot take focus, so the
 * "focus the first invalid field" step of a form calls this instead of `el.focus()`; the `toggle` events that follow
 * bring the sections' state in line.
 */
export function revealAndFocus(el: HTMLElement) {
  for (
    let d = el.closest('details');
    d;
    d = d.parentElement?.closest('details') ?? null
  ) {
    if (!d.open) d.open = true
  }
  el.focus()
}
