// 記録の形（schema 1）・Markdown のコピー・古さ（stale）。PC の Chromium だけ（clipboard と WebSocket の差し止めを使う）。
import { appendFileSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { test, ok } from './helpers/fixtures'
import { COPY_DOCS } from './env.mjs'
import { files, parseRecord, readRecord } from './helpers/store'

test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

type Kind = 'element' | 'text' | 'page' | 'site'
const REQUIRED = ['schema', 'id', 'kind', 'page', 'lang', 'created', 'omitted', 'commit', 'dirty', 'stale']

for (const kind of ['element', 'text', 'page', 'site'] as Kind[]) {
	test(`${kind === 'element' ? '要素' : kind === 'text' ? 'テキスト' : kind === 'page' ? 'ページ' : 'サイト'}を送ると schema 1 の記録になり、コピーした文と保存の文が（サーバー分を除いて）一致する`, async ({ fb, page }) => {
		const { inUi } = fb
		await fb.fresh()
		await fb.enter()
		if (kind === 'element') {
			await fb.para.click({ position: { x: 6, y: 6 } })
			await page.waitForTimeout(300)
			await inUi('.fab').click()
		} else if (kind === 'text') {
			await fb.selectRange(0, 12)
			await page.evaluate(() => document.dispatchEvent(new Event('selectionchange')))
			await page.waitForTimeout(200)
			const b = await page.evaluate(() => {
				const r = getSelection()!.getRangeAt(0).getBoundingClientRect()
				return { x: r.x + r.width / 2, y: r.y + r.height / 2 }
			})
			await page.mouse.move(b.x, b.y)
			await page.mouse.down()
			await page.mouse.up()
			await page.waitForTimeout(300)
			await page.mouse.dblclick(b.x - 10, b.y)
			await page.waitForTimeout(500)
			await inUi('.fab').click()
		} else {
			await inUi('.about').click()
			if (kind === 'site') await inUi('input[value=site]').check()
		}
		await page.waitForTimeout(300)
		await inUi('.popover textarea').fill(`# ${kind} の確認\n本文 ${kind}`)
		if (kind === 'element') {
			await inUi('.popover details.info > summary').click()
			for (const k of ['browser', 'state']) await inUi(`#chk-${k}`).uncheck()
		}
		await inUi('.popover button.copy').click()
		await page.waitForTimeout(300)
		const copied = await page.evaluate(() => navigator.clipboard.readText())
		const before = files().length
		await inUi('.popover button.primary').click()
		await fb.waitSaved(before)
		ok('記録ができる', files().length === before + 1)
		const file = files().at(-1)!
		const saved = readRecord(file)
		const { front, body } = parseRecord(saved)
		ok('schema 1 の frontmatter（id はファイル名）', front.schema === 1 && front.kind === kind && front.id === file.replace('.md', ''), JSON.stringify(front).slice(0, 160))
		ok('キーがそろう', REQUIRED.every((k) => k in front), REQUIRED.filter((k) => !(k in front)).join(','))
		if (kind === 'element' || kind === 'text') ok('source/xpath/selector がある', !!front.source && !!front.xpath && !!front.selector)
		ok('案内の行', body.includes('案内: katachi の docs サイト'))
		ok('コメント中の # をエスケープ', body.includes(`# \\# ${kind}`))
		ok('サイトの状態の節（要素のときは外したので無い）', kind === 'element' ? !body.includes('## サイトの状態') : body.includes('## サイトの状態') && body.includes('- テーマ:'))
		ok('ビルドの節（Astro・Starlight・Node・起動・ハッシュ）', ['- Astro:', '- Starlight:', '- Node:', '- dev サーバーの起動:', '- 統合のハッシュ:'].every((s) => body.includes(s)))
		if (kind === 'element') ok('omitted と「載せなかった情報」の行', front.omitted.join() === 'state,browser' && body.includes('（載せなかった情報: サイトの状態、ブラウザ）'), JSON.stringify(front.omitted))
		// コピーと保存: サーバーが足す分（id・commit・dirty・stale・ビルド・git・日時・注意）を除いて同じ
		const strip = (md: string) => md.replace(/^(id|commit|dirty|stale|attachments): .*\n/gm, '').replace(/\n## ビルド\n[\s\S]*?(?=\n## |$)/, '').replace(/\n## git\n[\s\S]*?(?=\n## |$)/, '').replace(/\n## スクリーンショット\n[\s\S]*?(?=\n## |$)/, '').replace(/^created: .*\n/m, '').replace(/^- 日時: .*\n/m, '').replace(/^注意: .*\n\n/m, '').replace(/\s+$/gm, '').trimEnd()
		ok('コピーの文 = 保存の文（サーバー分を除く）', !!copied && strip(copied) === strip(saved), copied ? '' : 'clipboard 空')
		if (kind === 'element') ok('コピーに git・ビルドが入らない', !copied.includes('## git') && !copied.includes('## ビルド') && !/^id:/m.test(copied))
	})
}

test('ページを開いたあとにソースの mdx が変わると、記録は stale: true で注意の行が付く。開き直せば false', async ({ fb, page }) => {
	const mdx = join(COPY_DOCS, 'src/content/docs/ja/guides/basic-api.mdx')
	let blockReload = false
	// Vite の HMR による自動リロードを止められるよう、WebSocket を中継する
	await page.routeWebSocket(/.*/, (ws) => {
		const server = ws.connectToServer()
		ws.onMessage((m) => server.send(m))
		server.onMessage((m) => {
			if (blockReload && /full-reload|"type":"update"/.test(String(m))) return
			ws.send(m)
		})
	})
	await fb.fresh()
	await fb.enter()
	await fb.para.click({ position: { x: 6, y: 6 } })
	await page.waitForTimeout(300)
	await fb.inUi('.fab').click()
	await fb.inUi('.popover textarea').fill('stale の確認')
	const orig = readFileSync(mdx, 'utf8')
	blockReload = true
	try {
		appendFileSync(mdx, '\n')
		await page.waitForTimeout(1500)
		const before = files().length
		await fb.inUi('.popover button.primary').click()
		await fb.waitSaved(before)
	} finally {
		writeFileSync(mdx, orig)
		blockReload = false
	}
	const st = parseRecord(readRecord(files().at(-1)!))
	ok('stale: true（ページを開いたあとに mdx が変わった）', st.front.stale === true, `stale=${st.front.stale}`)
	ok('本文に注意の 1 行', st.body.includes('注意: ページを開いた後にソースのファイルが変わっています'))
	await page.waitForTimeout(1500)
	await fb.fresh()
	await fb.enter()
	await fb.para.click({ position: { x: 6, y: 6 } })
	await page.waitForTimeout(300)
	await fb.inUi('.fab').click()
	await fb.inUi('.popover textarea').fill('stale でない確認')
	const before2 = files().length
	await fb.inUi('.popover button.primary').click()
	await fb.waitSaved(before2)
	ok('stale: false（開き直した後）', parseRecord(readRecord(files().at(-1)!)).front.stale === false)
})
