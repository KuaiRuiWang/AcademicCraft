export type ChapterSelection = { start: number; end: number }
export const REWRITE_SELECTION_LIMIT = 4000
export const REWRITE_CONTEXT_LENGTH = 500

function markdownOffset(draft: string, offset: number) {
  // Native textareas count CRLF as one character. Keep the saved line endings outside the selection.
  for (const lineBreak of draft.matchAll(/\r\n/g)) {
    if (lineBreak.index >= offset) break
    offset++
  }
  return offset
}

export function readChapterSelection(draft: string, editor: HTMLTextAreaElement): ChapterSelection | null {
  let { selectionStart: start, selectionEnd: end } = editor
  if (start === end) return null
  if (editor.value !== draft) {
    if (editor.value !== draft.replace(/\r\n?/g, '\n')) return null
    start = markdownOffset(draft, start)
    end = markdownOffset(draft, end)
  }
  const selected = draft.slice(start, end)
  const text = selected.trim()
  if (!text) return null
  start += selected.length - selected.trimStart().length
  return { start, end: start + text.length }
}

export function readChapterCaret(draft: string, editor: HTMLTextAreaElement): number | null {
  if (editor.value === draft) return editor.selectionEnd
  if (editor.value === draft.replace(/\r\n?/g, '\n')) return markdownOffset(draft, editor.selectionEnd)
  return null
}

export function selectionIncludesProtectedContent(draft: string, selection: ChapterSelection) {
  for (const marker of draft.matchAll(/<!-- 保留配图 \d+ -->/g)) {
    if (marker.index < selection.end && marker.index + marker[0].length > selection.start) return true
  }
  for (const marker of draft.matchAll(/\[@[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}\]/g)) {
    const end = marker.index + marker[0].length
    if (marker.index < selection.end && end > selection.start
      && (selection.start > marker.index || selection.end < end)) return true
  }

  // Keep fenced code and diagrams out of a prose rewrite, even when only part of a block is selected.
  let opening: RegExpMatchArray | null = null
  for (const fence of draft.matchAll(/^[ \t]{0,3}(`{3,}|~{3,})([^\r\n]*)/gm)) {
    if (fence.index >= selection.end) break
    if (!opening) {
      opening = fence
      continue
    }
    const marks = fence[1]!
    const openingMarks = opening[1]!
    if (marks[0] !== openingMarks[0] || marks.length < openingMarks.length || fence[2]?.trim()) continue
    if (fence.index + fence[0].length > selection.start) return true
    opening = null
  }
  return opening !== null
}
