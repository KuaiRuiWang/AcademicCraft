const diagramFence = /^```diagram-design[^\S\r\n]*\r?\n([\s\S]*?)^```[^\S\r\n]*\r?$/gm
const captionLine = /^图[ \t]*\d+(?:[.．-]\d+)*[ \t\u3000]+\S[^\r\n]*$/

export const isThesisFigureCaption = (text: string) => captionLine.test(text.trim())

function figureMarkup(source: string, caption: string, url: string, escape: (value: string) => string, dimensions?: string) {
  const label = escape(caption)
  const image = dimensions
    ? `<img src="${escape(url)}" alt="${label}" ${dimensions} decoding="async">`
    : `<img class="thesis-mermaid-image" src="${escape(url)}" alt="${label}" loading="lazy" decoding="async">`
  const error = dimensions ? '' : '<p class="thesis-diagram-error" role="status" hidden>配图暂时无法预览，可查看图中结构。</p>'
  return `<figure class="thesis-diagram"><div class="thesis-diagram-canvas" role="region" aria-label="${label}" tabindex="0">${image}${error}</div><p class="thesis-diagram-hint">左右滚动可查看完整配图</p><details><summary>查看图中内容</summary><pre tabindex="0" role="region" aria-label="图表结构原文"><code>${escape(source)}</code></pre></details><figcaption>${label}</figcaption></figure>`
}

export function renderThesisMermaid(content: string, outsideCaption: string, imageUrl: string, escape: (value: string) => string) {
  const raw = content.trim()
  const lastNewline = raw.lastIndexOf('\n')
  const tail = raw.slice(lastNewline + 1).trim()
  const embeddedCaption = lastNewline >= 0 && isThesisFigureCaption(tail) ? tail : ''
  const source = embeddedCaption ? raw.slice(0, lastNewline).trimEnd() : raw
  return figureMarkup(source, outsideCaption || embeddedCaption || '论文配图', imageUrl, escape)
}

export function renderThesisDiagram(content: string, escape: (value: string) => string): string {
  try {
    const figure = JSON.parse(content) as { version: number; source: string; svg: string; caption: string }
    if (figure.version !== 1 || typeof figure.svg !== 'string' || typeof figure.source !== 'string' || figure.svg.length > 120000)
      throw new Error('Invalid figure')
    const box = figure.svg.match(/\bviewBox\s*=\s*["']([^"']+)["']/)?.[1]?.trim().split(/[\s,]+/).map(Number)
    if (!box || box.length !== 4 || !box.every(Number.isFinite) || box[2]! < 400 || box[2]! > 960 || box[3]! < 160 || box[3]! > 1440)
      throw new Error('Invalid figure size')
    const caption = typeof figure.caption === 'string' ? figure.caption : '论文配图'
    // SVG stays in an image document; model markup is never injected into the host DOM.
    const url = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(figure.svg)
    return figureMarkup(figure.source, caption, url, escape, `width="${box[2]}" height="${box[3]}"`)
  } catch {
    return '<p class="thesis-diagram-error" role="alert">配图无法预览，请刷新或重新生成该章节。</p>'
  }
}

export function createChapterDraft(content: string) {
  const figures: { marker: string; block: string }[] = []
  const text = content.replace(diagramFence, block => {
    const marker = `<!-- 保留配图 ${figures.length + 1} -->`
    figures.push({ marker, block })
    return marker
  })
  return { text, figures }
}

export function restoreChapterDraft(text: string, figures: { marker: string; block: string }[]): string {
  return figures.reduce((result, figure) => result.split(figure.marker).join(figure.block), text)
}
