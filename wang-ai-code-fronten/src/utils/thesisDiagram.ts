const diagramFence = /^```diagram-design[^\S\r\n]*\r?\n([\s\S]*?)^```[^\S\r\n]*\r?$/gm

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
    return `<figure class="thesis-diagram"><div class="thesis-diagram-canvas" role="region" aria-label="${escape(caption)}" tabindex="0"><img src="${escape(url)}" alt="${escape(caption)}" width="${box[2]}" height="${box[3]}" decoding="async"></div><p class="thesis-diagram-hint">左右滚动可查看完整配图</p><details><summary>查看图中内容</summary><pre tabindex="0" role="region" aria-label="图表结构原文"><code>${escape(figure.source)}</code></pre></details><figcaption>${escape(caption)}</figcaption></figure>`
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
