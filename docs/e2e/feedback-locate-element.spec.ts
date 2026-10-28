// 要素を選んだときに、記録がその場所を一意に指すこと（ソースの行・XPath・セレクタ・見出し・テキストフラグメント・版・git）。
// 日本語の mdx で、段落・見出し・リスト・表・コード・Aside を 1 つずつ選ぶ。PC の Chromium だけ（ハイライトの色を画素で見る）。
import { test, ok, sleep, BASIC } from './helpers/fixtures'
import { Locate, head } from './helpers/locate'
import { bullet, parseRecord } from './helpers/store'

const elementCases: [string, string, number, { x: number; y: number }][] = [
	['段落', 'main .sl-markdown-content > p', 0, { x: 6, y: 6 }],
	['見出し', 'main .sl-markdown-content h2', 0, { x: 4, y: 4 }],
	['リスト項目', 'main .sl-markdown-content > ul > li', -1, { x: 30, y: 6 }],
	['表のセル', 'main .sl-markdown-content table tbody td', 0, { x: 6, y: 6 }],
	['コードブロック', 'main .expressive-code', 0, { x: 6, y: 6 }],
	['Aside の中の段落', 'main aside.starlight-aside .starlight-aside__content p', 0, { x: 6, y: 4 }],
	['Aside 自身', 'main aside.starlight-aside', 0, { x: 3, y: 3 }],
]
/** テキストフラグメントで見えるはずの要素（コードブロックと Aside 自身は対象外）。 */
const withFragment = new Set(['段落', '見出し', 'リスト項目', '表のセル', 'Aside の中の段落'])

for (const [name, sel, idx, pos] of elementCases) {
	test(`[ja mdx] ${name}を選ぶと、ソースの行・XPath・セレクタが同じ要素を指す`, async ({ fb, context }) => {
		const loc = new Locate(fb)
		const tag = `[${name}]`
		await fb.fresh(BASIC)
		await fb.enter()
		const target = idx === -1 ? fb.page.locator(sel).last() : fb.page.locator(sel).nth(idx)
		const handle = await loc.openElement(target, pos)
		const hit = await handle.evaluate((e) => (e as Element).tagName.toLowerCase() + '.' + [...(e as Element).classList].slice(0, 2).join('.'))
		ok(`${tag} popover が開く`, await loc.pop().isVisible(), `hit=${hit}`)
		const md = await loc.submit({ comment: `${name}の検証`, previewShot: name === '段落' ? 'popover-preview-element' : undefined })
		ok(`${tag} 記録ができる`, !!md)
		if (!md) return
		const { front } = await loc.sourceOk(tag, md, handle)
		await loc.locationOk(tag, md, handle)
		ok(`${tag} frontmatter に page/source/xpath/selector/created/commit`, ['page', 'source', 'xpath', 'selector', 'created', 'commit'].every((k) => front[k]) && front.commit === head(), JSON.stringify({ ...front, xpath: undefined }))
		ok(`${tag} 先頭が「対象:」の 1 行`, /\n---\n\n対象: docs\/src\/content\/docs\/ja\/guides\/basic-api\.mdx:\d/.test(md), md.split('\n')[md.split('\n').indexOf('---', 1) + 2])

		if (name === '段落') {
			ok('ページ: 版が v0.3（現行）', bullet(md, '版') === 'v0.3（現行）', bullet(md, '版'))
			ok('ページ: 言語が ja', bullet(md, '言語') === 'ja')
			ok('ページ: ページのソースファイルが basic-api.mdx', bullet(md, 'ページのソースファイル') === 'docs/src/content/docs/ja/guides/basic-api.mdx', bullet(md, 'ページのソースファイル'))
			ok('ランドマークが本文', bullet(md, 'ランドマーク') === '本文（main）', bullet(md, 'ランドマーク'))
			ok('git: commit が git rev-parse --short HEAD と一致', bullet(md, 'commit') === head() && front.commit === head(), `${bullet(md, 'commit')} vs ${head()}`)
			ok('git: 未コミットの変更の行がある', /^- ソースの未コミットの変更: (なし|あり)/m.test(md), bullet(md, 'ソースの未コミットの変更'))
		}
		if (name === 'リスト項目') {
			ok('見出し: 近くの見出しが ## で始まる', /^## /.test(bullet(md, '近くの見出し') ?? ''), bullet(md, '近くの見出し'))
			ok('見出しの階層がある', /h[1-6] .+/.test(bullet(md, '見出しの階層') ?? ''), bullet(md, '見出しの階層'))
		}
		if (name === '段落' || name === 'リスト項目') {
			// 見出しのアンカー URL が実在する見出しの id を指す
			const url = bullet(md, '見出しのアンカーURL')
			if (!url) ok(`見出しのアンカー(${name}): URL がある`, false, 'なし')
			else {
				const id = new URL(url).hash.slice(1)
				await fb.page.goto(url, { waitUntil: 'networkidle' })
				await sleep(800)
				const info = await fb.page.evaluate((i) => {
					const e = document.getElementById(decodeURIComponent(i))
					return e ? { tag: e.tagName, text: (e.textContent ?? '').trim() } : null
				}, id)
				const headText = (bullet(md, '近くの見出し') ?? '').replace(/^#+ /, '')
				ok(`見出しのアンカー(${name}): 実在する id を指し、見出しの文字が一致`, !!info && /^H[1-6]$/.test(info.tag) && info.text.includes(headText), `${url} -> ${JSON.stringify(info)}`)
			}
		}
		if (withFragment.has(name)) {
			const frag = bullet(md, 'テキストフラグメントURL')
			ok(`${tag} テキストフラグメントの URL がある`, !!frag, frag)
			if (frag) {
				const page2 = await context.newPage()
				await loc.fragmentHighlight(page2, `${tag}要素のテキストフラグメント`, frag, parseRecord(md).front.xpath, `el-${name.replace(/[^\p{L}\p{N}]/gu, '')}`)
				await page2.close()
			}
		}
		if (name === '段落') console.log('--- 保存例（段落）---\n' + md.slice(0, 600))
	})
}
