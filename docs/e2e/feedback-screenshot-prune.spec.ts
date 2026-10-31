// 撮影の高速化（画面から遠い中身を複製しない: feedback/prune.ts）で、絵が 1 画素も変わらないこと（PC だけ）。
// 同じ画面の状態で、落とす版と落とさない版を描いて画素を比べる。window.png と element.png は同じ canvas から作るので、canvas が同じなら両方同じ。
// 確かめる場面: ページの上端・途中・下端、sticky（右の目次）・fixed（ヘッダ）、画面外の親から fixed / 絶対配置で画面内に出る子、
// 撮影から除く部品（入力欄・toolbar）を開いたまま。
import type { Page } from '@playwright/test'
import { COPY_DOCS } from './env.mjs'
import { test, ok } from './helpers/fixtures'

/** capture.ts を、dev が配る URL で読み込む（写しの docs の中なので /@fs/ で届く）。 */
const CAPTURE_URL = `/@fs${COPY_DOCS}/src/integrations/feedback/feedback/capture.ts`

type Diff = { w: number; h: number; differ: number; noise: number; sample: string }

/**
 * 落とさない版を 2 回（描くたびの揺れを測る）と、落とす版を 1 回描いて比べる。
 * differ は落とす版と落とさない版で違う画素の数、noise は落とさない版どうしで違う画素の数。
 */
async function comparePrune(page: Page): Promise<Diff> {
	return page.evaluate(async (url) => {
		const m = await import(/* @vite-ignore */ url)
		const pixels = (c: HTMLCanvasElement) => c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data
		const a = await m.renderViewport(2, { prune: false })
		const b = await m.renderViewport(2, { prune: false })
		const p = await m.renderViewport(2, { prune: true })
		const [da, db, dp] = [a, b, p].map(pixels)
		let differ = 0
		let noise = 0
		let sample = ''
		for (let i = 0; i < da.length; i += 4) {
			const d = (x: Uint8ClampedArray) => x[i] !== da[i] || x[i + 1] !== da[i + 1] || x[i + 2] !== da[i + 2] || x[i + 3] !== da[i + 3]
			if (d(db)) noise++
			if (d(dp)) {
				if (!differ) sample = `x=${(i / 4) % a.width} y=${Math.floor(i / 4 / a.width)}`
				differ++
			}
		}
		return { w: a.width, h: a.height, differ, noise, sample }
	}, CAPTURE_URL)
}

/** 画面外の親から画面内に出る要素を本文に差し込む（実際のページには無い形を、本物の配置の上で作る）。 */
async function injectEscapers(page: Page): Promise<void> {
	await page.evaluate(() => {
		const main = document.querySelector('main .sl-markdown-content') ?? document.querySelector('main')!
		const host = document.createElement('div')
		host.id = 'prune-escapers'
		host.innerHTML = `
			<div id="esc-fixed-parent" style="padding:8px;border:1px solid #888"><span>fixed の親（画面外）</span>
				<div style="position:fixed;left:24px;bottom:96px;width:220px;padding:6px;background:#ffd;color:#000;border:2px solid #c60;z-index:5">fixed の子 <b>太字</b></div>
			</div>
			<div id="esc-abs-parent" style="position:relative;padding:8px;border:1px solid #888"><span>絶対配置の親（画面外）</span>
				<div id="esc-abs-child" style="position:absolute;left:40px;width:260px;padding:6px;background:#dff;color:#000;border:2px solid #069">絶対配置の子 <i>斜体</i></div>
			</div>
			<div id="esc-sticky-col" style="height:2400px;border:1px dashed #aaa">
				<div style="position:sticky;top:120px;margin-left:300px;width:240px;padding:6px;background:#fdf;color:#000;border:2px solid #909">sticky の子 <code>code</code></div>
			</div>`
		main.prepend(host)
		// 絶対配置の子を、画面外の親から画面の中央へ出す
		const parent = document.getElementById('esc-abs-parent')!
		const child = document.getElementById('esc-abs-child')!
		child.style.top = `${innerHeight / 2 - parent.getBoundingClientRect().top}px`
	})
}

const PAGES = [
	{ path: '/ja/guides/basic-api/', at: 'top' },
	{ path: '/ja/guides/basic-api/', at: 1234 },
	{ path: '/ja/guides/basic-api/', at: 'bottom' },
	{ path: '/ja/get-started/motivation/', at: 2777 },
	{ path: '/ja/recipes/android-three-layer/', at: 1900 },
	{ path: '/ja/guides/layout/', at: 3333 },
] as const

test.describe('撮影の高速化で絵が変わらない（PC）', () => {
	test.skip(({ isMobile }) => isMobile, 'PC の画面で比べる')

	for (const { path, at } of PAGES) {
		test(`${path} の ${at} で、落とす版と落とさない版の絵が同じ`, async ({ fb, page }) => {
			// 複製のときに、本文のカスタム要素（<starlight-tabs> など）のコンストラクタが投げる（B を入れる前から。落とす版でも数は同じ）。
			// TODO: 撮影でカスタム要素のコンストラクタを動かさない（複製をカスタム要素の登録の無い文書で作る）。
			fb.allowPageErrors = true
			await fb.fresh(path)
			await page.evaluate((y) => scrollTo(0, y === 'top' ? 0 : y === 'bottom' ? document.documentElement.scrollHeight : y), at)
			await page.waitForTimeout(400)
			const d = await comparePrune(page)
			ok(`揺れが無い（落とさない版どうし ${d.noise} 画素）`, d.noise === 0, JSON.stringify(d))
			ok(`画素が同じ（${d.differ} / ${d.w * d.h} 画素が違う ${d.sample}）`, d.differ === 0, JSON.stringify(d))
			const other = fb.pageErrors.filter((e) => !/reading '(querySelectorAll|querySelector)'/.test(e))
			ok('複製のときのカスタム要素の失敗のほかに、ページエラーが出ない', other.length === 0, JSON.stringify(other))
		})
	}

	test('画面外の親から fixed・絶対配置・sticky で画面に出る子があっても、絵が同じ', async ({ fb, page }) => {
		await fb.fresh('/ja/guides/basic-api/')
		await injectEscapers(page)
		// 親を画面の上の外へ出す（sticky の列の途中まで進める）
		const y = await page.evaluate(() => document.getElementById('esc-sticky-col')!.getBoundingClientRect().top + scrollY + 900)
		await page.evaluate((v) => scrollTo(0, v), y)
		await page.evaluate(() => {
			// スクロールしたあとの位置で、絶対配置の子を画面の中央へ出し直す
			const parent = document.getElementById('esc-abs-parent')!
			document.getElementById('esc-abs-child')!.style.top = `${innerHeight / 2 - parent.getBoundingClientRect().top}px`
		})
		await page.waitForTimeout(300)
		const where = await page.evaluate(() => ['esc-fixed-parent', 'esc-abs-parent', 'esc-abs-child'].map((id) => Math.round(document.getElementById(id)!.getBoundingClientRect().top)))
		ok('親は画面の上の外、絶対配置の子は画面の中', where[0] < -50 && where[1] < -50 && where[2] > 0 && where[2] < 800, JSON.stringify(where))
		const d = await comparePrune(page)
		ok(`揺れが無い（${d.noise} 画素）`, d.noise === 0, JSON.stringify(d))
		ok(`画素が同じ（${d.differ} 画素が違う ${d.sample}）`, d.differ === 0, JSON.stringify(d))
	})

	test('画面の下の外にある親から、fixed で画面の上に出る子があっても、絵が同じ（ページの上端）', async ({ fb, page }) => {
		await fb.fresh('/ja/guides/basic-api/')
		await page.evaluate(() => {
			const main = document.querySelector('main .sl-markdown-content') ?? document.querySelector('main')!
			const parent = document.createElement('div')
			parent.id = 'far-parent'
			parent.style.cssText = 'padding:8px;border:1px solid #888'
			// fixed の子は親の箱をはみ出しても、親の scrollHeight を増やさない（はみ出しの判定では拾えない）
			parent.innerHTML = '<span>fixed の親（画面の下の外）</span><div style="position:fixed;left:320px;top:160px;width:260px;padding:6px;background:#ffd;color:#000;border:2px solid #c60;z-index:5">fixed の子 <b>太字</b></div>'
			main.append(parent)
			scrollTo(0, 0)
		})
		await page.waitForTimeout(300)
		const top = await page.evaluate(() => Math.round(document.getElementById('far-parent')!.getBoundingClientRect().top))
		ok('親は画面の下の外', top > 900, String(top))
		const d = await comparePrune(page)
		ok(`揺れが無い（${d.noise} 画素）`, d.noise === 0, JSON.stringify(d))
		ok(`画素が同じ（${d.differ} 画素が違う ${d.sample}）`, d.differ === 0, JSON.stringify(d))
	})

	test('画面外の子の margin が親の外へ相殺するブロックと、画面の上から続く段落の中の inline があっても、絵が同じ', async ({ fb, page }) => {
		await fb.fresh('/ja/guides/basic-api/')
		await page.evaluate(() => {
			const main = document.querySelector('main .sl-markdown-content') ?? document.querySelector('main')!
			const box = document.createElement('div')
			box.id = 'leak-box'
			// 子の h3 の margin-top は、padding も border も無い 2 段の div を抜けて外へ相殺する
			box.innerHTML = '<div><h3 style="margin-top:160px">margin が外へ相殺する見出し</h3><p>本文</p></div>'
			const para = document.createElement('p')
			para.id = 'long-para'
			const words = Array.from({ length: 160 }, (_, i) => (i % 9 === 0 ? `<a href="#">リンク ${i} の長い文字列</a>` : `語${i}`)).join(' ')
			para.innerHTML = words
			main.prepend(box, para)
		})
		// 相殺する箱は画面の上の外、長い段落は画面の上から画面の中へ続く位置にする
		const y = await page.evaluate(() => document.getElementById('long-para')!.getBoundingClientRect().top + scrollY + 260)
		await page.evaluate((v) => scrollTo(0, v), y)
		await page.waitForTimeout(300)
		const where = await page.evaluate(() => {
			const r = (id: string) => document.getElementById(id)!.getBoundingClientRect()
			const firstLink = document.querySelector('#long-para a')!.getBoundingClientRect()
			return { box: Math.round(r('leak-box').bottom), paraTop: Math.round(r('long-para').top), paraBottom: Math.round(r('long-para').bottom), link: Math.round(firstLink.bottom) }
		})
		ok('箱は画面の上の外、段落は画面の上の外から画面の中へ続き、その最初のリンクは画面の上の外', where.box < -50 && where.paraTop < -50 && where.paraBottom > 100 && where.link < -50, JSON.stringify(where))
		const d = await comparePrune(page)
		ok(`揺れが無い（${d.noise} 画素）`, d.noise === 0, JSON.stringify(d))
		ok(`画素が同じ（${d.differ} 画素が違う ${d.sample}）`, d.differ === 0, JSON.stringify(d))
	})

	test('入力欄と toolbar を開いたまま（送信の直前と同じ状態）でも、絵が同じ', async ({ fb, page }) => {
		await fb.fresh('/ja/guides/basic-api/')
		await page.evaluate(() => scrollTo(0, 900))
		await page.waitForTimeout(300)
		await fb.enter()
		await fb.pickEl(page.locator('main p').nth(8))
		ok('入力欄が開いている', await fb.inUi('.dialog textarea').isVisible())
		const d = await comparePrune(page)
		ok(`揺れが無い（${d.noise} 画素）`, d.noise === 0, JSON.stringify(d))
		ok(`画素が同じ（${d.differ} 画素が違う ${d.sample}）`, d.differ === 0, JSON.stringify(d))
	})
})
