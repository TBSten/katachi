// Review（dev 限定。docs への指摘を印と popup で出す）。既定は隠したまま、押すと出る、popup、解決済みの付け外し、
// 隠れた場所（閉じた details・選ばれていないタブ・畳んだコード）、ページをまたぐ前・次、壊れた指摘、外での書き換え。
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Locator } from '@playwright/test'
import { FEEDBACK_OUT } from './env.mjs'
import type { Fb } from './helpers/fb'
import { test, ok, sleep, waitFor } from './helpers/fixtures'

const FAQ = '/ja/get-started/faq/'
const FIRST = '/ja/get-started/first-architecture/'
const reviewFile = () => join(FEEDBACK_OUT, 'review', 'e2e.md')
const readReview = () => readFileSync(reviewFile(), 'utf8')

/** 指摘のファイル。行は docs が変わってもずれないよう、quote だけで探させる（行を書いたものは 1 件だけ）。 */
const SET = `---
title: e2e の指摘
---

## [p8] 段落への指摘
- file: ja/get-started/faq.mdx
- quote: 違うのは**列挙し損ねたものを許容するか

段落への指摘の本文。**太字** と \`code\`。

## [p5] 閉じた details の中への指摘
- file: ja/get-started/faq.mdx
- quote: fail-safe defaults

details の中。

## [p2] 解決済みの指摘
- file: ja/get-started/faq.mdx
- quote: 違うのは**列挙し損ねたものを許容するか
- status: resolved
- resolved-at: 2026-11-09T00:00:00.000Z
- note: 直した

## [p3] タブの中のコードの行への指摘
- file: ja/get-started/first-architecture.mdx
- quote: val projectArchitecture = architecture {

コードの行。

## [p6] ページの無いファイル
- file: docs/package.json
- line: 2

## [p6] 無いファイル
- file: ja/nope.mdx

## 読めない指摘（file が無い）

本文
`

function writeSet(text = SET) {
	mkdirSync(join(FEEDBACK_OUT, 'review'), { recursive: true })
	writeFileSync(reviewFile(), text)
}

const ui = (fb: Fb) => (sel: string): Locator => fb.page.locator(`katachi-review-root ${sel}`)
const btn = (fb: Fb) => fb.page.locator('astro-dev-toolbar button[data-app-id="katachi-review"]')
const roots = (fb: Fb) => fb.page.evaluate(() => document.querySelectorAll('katachi-review-root').length)

async function toggle(fb: Fb) {
	await fb.revealToolbar()
	await fb.act(btn(fb))
	await sleep(600)
}
async function openRow(fb: Fb, text: string) {
	const u = ui(fb)
	// スマホは popup（シート）を開いているあいだ一覧の札を隠すので、先に閉じる。
	if (fb.mobile && (await u('.pop').isVisible())) await fb.act(u('.pop .x'))
	// popup を開くと一覧は畳まれるので、畳まれていれば開いてから押す。
	if ((await u('.panel').getAttribute('class'))?.includes('is-folded')) await fb.act(u('.panel .fold'))
	await fb.act(u('.row').filter({ hasText: text }).first())
	await u('.pop:not([hidden])').waitFor({ state: 'visible', timeout: 5000 })
	await sleep(300)
}
const popTitle = (fb: Fb) => ui(fb)('.pop .pop-title').textContent()

test('既定は隠したまま。押すと印と一覧が出て、もう一度押すと全部消える。出したままページを読み直しても出し続ける', async ({ fb }) => {
	writeSet()
	await fb.fresh(FAQ)
	ok('最初は何も出ていない', (await roots(fb)) === 0)
	await toggle(fb)
	const u = ui(fb)
	ok('押すと出る', (await roots(fb)) === 1)
	ok('一覧にこのページの未解決の指摘（解決済みは隠す）', /このページ 2 ·/.test((await u('.sum').textContent()) ?? ''), (await u('.sum').textContent()) ?? '')
	ok('印が出る', (await u('.pin').count()) === 2)
	ok('読めない指摘・無いファイルは一覧の下に畳んで出す', /読めない指摘・ファイル 2 件/.test((await u('.errors summary').textContent()) ?? ''))
	await fb.shot('review-1-on')
	await fb.page.reload({ waitUntil: 'networkidle' })
	await sleep(1500)
	ok('読み直しても出し続ける（タブの中で覚えている）', (await roots(fb)) === 1)
	await toggle(fb)
	ok('もう一度押すと全部消える', (await roots(fb)) === 0)
	await fb.page.reload({ waitUntil: 'networkidle' })
	await sleep(1500)
	ok('隠したあとは読み直しても出ない', (await roots(fb)) === 0)
})

test('印を押すと popup。本文・何件め / 総数・前と次。× で閉じる', async ({ fb }) => {
	writeSet()
	await fb.fresh(FAQ)
	await toggle(fb)
	const u = ui(fb)
	await openRow(fb, '段落への指摘')
	ok('見出し', (await popTitle(fb)) === '段落への指摘')
	ok('本文の書式（太字・code）', (await u('.pop .md strong').textContent()) === '太字' && (await u('.pop .md code').textContent()) === 'code')
	ok('右下に 何件め / 総数（ページの無いファイル・無いファイルも数える）', /^\d+ \/ 5$/.test((await u('.pop .count').textContent()) ?? ''), (await u('.pop .count').textContent()) ?? '')
	const box = await u('.box.is-active').boundingBox()
	const para = await fb.page.locator('main p', { hasText: '違うのは' }).first().boundingBox()
	ok('指摘の段落を枠で光らせる', !!box && !!para && Math.abs(box.y + 3 - para.y) < 4 && Math.abs(box.height - 6 - para.height) < 4, JSON.stringify({ box, para }))
	const pop = (await u('.pop').boundingBox())!
	const vp = fb.page.viewportSize()!
	ok('popup は画面に収まり、高さに上限がある', pop.y >= 0 && pop.y + pop.height <= vp.height + 1 && pop.height <= Math.min(vp.height * 0.6, 520) + 2, JSON.stringify(pop))
	if (fb.mobile) ok('スマホは下からのシート（幅いっぱい）', Math.abs(pop.width - vp.width) < 2 && Math.abs(pop.y + pop.height - vp.height) < 2, JSON.stringify(pop))
	await fb.shot('review-2-pop')
	// 「開く」は Astro の dev サーバーの「エディタで開く」を呼ぶ（エディタは起こさないよう、ここでは横取りする）。
	let opened = ''
	await fb.page.route('**/__open-in-editor?**', (r) => { opened = decodeURIComponent(new URL(r.request().url()).searchParams.get('file') ?? ''); return r.fulfill({ status: 200, body: '' }) })
	await fb.act(u('.pop .edit'))
	ok('開く: 指摘のファイルを今の行で開く', await waitFor(async () => /\/docs\/src\/content\/docs\/ja\/get-started\/faq\.mdx:\d+:1$/.test(opened)), opened)
	await fb.page.unroute('**/__open-in-editor?**')
	await fb.act(u('.pop .nav .btn').nth(1))
	await sleep(500)
	ok('次へ進む', (await popTitle(fb)) === '閉じた details の中への指摘')
	await fb.act(u('.pop .nav .btn').nth(0))
	await sleep(500)
	ok('前へ戻る', (await popTitle(fb)) === '段落への指摘')
	if (!fb.mobile) {
		await fb.page.keyboard.press('ArrowRight')
		await sleep(400)
		ok('→ で次', (await popTitle(fb)) === '閉じた details の中への指摘')
		await fb.page.keyboard.press('Escape')
		await sleep(300)
		ok('Esc で popup だけ閉じる（アプリは出たまま）', (await u('.pop').isHidden()) && (await roots(fb)) === 1)
		await openRow(fb, '段落への指摘')
	}
	// docs を直すと Astro がページを読み直す。開いていた指摘はそのまま開き直す。閉じたあとは開かない。
	await fb.page.reload({ waitUntil: 'networkidle' })
	await u('.pop:not([hidden])').waitFor({ state: 'visible', timeout: 8000 })
	ok('読み直しても同じ指摘を開いたまま', (await popTitle(fb)) === '段落への指摘')
	await fb.act(u('.pop .x'))
	await sleep(300)
	ok('× で閉じる', await u('.pop').isHidden())
	await fb.page.reload({ waitUntil: 'networkidle' })
	await sleep(1500)
	ok('閉じたあとに読み直すと開かない', (await roots(fb)) === 1 && (await u('.pop').isHidden()))
})

test('解決済みにする → ファイルに書き戻して次へ進む → 戻すと元のファイルに戻る。連打しても 1 件だけ', async ({ fb }) => {
	writeSet()
	await fb.fresh(FAQ)
	await toggle(fb)
	const u = ui(fb)
	await openRow(fb, '段落への指摘')
	await u('.pop .act input').fill('言い回しを直した')
	const b = (await u('.pop .act .btn.primary').boundingBox())!
	// 3 回すばやく押す（次の指摘まで解決済みにしない）。
	for (let i = 0; i < 3; i++) {
		await fb.page.mouse.click(b.x + 10, b.y + 10)
		await sleep(80)
	}
	ok('ファイルに status と一言が書かれる', await waitFor(() => readReview().includes('- quote: 違うのは**列挙し損ねたものを許容するか\n- status: resolved\n- resolved-at: ')), readReview().slice(0, 400))
	ok('一言', readReview().includes('- note: 言い回しを直した'))
	await sleep(800)
	ok('解決済みになったのは 1 件だけ（元の 1 件 + 1）', (readReview().match(/- status: resolved/g) ?? []).length === 2)
	ok('次の指摘へ進む', (await popTitle(fb)) === '閉じた details の中への指摘')
	ok('一覧と印から消える', /このページ 1 ·/.test((await u('.sum').textContent()) ?? ''))
	const tb = (await u('.toast').boundingBox())!
	const nb = (await u('.pop .act .btn.primary').boundingBox())!
	ok('トーストが次の指摘の「解決済みにする」を覆わない', tb.y + tb.height <= nb.y || tb.y >= nb.y + nb.height || tb.x + tb.width <= nb.x || tb.x >= nb.x + nb.width, JSON.stringify({ tb, nb }))
	await fb.act(u('.toast .btn'))
	ok('トーストの「戻す」で元のファイルに戻る', await waitFor(() => readReview() === SET))
	await sleep(500)
	ok('一覧にも戻る', /このページ 2 ·/.test((await u('.sum').textContent()) ?? ''))
	// 解決済みも出すと、解決済みの指摘を開いて戻せる
	if (fb.mobile && (await u('.pop').isVisible())) await fb.act(u('.pop .x'))
	if ((await u('.panel').getAttribute('class'))?.includes('is-folded')) await fb.act(u('.panel .fold'))
	await fb.act(u('.filters input[type=checkbox]'))
	await sleep(400)
	await openRow(fb, '解決済みの指摘')
	ok('解決済みの印と一言', /解決済み/.test((await u('.pop .resolved-line').textContent()) ?? '') && /直した/.test((await u('.pop .resolved-line').textContent()) ?? ''))
	await fb.act(u('.pop .act .btn'))
	ok('「戻す」でファイルから解決済みの行が消える', await waitFor(() => (readReview().match(/- status: resolved/g) ?? []).length === 0))
})

test('閉じた details・選ばれていないタブ・畳んだコードの中の指摘は、開くと見えるようにして光らせる', async ({ fb }) => {
	writeSet()
	await fb.fresh(FAQ)
	await toggle(fb)
	const u = ui(fb)
	const details = fb.page.locator('main details', { hasText: 'fail-safe defaults' }).first()
	ok('最初は details が閉じている', !(await details.evaluate((d) => (d as HTMLDetailsElement).open)))
	await openRow(fb, '閉じた details の中への指摘')
	ok('開くと details が開く', await details.evaluate((d) => (d as HTMLDetailsElement).open))
	const box = await u('.box.is-active').boundingBox()
	const p = await fb.page.locator('main p', { hasText: 'fail-safe defaults' }).first().boundingBox()
	ok('details の中の段落を光らせる', !!box && !!p && Math.abs(box.y + 3 - p.y) < 4, JSON.stringify({ box, p }))

	await fb.fresh(FIRST)
	await openRow(fb, 'タブの中のコードの行への指摘')
	const line = fb.page.locator('.ec-line', { hasText: 'val projectArchitecture = architecture {' }).first()
	ok('コードの行が見えている（タブを選び、畳んだコードを開く）', await line.evaluate((l) => l.checkVisibility() && !l.closest('.ec-collapse--collapsed')))
	const lb = (await line.boundingBox())!
	const ab = (await u('.box.is-active').boundingBox())!
	ok('コードの 1 行だけを光らせる', Math.abs(ab.y + 3 - lb.y) < 4 && ab.height < lb.height + 12, JSON.stringify({ ab, lb }))
	const vp = fb.page.viewportSize()!
	ok('その行が画面に入るようスクロールする', lb.y > 0 && lb.y < vp.height, JSON.stringify(lb))
	await fb.shot('review-3-code-line')

	// 印（隠れたタブを囲む枠のそば）から開いても、タブを開いた先の行までスクロールする。
	// Starlight は選んだタブを覚えているので、忘れさせてから読み直す（行が選ばれていないタブに隠れた状態に戻す）。
	// 開いたままだと読み直したあとに開き直すので、先に閉じる。
	await fb.act(u('.pop .x'))
	await fb.page.evaluate(() => Object.keys(localStorage).filter((k) => k.startsWith('starlight-synced-tabs')).forEach((k) => localStorage.removeItem(k)))
	await fb.fresh(FIRST)
	ok('タブを忘れさせると、行はまた隠れている', !(await line.evaluate((l) => l.checkVisibility())))
	await u('.pin').first().waitFor({ state: 'visible', timeout: 5000 })
	// スマホの一覧は下に重なるので、畳んでから印を押す。
	if (fb.mobile && !(await u('.panel').getAttribute('class'))?.includes('is-folded')) await fb.act(u('.panel .fold'))
	await fb.act(u('.pin').first())
	await u('.pop:not([hidden])').waitFor({ state: 'visible', timeout: 5000 })
	await sleep(400)
	const lb2 = (await line.boundingBox())!
	ok('印から開いても、その行が画面に入る', lb2.y > 0 && lb2.y < vp.height, JSON.stringify(lb2))
})

test('次のページへ: ページを移って popup が開く。ページの無いファイルは一覧から開ける', async ({ fb }) => {
	writeSet()
	await fb.fresh(FAQ)
	await toggle(fb)
	const u = ui(fb)
	await openRow(fb, '閉じた details の中への指摘')
	ok('次はほかのページ', (await u('.pop .nav .btn').nth(1).textContent()) === '次のページ ›')
	await fb.act(u('.pop .nav .btn').nth(1))
	await fb.page.waitForURL(/first-architecture/)
	await u('.pop:not([hidden])').waitFor({ state: 'visible', timeout: 8000 })
	ok('移った先で popup が開く', (await popTitle(fb)) === 'タブの中のコードの行への指摘')
	await fb.act(u('.pop .nav .btn').nth(1))
	await sleep(500)
	ok('ページの無いファイルの指摘も開ける（ページは移らない）', (await popTitle(fb)) === 'ページの無いファイル' && /first-architecture/.test(fb.page.url()))
	ok('ページではないことを伝える', /サイトのページではありません/.test((await u('.pop .info').textContent()) ?? ''))
})

test('`#review=<id>` のリンクで来ると、隠していても出してその指摘を開く。同じページの中のリンクでも開く', async ({ fb }) => {
	writeSet()
	await fb.fresh(FAQ)
	await toggle(fb)
	const u = ui(fb)
	await openRow(fb, '閉じた details の中への指摘')
	const id = (await u('.pop').getAttribute('data-id'))!
	// スマホのシートは toolbar に重なるので、閉じてから隠す。
	if (fb.mobile) await fb.act(u('.pop .x'))
	await toggle(fb)
	ok('隠した', (await roots(fb)) === 0)
	await fb.fresh(`${FAQ}#review=${encodeURIComponent(id)}`)
	await u('.pop:not([hidden])').waitFor({ state: 'visible', timeout: 8000 })
	ok('リンクの指摘が開く', (await popTitle(fb)) === '閉じた details の中への指摘')
	ok('アドレスから #review= を消す（読み直しても開き直さない）', !fb.page.url().includes('#review='))
	await fb.page.evaluate(() => { location.hash = '#review=e2e/nope' })
	ok('無い指摘は知らせる', await waitFor(async () => /見つかりません/.test((await u('.toast').textContent().catch(() => '')) ?? '')))
})

test('外で指摘のファイルが書き換わったあとに解決済みにすると、読み直して知らせる（壊れない）', async ({ fb }) => {
	writeSet()
	await fb.fresh(FAQ)
	await toggle(fb)
	const u = ui(fb)
	await openRow(fb, '段落への指摘')
	writeSet('## 別の指摘\n- file: ja/get-started/faq.mdx\n- quote: fail-safe defaults\n')
	await fb.act(u('.pop .act .btn.primary'))
	ok('トーストで知らせる', await waitFor(async () => /無くなっていました/.test((await u('.toast').textContent().catch(() => '')) ?? '')))
	ok('読み直した指摘が出る', await waitFor(async () => /このページ 1 ·/.test((await u('.sum').textContent()) ?? '')))
	ok('popup は閉じる', await u('.pop').isHidden())
	ok('ファイルはそのまま', readReview().startsWith('## 別の指摘'))
})

test('指摘のファイルが無くても開ける（空の案内）', async ({ fb }) => {
	await fb.fresh(FAQ)
	await toggle(fb)
	const u = ui(fb)
	ok('空の案内', /指摘がありません/.test((await u('.panel-body').textContent()) ?? ''))
	ok('印は無い', (await u('.pin').count()) === 0)
	await toggle(fb)
})
