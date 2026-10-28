// 場所の記録の続き: mermaid の図・英語ページ・凍結版・チェックを外した節・プレビュー。PC の Chromium だけ。
import { test, ok, BASIC } from './helpers/fixtures'
import { Locate, sourceLines } from './helpers/locate'
import { bullet, hasSection, parseRecord } from './helpers/store'

test('[mermaid] 図を選ぶと、ソースが ```mermaid のフェンスの行を指し、図は描画されたまま', async ({ fb }) => {
	const loc = new Locate(fb)
	await fb.fresh('/ja/guides/baseline/')
	await fb.enter()
	await fb.page.waitForTimeout(1500)
	const handle = await loc.openElement(fb.page.locator('main pre.mermaid').first(), { x: 20, y: 20 })
	const hit = await handle.evaluate((e) => (e as Element).tagName.toLowerCase() + '.' + (e as Element).className)
	const md = await loc.submit({ comment: 'mermaid の検証' })
	ok('記録ができる', !!md)
	if (!md) return
	const { front } = parseRecord(md)
	const lines = sourceLines(front.source)
	ok('ソースが ```mermaid のフェンス（行が取れる）', !!lines && lines.text.startsWith('```mermaid'), `${front.source} hit=${hit} 拾った要素=${bullet(md, '拾った要素')}`)
	ok('図が描画されたまま（差し替えで壊れていない）', await fb.page.evaluate(() => !!document.querySelector('main pre.mermaid svg')))
})

const verCases = [
	['英語の現行ページ', '/guides/basic-api/', 'en', 'v0.3（現行）', 'docs/src/content/docs/guides/basic-api.mdx'],
	['日本語の凍結版 0.2', '/ja/0.2/get-started/faq/', 'ja', 'v0.2（凍結版）', 'docs/src/content/docs/ja/0.2/get-started/faq.mdx'],
	['英語の凍結版 0.2', '/0.2/get-started/faq/', 'en', 'v0.2（凍結版）', 'docs/src/content/docs/0.2/get-started/faq.mdx'],
]
for (const [name, path, lang, ver, src] of verCases) {
	test(`[${name}] 言語・版・ソースのパスが正しく、場所が一意に指せる`, async ({ fb }) => {
		const loc = new Locate(fb)
		await fb.fresh(path)
		await fb.enter()
		const h = await loc.openElement(fb.page.locator('main .sl-markdown-content > p').first(), { x: 6, y: 6 })
		const md = await loc.submit({ comment: `${name}の検証` })
		ok('記録ができる', !!md)
		if (!md) return
		const { front } = parseRecord(md)
		ok(`言語が ${lang}`, bullet(md, '言語') === lang, bullet(md, '言語'))
		ok(`版が ${ver}`, bullet(md, '版') === ver, bullet(md, '版'))
		ok(`ソースのパスが ${src}`, String(front.source).startsWith(src + ':'), String(front.source))
		await loc.sourceOk(`[${name}]`, md, h)
		await loc.locationOk(`[${name}]`, md, h)
	})
}

test('チェックを外した節は記録に出ず、プレビューには各節の値が見える（全 12 節）', async ({ fb }) => {
	const loc = new Locate(fb)
	await fb.fresh(BASIC)
	await fb.enter()
	await loc.openElement(fb.page.locator('main .sl-markdown-content > p').first())
	let pv: Record<string, any> = {}
	const md = await loc.submit({
		comment: 'チェックを外す検証',
		uncheck: ['location', 'git', 'screen', 'browser'],
		previewShot: 'popover-preview-sections',
		previewChecks: async () => {
			await fb.page.evaluate(() => {
				const r = document.querySelector('katachi-feedback-root')!.shadowRoot!
				for (const k of ['source', 'location']) (r.querySelector(`#chk-${k}`)!.closest('details') as HTMLDetailsElement).open = true
			})
			await fb.page.waitForTimeout(300)
			const pre = (k: string) => fb.inUi(`.popover #chk-${k}`).evaluate((b) => b.closest('details')!.querySelector('pre')!.textContent)
			pv = { items: await fb.inUi('.popover .item').count(), source: await pre('source'), location: await pre('location'), content: await pre('content'), git: await pre('git'), page: await pre('page') }
		},
	})
	ok('プレビュー: 載せる情報の節の数', pv.items === EXPECTED_SECTIONS, String(pv.items))
	ok('プレビュー: ソースの位置に path:行 が見える', /ソース: docs\/src\/content\/docs\/ja\/guides\/basic-api\.mdx:\d+/.test(pv.source ?? ''), pv.source)
	ok('プレビュー: ページ上の場所に XPath / CSSセレクタ / テキストフラグメントURL / 近くの見出し が見える', ['XPath:', 'CSSセレクタ:', 'テキストフラグメントURL:', '近くの見出し:'].every((t) => (pv.location ?? '').includes(t)))
	ok('プレビュー: 選んだ内容・ページ・git の値が見える', (pv.content ?? '').includes('テキストの全文:') && (pv.page ?? '').includes('版: v0.3') && (pv.git ?? '').includes('commit:'))
	ok('記録ができる', !!md)
	if (!md) return
	const { front } = parseRecord(md)
	ok('外した節: ページ上の場所 / git / 画面 / ブラウザ が記録に無い', !hasSection(md, 'ページ上の場所') && !hasSection(md, 'git') && !hasSection(md, '画面') && !hasSection(md, 'ブラウザ'))
	ok('外した節: frontmatter に xpath/selector/heading/commit が無い', !('xpath' in front) && !('selector' in front) && !('heading' in front) && !('commit' in front), JSON.stringify(Object.keys(front)))
	ok('外していない節は出る（ソースの位置・ページ・選んだ内容・日時）', hasSection(md, 'ソースの位置') && hasSection(md, 'ページ') && hasSection(md, '選んだ内容') && hasSection(md, '日時'))
	ok('frontmatter に page と source は残る', !!front.page && !!front.source)
	await fb.shot('after-uncheck')
})

/** 「載せる情報」の節の数（core/protocol.ts の SECTION_KEYS）。節が増えたらここを更新する。 */
const EXPECTED_SECTIONS = 12
