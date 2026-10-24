/**
 * The paragraph rule of the letters (design decision 10): split on one or more blank lines (a line with only
 * whitespace counts as blank), trim each part, drop empty parts. The server uses the same rule for comment numbers;
 * `paragraphs.cases.json` is the case table both sides test against.
 */
export function paragraphs(text: string): string[] {
  return text
    .split(/(?:\r?\n[^\S\r\n]*){2,}/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
}
