// 見た目の崩れ: 長い field 名・ダークテーマ・狭い窓のボタン・popover と下端の帯の重なり。
import { test, ok } from './helpers/fixtures'
import type { Fb } from './helpers/fb'

const LONG = '密度の高さ（とても長いフィールド名を入れた場合の折り返し確認用の名前）'

async function openMemo(fb: Fb) {
	await fb.revealToolbar()
	await fb.act(fb.memoBtn)
	await fb.memoUi('.panel').waitFor({ state: 'visible', timeout: 5000 })
	await fb.memoUi('.note-text').first().waitFor({ state: 'visible', timeout: 5000 })
}

test('Memo: field の名前が長くても、入力欄と × が panel の中に収まる', async ({ fb }) => {
	const ui = fb.memoUi
	await fb.fresh()
	await openMemo(fb)
	await fb.act(ui('.page-note .add-field'))
	await ui('.page-note .add-input').fill(LONG)
	await ui('.page-note .add-input').press('Enter')
	await fb.page.waitForTimeout(300)
	const panel = (await ui('.panel').boundingBox())!
	const inside = (b: { x: number; width: number }) => b.x >= panel.x - 1 && b.x + b.width <= panel.x + panel.width + 1
	const inputs = ui('.page-note .field input')
	const drops = ui('.page-note .field-drop')
	ok('field が 2 つ（読みやすさ + 長い名前）', (await inputs.count()) === 2)
	for (let i = 0; i < (await inputs.count()); i++) {
		ok(`入力欄 ${i + 1} が panel の中`, inside((await inputs.nth(i).boundingBox())!), JSON.stringify([await inputs.nth(i).boundingBox(), panel]))
		ok(`× ${i + 1} が panel の中`, inside((await drops.nth(i).boundingBox())!), JSON.stringify([await drops.nth(i).boundingBox(), panel]))
	}
	const label = (await ui('.page-note .field-label').nth(1).boundingBox())!
	ok('長いラベルは panel の幅を超えない', inside(label), JSON.stringify(label))
})

test('ダークテーマ: Feedback のダイアログと Memo の panel が白のままにならない', async ({ fb, page }) => {
	await fb.fresh()
	// 開いた時点で html に inline の color-scheme が付いている（テーマの初期化）ので、外してから data-theme を替える。
	await page.evaluate(() => { document.documentElement.style.removeProperty('color-scheme'); document.documentElement.dataset.theme = 'dark' })
	await page.waitForTimeout(200)
	const scheme = await page.evaluate(() => `${getComputedStyle(document.documentElement).colorScheme} theme=${document.documentElement.dataset.theme} attr=${document.documentElement.getAttribute('data-theme')} style=${document.documentElement.getAttribute('style')}`)
	ok('ページの color-scheme が dark（Starlight）', scheme.startsWith('dark'), scheme)
	const luminance = (css: string) => {
		const m = /rgba?\((\d+), (\d+), (\d+)/.exec(css)
		return m ? 0.2126 * +m[1]! + 0.7152 * +m[2]! + 0.0722 * +m[3]! : 255
	}
	await fb.enter()
	await fb.pickEl(fb.para)
	const dialogBg = await fb.inUi('.dialog').evaluate((e) => getComputedStyle(e).backgroundColor)
	ok('Feedback のダイアログの背景が暗い', luminance(dialogBg) < 100, dialogBg)
	const textBg = await fb.inUi('.dialog textarea').evaluate((e) => getComputedStyle(e).backgroundColor)
	ok('コメント欄の背景も暗い', luminance(textBg) < 100, textBg)
	await fb.esc()
	await openMemo(fb)
	const panelBg = await fb.memoUi('.panel').evaluate((e) => getComputedStyle(e).backgroundColor)
	ok('Memo の panel の背景が暗い', luminance(panelBg) < 100, panelBg)
	// ライトに戻すと明るくなる（テーマの切り替えに追従する）
	await page.evaluate(() => { document.documentElement.dataset.theme = 'light' })
	await page.waitForTimeout(300)
	const light = await fb.memoUi('.panel').evaluate((e) => getComputedStyle(e).backgroundColor)
	ok('ライトに戻すと panel が明るい', luminance(light) > 200, light)
})

test('窓幅 400px: ダイアログの下のボタンの文字が折り返さない', async ({ fb, page }) => {
	test.skip(fb.mobile, 'PC の列（窓幅を狭める）')
	await page.setViewportSize({ width: 400, height: 800 })
	await fb.fresh()
	await fb.enter()
	await fb.pickEl(fb.para)
	const boxes = await fb.inUi('.dialog .actions button').evaluateAll((bs) => bs.map((b) => {
		const r = b.getBoundingClientRect()
		return { text: b.textContent, height: r.height, lines: new Set(Array.from((() => { const range = document.createRange(); range.selectNodeContents(b); return range.getClientRects() })()).map((q) => Math.round(q.top))).size }
	}))
	ok('ボタンが 2 つ以上ある', boxes.length >= 2, JSON.stringify(boxes))
	for (const b of boxes) ok(`「${b.text}」が 1 行`, b.lines === 1 && b.height < 56, JSON.stringify(b))
	await fb.esc()
})

test('popover が下端の案内の帯を覆わない', async ({ fb, page }) => {
	test.skip(fb.mobile, 'PC の列（popover）')
	await fb.fresh()
	await page.evaluate(() => {
		const r = document.querySelector('main p')!.getBoundingClientRect()
		scrollBy(0, r.top - 400)
	})
	await fb.enter()
	await fb.pickEl(fb.para)
	const pop = (await fb.inUi('.dialog.popover').boundingBox())!
	const banner = (await fb.inUi('.banner').boundingBox())!
	const overlap = pop.x < banner.x + banner.width && pop.x + pop.width > banner.x && pop.y < banner.y + banner.height && pop.y + pop.height > banner.y
	ok('popover と帯が重ならない', !overlap, JSON.stringify({ pop, banner }))
	await fb.esc()
})
