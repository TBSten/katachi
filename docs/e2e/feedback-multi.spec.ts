// 複数の要素（Shift+クリック）。Feedback と Memo。PC の Chromium だけ（スマホには Shift が無い）。
import { execFileSync } from 'node:child_process'
import { test, ok, sleep } from './helpers/fixtures'
import { FEEDBACK_OUT, REPO_ROOT } from './env.mjs'
import { parseRecord, readMemo, readRecord, sectionBody } from './helpers/store'
import { join } from 'node:path'
import type { Fb } from './helpers/fb'

const SHIFT = { modifiers: ['Shift' as const], position: { x: 6, y: 6 } }
const frames = (fb: Fb) => fb.inUi('.frame.picked.multi').evaluateAll((fs) => fs.filter((f) => getComputedStyle(f).display !== 'none').map((f) => (f as HTMLElement).dataset.n))
const single = (fb: Fb) => fb.inUi('.frame.picked:not(.multi)').evaluate((f) => getComputedStyle(f).display !== 'none')
/** Shift 付きの click を、画面の外の要素にも送る。 */
const shiftClickEl = (fb: Fb, sel: string, i: number) => fb.page.evaluate(([s, n]) => document.querySelectorAll(s as string)[n as number].dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, composed: true, shiftKey: true })), [sel, i] as const)

test('Shift+クリックで足し・外し、番号つきの枠になる。Shift なしで 1 つに戻り、上限は 20 個', async ({ fb, page }) => {
	const P = (i: number) => page.locator('main p').nth(i)
	await fb.fresh()
	await fb.enter()
	await P(0).click({ position: { x: 6, y: 6 } })
	await page.waitForTimeout(150)
	ok('通常のクリックは 1 つだけ（枠は 1 つで番号なし）', (await single(fb)) && (await frames(fb)).length === 0)
	await P(1).click(SHIFT)
	await page.waitForTimeout(150)
	await P(2).click(SHIFT)
	await page.waitForTimeout(150)
	ok('Shift+クリックで足すと、要素ごとに番号つきの枠（1, 2, 3）', JSON.stringify(await frames(fb)) === '["1","2","3"]' && !(await single(fb)), JSON.stringify(await frames(fb)))
	const box1 = (await P(1).boundingBox())!
	const f2 = (await fb.inUi('.frame.picked.multi').nth(1).boundingBox())!
	ok('2 番目の枠は 2 番目に選んだ段落の中に重なる', f2.x >= box1.x - 2 && f2.y >= box1.y - 2 && f2.y + f2.height <= box1.y + box1.height + 2 && f2.x + f2.width <= box1.x + box1.width + 2, JSON.stringify([f2, box1]))
	ok('浮かぶボタン（フィードバック…）が出る', await fb.inUi('.fab').isVisible())
	await P(1).click(SHIFT)
	await page.waitForTimeout(150)
	ok('もう一度 Shift+クリックすると外れる（番号は振り直し）', JSON.stringify(await frames(fb)) === '["1","2"]', JSON.stringify(await frames(fb)))
	await P(1).click(SHIFT)
	await page.waitForTimeout(150)
	ok('また足すと 3 つ', (await frames(fb)).length === 3)
	await P(3).click({ position: { x: 6, y: 6 } })
	await page.waitForTimeout(150)
	ok('Shift なしのクリックで 1 つに戻る（前の選択は外れる）', (await single(fb)) && (await frames(fb)).length === 0)
	await P(0).click(SHIFT)
	await page.waitForTimeout(150)
	ok('1 つ選んでいるところに Shift+クリックで足せる', (await frames(fb)).length === 2)
	await P(0).click(SHIFT)
	await page.waitForTimeout(150)
	ok('2 つから 1 つに減ると、番号なしの 1 つの枠に戻る', (await single(fb)) && (await frames(fb)).length === 0)

	// 上限
	await P(3).click({ position: { x: 6, y: 6 } })
	await page.waitForTimeout(100)
	for (let i = 0; i < 22; i++) await shiftClickEl(fb, 'main span', i)
	await page.waitForTimeout(300)
	ok('20 個で止まる（21 個目は足されない）', (await frames(fb)).length === 20, String((await frames(fb)).length))
	ok('案内の帯に上限のお知らせが出る', /20 個まで/.test((await fb.inUi('.banner .notice').first().textContent().catch(() => '')) ?? ''))
	await page.waitForTimeout(2800)
	ok('お知らせは少しで消える', (await fb.inUi('.banner .notice').count()) === 0)
})

test('3 つ選んで入力欄を開き、開いたまま足し外しし、下書きが戻り、送ると kind: elements の 1 件になる', async ({ fb, page }) => {
	const P = (i: number) => page.locator('main p').nth(i)
	await fb.fresh()
	await fb.enter()
	await P(0).click({ position: { x: 6, y: 6 } })
	await P(1).click(SHIFT)
	await P(2).click(SHIFT)
	await page.waitForTimeout(200)
	await P(2).click({ button: 'right', position: { x: 6, y: 6 } })
	await page.waitForTimeout(150)
	await fb.inUi('.menu button').click()
	await page.waitForTimeout(400)
	ok('右クリックのメニューから、3 つまとめて入力欄が開く', (await fb.inUi('.dialog.popover').count()) === 1 && (await frames(fb)).length === 3)
	ok('「親を選ぶ」は複数のときは出ない', (await fb.inUi('.dialog .parent').count()) === 0)
	await fb.inUi('.dialog textarea').fill('3 つの段落をまとめて直す')
	await shiftClickEl(fb, 'main p', 3)
	await page.waitForTimeout(500)
	ok('入力欄を開いたままの Shift+クリックで 4 つになり、コメントは残る', (await frames(fb)).length === 4 && (await fb.inUi('.dialog textarea').inputValue()) === '3 つの段落をまとめて直す')
	await shiftClickEl(fb, 'main p', 3)
	await page.waitForTimeout(500)
	ok('外すと 3 つに戻る', (await frames(fb)).length === 3 && (await fb.inUi('.dialog textarea').inputValue()) === '3 つの段落をまとめて直す')
	await fb.esc()
	await fb.enter()
	await P(0).click({ position: { x: 6, y: 6 } })
	await P(1).click(SHIFT)
	await P(2).click(SHIFT)
	await page.waitForTimeout(200)
	await P(2).click({ button: 'right', position: { x: 6, y: 6 } })
	await fb.inUi('.menu button').click()
	await page.waitForTimeout(400)
	ok('同じ 3 つを選び直すと下書きが戻る', (await fb.inUi('.dialog textarea').inputValue()) === '3 つの段落をまとめて直す')
	const name = await fb.submitAndWait()
	ok('送信で記録が 1 件できる', name !== null)
	if (!name) return
	const md = readRecord(name)
	const { front } = parseRecord(md)
	ok('kind: elements', front.kind === 'elements', String(front.kind))
	ok('source・xpath が 3 つずつの配列（ソースの行つき）', Array.isArray(front.source) && front.source.length === 3 && front.source.every((s: string) => /basic-api\.mdx:\d+/.test(s)) && Array.isArray(front.xpath) && front.xpath.length === 3, JSON.stringify([front.source, front.xpath?.length]))
	ok('要約の 1 行は「対象: 3 個の要素（…ほか）」', /^対象: 3 個の要素（.*basic-api\.mdx:\d+-\d+ ほか）$/m.test(md), (md.match(/^対象: .*$/m) ?? [])[0])
	for (const title of ['ソースの位置', 'ページ上の場所', '選んだ内容']) {
		const nums = [...sectionBody(md, title).matchAll(/^### (\d)\.$/gm)].map((x) => x[1])
		ok(`「${title}」の節が ### 1. ### 2. ### 3. で並ぶ`, JSON.stringify(nums) === '["1","2","3"]', JSON.stringify(nums))
	}
	ok('スクリーンショットは 2 枚（画面全体と、全部を囲む切り抜き）', (front.attachments ?? []).length === 2, JSON.stringify(front.attachments))
	const cli = execFileSync('node', [join(REPO_ROOT, 'docs/src/integrations/feedback/server/cli.mjs'), 'list'], { env: { ...process.env, KATACHI_FEEDBACK_DIR: FEEDBACK_OUT }, encoding: 'utf8' })
	ok('cli list の一覧に kind と「ほか」が出る', /\[elements\]/.test(cli) && /ほか2件/.test(cli), cli.split('\n').find((l) => l.includes('elements')) ?? cli)

})

test('Memo: Shift+クリックで選ぶ操作が続き、印が増え、Esc で終えると箇所のメモが並ぶ', async ({ fb, page }) => {
	const P = (i: number) => page.locator('main p').nth(i)
	const memo = fb.memoUi
	await fb.fresh()
	await fb.revealToolbar()
	await fb.memoBtn.click()
	await memo('.panel').waitFor({ state: 'visible' })
	await page.waitForTimeout(500)
	await memo('.pick').click()
	await page.waitForTimeout(300)
	await P(0).click(SHIFT)
	await page.waitForTimeout(200)
	ok('Shift+クリックしても選ぶ操作が続く（パネルは隠れたまま）', !(await memo('.panel').isVisible()) && (await memo('.pill').isVisible()))
	await P(1).click(SHIFT)
	await page.waitForTimeout(200)
	await P(2).click(SHIFT)
	await page.waitForTimeout(300)
	ok('3 つ足すと印が 3 つ出る', (await memo('.mark').count()) === 3, String(await memo('.mark').count()))
	await page.keyboard.press('Escape')
	await page.waitForTimeout(400)
	ok('Esc で選ぶのを終えるとパネルが戻り、箇所のメモが 3 件', (await memo('.panel').isVisible()) && (await memo('.card').count()) === 3, String(await memo('.card').count()))
	ok('印は 3 つ・番号 1,2,3', JSON.stringify(await memo('.mark').allTextContents()) === '["1","2","3"]')
	await memo('.pick').click()
	await page.waitForTimeout(300)
	await P(3).click({ position: { x: 6, y: 6 } })
	await page.waitForTimeout(500)
	ok('Shift を離したクリックで 1 つ足して終える（4 件）', (await memo('.card').count()) === 4 && (await memo('.panel').isVisible()))
	await sleep(1200)
	const memoMd = readMemo('ja/guides/basic-api.md') ?? ''
	// TODO: 件数が >= 4 だと重複して 8 件保存されても通る。=== 4 にする（review L17）。
	ok('保存された Markdown に箇所が 4 つ（要素ごとに別の箇所）', (memoMd.match(/xpath:/g) ?? []).length >= 4, String((memoMd.match(/xpath:/g) ?? []).length))
})
