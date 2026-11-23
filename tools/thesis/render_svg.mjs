// Reuse Mermaid CLI's installed browser dependency, including when extracted from a JAR.
import fs from 'node:fs/promises'
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'

const [source, target, mermaidCli, kind] = process.argv.slice(2)
const require = createRequire(mermaidCli)
const { default: puppeteer } = await import(pathToFileURL(require.resolve('puppeteer')).href)
const svg = await fs.readFile(source, 'utf8')
const browser = await puppeteer.launch({ executablePath: process.env.PUPPETEER_EXECUTABLE_PATH })
try {
  const page = await browser.newPage()
  await page.setRequestInterception(true)
  page.on('request', request => request.abort())
  await page.setJavaScriptEnabled(false)
  await page.setViewport({ width: 1000, height: 1500, deviceScaleFactor: 3 })
  await page.setContent(`<html><head><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'none'"></head><body style="margin:0;background:white">${svg}</body></html>`)
  await page.evaluate(() => document.fonts.ready)
  const errors = await page.$eval('svg', (root, diagramKind) => {
    const box = root.viewBox.baseVal
    root.setAttribute('width', String(box.width))
    root.setAttribute('height', String(box.height))
    const frame = root.getBoundingClientRect()
    const findings = Array.from(root.querySelectorAll('text')).flatMap(text => {
      const bounds = text.getBoundingClientRect()
      if (bounds.left < frame.left - 1 || bounds.top < frame.top - 1 || bounds.right > frame.right + 1 || bounds.bottom > frame.bottom + 1)
        return [`Text is clipped: ${text.textContent?.slice(0, 60)}`]
      if (bounds.width > 0 && bounds.height > 0) {
        const top = document.elementFromPoint(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2)
        if (top && !text.contains(top) && top !== text)
          return [`Text is covered by a later shape; move the label clear of nodes: ${text.textContent?.slice(0, 60)}`]
      }
      return []
    })
    const nodes = new Map(Array.from(root.querySelectorAll('[data-node-id]'), node => [node.getAttribute('data-node-id'), node.getBoundingClientRect()]))
    const sequence = []
    for (const edge of root.querySelectorAll('[data-edge-id]')) {
      const connector = Array.from(edge.querySelectorAll('path,line,polyline')).sort((a, b) => b.getTotalLength() - a.getTotalLength())[0]
      if (!connector) { findings.push('Missing visible edge ' + edge.getAttribute('data-edge-id')); continue }
      const matrix = connector.getScreenCTM()
      const start = connector.getPointAtLength(0).matrixTransform(matrix)
      const end = connector.getPointAtLength(connector.getTotalLength()).matrixTransform(matrix)
      const inside = (point, bounds) => bounds && point.x >= bounds.left - 6 && point.x <= bounds.right + 6 && point.y >= bounds.top - 6 && point.y <= bounds.bottom + 6
      if (!inside(start, nodes.get(edge.getAttribute('data-from'))) || !inside(end, nodes.get(edge.getAttribute('data-to'))))
        findings.push('Edge endpoints do not attach to their declared source/target: ' + edge.getAttribute('data-edge-id'))
      sequence.push({ order: Number(edge.getAttribute('data-edge-id').slice(1)), y: (start.y + end.y) / 2 })
    }
    if (diagramKind === 'sequence') {
      sequence.sort((a, b) => a.order - b.order)
      if (sequence.some((edge, index) => index > 0 && edge.y <= sequence[index - 1].y)) findings.push('Sequence message order changed')
    }
    return findings
  }, kind)
  if (errors.length) throw new Error(errors.slice(0, 5).join('; '))
  const figure = await page.$('svg')
  if (!figure) throw new Error('No SVG figure found')
  await figure.screenshot({ path: target })
} finally {
  await browser.close()
}
