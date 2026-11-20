// Run against Vite on 5179 with VITE_API_BASE_URL=http://127.0.0.1:18723/api.
import assert from 'node:assert/strict'
import http from 'node:http'
import fs from 'node:fs/promises'
import { createRequire } from 'node:module'
import { pathToFileURL } from 'node:url'
const toolRequire = createRequire(new URL('../../tools/thesis/package.json', import.meta.url))
const { default: puppeteer } = await import(pathToFileURL(toolRequire.resolve('puppeteer')).href)

const titles = [
  '摘要',
  'Abstract',
  '第一章 绪论',
  '第二章 相关技术',
  '第三章 需求分析',
  '第四章 系统设计',
  '第五章 系统实现',
  '第六章 系统测试',
  '第七章 总结与展望',
  '参考文献与待补材料',
]
let thesis = {
  appId: '1',
  title: '图书管理系统的设计与实现',
  revision: 'r1',
  sourceVersion: 'v1',
  status: 'ready',
  stage: '初稿已生成，可编辑章节或导出 Word',
  chapters: titles.map((title, index) => ({
    chapterNo: index + 1,
    title,
    content:
      '本系统提供图书列表和录入功能，采用 Vue 页面与接口连接。\n\n### 实现说明\n\n根据实际源码梳理业务流程，功能测试结果待验证。',
    status: 'ready',
    userEdited: 0,
    sourceVersion: 'v1',
  })),
}
let saved
let created
let generateCalls = 0
let rejectSave = false
const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', 'http://127.0.0.1:5179')
  res.setHeader('Access-Control-Allow-Credentials', 'true')
  res.setHeader('Access-Control-Allow-Headers', 'content-type')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, OPTIONS')
  if (req.method === 'OPTIONS') {
    res.writeHead(204).end()
    return
  }
  const url = new URL(req.url, 'http://localhost')
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {}
  let data = {}
  let code = 0
  let message = 'ok'
  if (url.pathname.endsWith('/user/get/login'))
    data = { id: '2', userName: '界面测试用户', userRole: 'user' }
  else if (url.pathname.endsWith('/app/get/vo'))
    data = {
      id: '1',
      appName: '图书管理',
      initPrompt: '管理图书',
      userId: '2',
      codeGenType: 'vue_project',
      generateThesis: 1,
    }
  else if (url.pathname.includes('/chatHistory/app/'))
    data = { records: [{ id: '1', messageType: 'ai', message: '应用源码已生成。' }] }
  else if (url.pathname.endsWith('/thesis/generate')) {
    generateCalls++
    thesis.status = 'ready'
    thesis.stage = '初稿已生成，可编辑章节或导出 Word'
    for (const chapter of thesis.chapters) {
      chapter.status = 'ready'
      if (!chapter.userEdited) chapter.sourceVersion = thesis.sourceVersion
    }
    data = true
  } else if (url.pathname.includes('/thesis/chapters/')) {
    saved = body
    if (rejectSave) {
      code = 50001
      message = '本章已在其他窗口修改，请刷新后核对，当前输入尚未保存'
    } else {
      const chapter = thesis.chapters.find(
        (c) => c.chapterNo === Number(url.pathname.split('/').pop()),
      )
      chapter.content = body.content
      chapter.userEdited = 1
      chapter.sourceVersion = thesis.sourceVersion
      data = true
    }
  } else if (url.pathname.endsWith('/thesis/export')) {
    res.setHeader(
      'Content-Type',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    )
    res.end('PK-fixture-download')
    return
  } else if (url.pathname.endsWith('/thesis')) data = thesis
  else if (url.pathname.endsWith('/app/add')) {
    created = body
    data = '1'
  } else if (url.pathname.includes('/list'))
    data = url.pathname.includes('page') ? { records: [], totalRow: 0 } : []
  res.setHeader('Content-Type', 'application/json')
  res.end(JSON.stringify({ code, data, message }))
})
await new Promise((resolve) => server.listen(18723, '127.0.0.1', resolve))
const browser = await puppeteer.launch({
  headless: true,
  executablePath: process.env.PUPPETEER_EXECUTABLE_PATH,
})
const page = await browser.newPage()
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
const clickText = async (text) => {
  await page.waitForFunction(
    (value) =>
      [...document.querySelectorAll('button, [role="tab"]')].some(
        (x) => x.textContent.trim() === value && !x.disabled,
      ),
    {},
    text,
  )
  const handle = await page.evaluateHandle(
    (value) =>
      [...document.querySelectorAll('button, [role="tab"]')].find(
        (x) => x.textContent.trim() === value && !x.disabled,
      ),
    text,
  )
  await handle.asElement().click()
  await handle.dispose()
}
try {
  await page.setViewport({ width: 1440, height: 1100 })
  await page.goto('http://127.0.0.1:5179/app/chat/1?view=1', { waitUntil: 'networkidle0' })
  await clickText('论文初稿')
  await page.waitForSelector('.thesis-content .markdown-content')
  await clickText('编辑本章')
  await page.$eval('#thesis-chapter-content', (el) => {
    el.value = '作者手动修订的摘要，更新论文时必须保留。'
    el.dispatchEvent(new Event('input', { bubbles: true }))
  })
  await clickText('保存本章')
  await page.waitForFunction(() =>
    document.querySelector('.thesis-content')?.textContent.includes('作者手动修订'),
  )
  assert.equal(saved.content, '作者手动修订的摘要，更新论文时必须保留。')
  assert.equal(saved.revision, 'r1')
  assert.ok(saved.originalContent.includes('图书列表'))
  assert.equal(thesis.chapters[0].userEdited, 1)
  await fs.mkdir('../tmp/thesis-ui-check', { recursive: true })
  await page.screenshot({ path: '../tmp/thesis-ui-check/desktop.png', fullPage: true })
  rejectSave = true
  await clickText('编辑本章')
  await page.$eval('#thesis-chapter-content', (el) => {
    el.value = '冲突后仍需保留的输入'
    el.dispatchEvent(new Event('input', { bubbles: true }))
  })
  await clickText('保存本章')
  await page.waitForFunction(() => document.body.textContent.includes('本章已在其他窗口修改'))
  assert.equal(
    await page.$eval('#thesis-chapter-content', (el) => el.value),
    '冲突后仍需保留的输入',
  )
  await clickText('取消编辑')
  thesis.status = 'stale'
  thesis.sourceVersion = 'v2'
  await clickText('重新读取')
  await clickText('更新未编辑章节')
  assert.equal(generateCalls, 1)
  await page.waitForFunction(() => document.body.textContent.includes('基于旧版本的手动编辑章节'))
  assert.equal(thesis.chapters[0].content, '作者手动修订的摘要，更新论文时必须保留。')
  thesis.status = 'failed'
  thesis.chapters[3].status = 'failed'
  await page.reload({ waitUntil: 'networkidle0' })
  await clickText('论文初稿')
  await clickText('重试未完成章节')
  assert.equal(generateCalls, 2)
  const downloadResponse = page.waitForResponse((response) =>
    response.url().endsWith('/thesis/export'),
  )
  await clickText('下载 Word')
  assert.ok((await downloadResponse).headers()['content-type'].includes('wordprocessingml'))
  await page.waitForFunction(() =>
    [...document.querySelectorAll('button')].some(
      (x) => x.textContent.trim() === '下载 Word' && !x.disabled,
    ),
  )
  await page.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true })
  await page.waitForNetworkIdle()
  await clickText('论文初稿')
  await page.waitForSelector('.thesis-content .markdown-content')
  await page.waitForNetworkIdle()
  await page.screenshot({ path: '../tmp/thesis-ui-check/mobile.png', fullPage: true })
  assert.ok(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
    'mobile layout must not overflow',
  )
  await page.goto('http://127.0.0.1:5179/create', { waitUntil: 'networkidle0' })
  await page.type('#project-prompt', '创建图书管理系统')
  await page.type('#thesis-title', '图书管理系统设计与实现')
  await clickText('开始生成')
  await page.waitForFunction(() => window.location.pathname === '/app/chat/1')
  assert.equal(created.generateThesis, true)
  assert.equal(created.thesisTitle, '图书管理系统设计与实现')
  assert.deepEqual(errors, [])
  console.log(
    'PASS: create, chapter editing, conflict preservation, source update, failure retry, desktop/mobile layout',
  )
} finally {
  await browser.close()
  await new Promise((resolve) => server.close(resolve))
}
