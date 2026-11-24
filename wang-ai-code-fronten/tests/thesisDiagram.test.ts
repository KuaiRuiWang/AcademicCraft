import assert from 'node:assert/strict'
import test from 'node:test'
import { createChapterDraft, renderThesisDiagram, renderThesisMermaid, restoreChapterDraft } from '../src/utils/thesisDiagram.ts'

const escape = (s: string) => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;')
const payload = {
  version: 1,
  source: 'flowchart LR\nA["页面"] --> B["接口"]',
  caption: '图4.1 系统架构',
  svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 360"><text>中文图表</text><script>window.executed=true</script></svg>',
}
const block = '```diagram-design\n' + JSON.stringify(payload) + '\n```'

test('embeds the diagram as an inert image and escapes captions and source text', () => {
  const result = renderThesisDiagram(JSON.stringify({ ...payload, caption: '"><img src=x onerror=alert(1)>', source: '</code><script>alert(1)</script>' }), escape)
  assert.ok(result.includes('<figure'))
  assert.ok(result.includes('data:image/svg+xml;'))
  assert.ok(result.includes('width="720" height="360"'))
  assert.ok(result.includes('<details><summary>'))
  assert.ok(result.includes('<figcaption>'))
  assert.ok(!result.includes('<script>'))
  assert.ok(!result.includes('<img src=x'))
})

test('shows a readable error for malformed or unbounded figures', () => {
  for (const content of ['not JSON', JSON.stringify({ ...payload, version: 2 }), JSON.stringify({ ...payload, svg: '<svg viewBox="0 0 900000 900000"/>' })])
    assert.ok(renderThesisDiagram(content, escape).includes('role="alert"'))
})

test('shows a legacy Mermaid figure as a lazy image with a separate caption', () => {
  const result = renderThesisMermaid(payload.source + '\n\n图4.2 系统关系图\n', '', '/app/1/thesis/chapters/6/mermaid/1', escape)
  assert.ok(result.includes('src="/app/1/thesis/chapters/6/mermaid/1"'))
  assert.ok(result.includes('loading="lazy"'))
  assert.ok(result.includes('<figcaption>图4.2 系统关系图</figcaption>'))
  assert.ok(!result.match(/<code>[^]*图4\.2[^]*<\/code>/))
})

test('editing prose preserves the exact original figure and allows explicit removal', () => {
  const original = '正文之前\n\n' + block + '\n\n正文之后'
  const draft = createChapterDraft(original)
  assert.ok(!draft.text.includes('<svg'))
  assert.equal(restoreChapterDraft(draft.text, draft.figures), original)
  assert.equal(restoreChapterDraft(draft.text.replace('正文之前', '作者修改'), draft.figures), original.replace('正文之前', '作者修改'))
  assert.ok(!restoreChapterDraft(draft.text.replace(draft.figures[0]!.marker, ''), draft.figures).includes('diagram-design'))
})
