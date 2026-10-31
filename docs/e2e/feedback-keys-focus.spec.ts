// モード中のキーとフォーカス: 入力欄で打ったキーがサイトのショートカット（document・html の capture を含む）に届かない、
// Tab やクリックでページの編集欄に入れない（中身を変えられない）、モードを抜けたら元どおり使える。
import { test, ok } from './helpers/fixtures'
import type { Page } from '@playwright/test'

/** ページの編集欄（の前後にボタン）を本文の頭に足す。スマホは選んだ要素のそばにボタンが浮くので、隣の欄に重ならないよう間を空ける。 */
const addPageEditors = (page: Page) => page.evaluate(() => {
	document.querySelector('main')!.insertAdjacentHTML('afterbegin', [
		'<style>#e2e-editors > * { display: block; margin-bottom: 96px }</style>',
		'<div id="e2e-editors">',
		'<button id="e2e-before">before</button>',
		'<input id="e2e-in" value="">',
		'<textarea id="e2e-ta"></textarea>',
		'<div id="e2e-ce" contenteditable="true" style="min-height:1em">orig</div>',
		'<button id="e2e-after">after</button>',
		'</div>',
	].join(''))
	scrollTo(0, 0)
})
/** フォーカスのある要素（shadow root の中までたどる）の id かタグ。 */
const activeId = (page: Page) => page.evaluate(() => {
	let a = document.activeElement
	while (a?.shadowRoot?.activeElement) a = a.shadowRoot.activeElement
	return a ? a.id || a.localName : ''
})
const values = (page: Page) => page.evaluate(() => ({
	input: (document.getElementById('e2e-in') as HTMLInputElement).value,
	textarea: (document.getElementById('e2e-ta') as HTMLTextAreaElement).value,
	editable: document.getElementById('e2e-ce')!.textContent,
}))
const UNCHANGED = { input: '', textarea: '', editable: 'orig' }
const EDITORS = ['e2e-in', 'e2e-ta', 'e2e-ce']
const searchOpen = (page: Page) => page.locator('site-search dialog[open]').count().then((n) => n > 0)

test('入力欄で打ったキーは、document・html の capture にあるショートカットにも検索にも届かず、抜けたら元どおり届く', async ({ fb, page }) => {
	await fb.fresh()
	// サイトのショートカットの代わり: window の bubble と、document・html の capture（いちばん先に動くページのリスナー）で数える。
	await page.evaluate(() => {
		const w = window as unknown as { __pageKeys: string[] }
		w.__pageKeys = []
		for (const t of ['keydown', 'keyup', 'keypress']) {
			document.addEventListener(t, (e) => w.__pageKeys.push(`document-capture:${(e as KeyboardEvent).key}`), true)
			document.documentElement.addEventListener(t, (e) => w.__pageKeys.push(`html-capture:${(e as KeyboardEvent).key}`), true)
			window.addEventListener(t, (e) => w.__pageKeys.push(`window:${(e as KeyboardEvent).key}`))
		}
	})
	const pageKeys = () => page.evaluate(() => (window as unknown as { __pageKeys: string[] }).__pageKeys.splice(0))
	await fb.enter()
	await fb.pickEl(fb.para)
	const ta = fb.inUi('.dialog textarea')
	await ta.click()
	await pageKeys()
	await page.keyboard.type('/ abc')
	await page.keyboard.press('Control+k')
	await page.keyboard.press('Meta+k')
	await page.keyboard.press('Enter')
	await page.keyboard.type('def')
	ok('textarea に文字が入る（改行も）', /^\/ abc\n?def$/.test((await ta.inputValue()).replace(/\n+/g, '\n')), JSON.stringify(await ta.inputValue()))
	const leaked = await pageKeys()
	ok('textarea で打ったキーは document・html の capture にも window にも届かない', leaked.length === 0, leaked.join(' '))
	ok('Ctrl/⌘+K で検索が開かない', !(await searchOpen(page)))
	ok('自前の UI の中のキー操作（Esc 以外）でモードが閉じない', (await fb.rootCount()) === 1)
	// Esc でモードを抜けたあとは、ページの上のキーがショートカットに届く（数える仕掛けが生きていることの確かめ）
	await fb.esc()
	ok('Esc でモードを抜ける', (await fb.rootCount()) === 0)
	await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur())
	await pageKeys()
	await page.keyboard.press('Control+k')
	await page.waitForTimeout(300)
	const after = await pageKeys()
	ok('抜けたあとは document の capture にキーが届く', after.some((k) => k.startsWith('document-capture:')), after.join(' '))
	ok('抜けたあとは Ctrl+K で検索が開く', await searchOpen(page))
	await page.keyboard.press('Escape')
})

test('モード中は Tab やクリックでページの編集欄に入れず書き換えられない。抜けたら Tab で入って書ける', async ({ fb, page, browserName }) => {
	await fb.fresh()
	await addPageEditors(page)
	await fb.enter()
	// Tab: 編集欄の手前のボタンから進む
	await page.evaluate(() => document.getElementById('e2e-before')!.focus())
	const visited: string[] = []
	for (let i = 0; i < 5; i++) {
		await page.keyboard.press('Tab')
		visited.push(await activeId(page))
		await page.keyboard.type('x')
	}
	ok('Tab でページの編集欄にフォーカスが入らない', !visited.some((id) => EDITORS.includes(id)), visited.join(' > '))
	// WebKit（iPhone）は既定で Tab がフォームの部品にしか移らないので、ボタンへ進むかは見ない。
	if (browserName !== 'webkit') ok('Tab で編集欄を飛ばして先へ進める（同じ欄で止まらない）', visited.includes('e2e-after'), visited.join(' > '))
	ok('Tab のあと打っても中身は変わらない', JSON.stringify(await values(page)) === JSON.stringify(UNCHANGED), JSON.stringify(await values(page)))
	// クリック・タップ: mousedown でフォーカスが入っても外れる
	for (const id of EDITORS) {
		await fb.act(page.locator(`#${id}`))
		await page.keyboard.type('y')
		ok(`クリックした ${id} にフォーカスが残らない`, (await activeId(page)) !== id, await activeId(page))
	}
	ok('クリックのあと打っても中身は変わらない', JSON.stringify(await values(page)) === JSON.stringify(UNCHANGED), JSON.stringify(await values(page)))
	// 入力欄を開いているときも、Tab で抜けてページの編集欄に入らない。自前の入力欄は書けるまま
	// スマホは、選んでいる要素があると次のタップは選ぶのをやめるだけなので、先に 1 回タップしてから選ぶ。
	if (fb.mobile) {
		await fb.act(fb.para)
		await page.waitForTimeout(200)
	}
	await fb.pickEl(fb.para)
	const ta = fb.inUi('.dialog textarea')
	await ta.click()
	await page.keyboard.type('コメント')
	const fromDialog: string[] = []
	for (let i = 0; i < 12; i++) {
		await page.keyboard.press('Tab')
		fromDialog.push(await activeId(page))
	}
	ok('入力欄から Tab を続けてもページの編集欄に入らない', !fromDialog.some((id) => EDITORS.includes(id)), fromDialog.join(' > '))
	ok('自前の入力欄には書ける', (await ta.inputValue()) === 'コメント', await ta.inputValue())
	ok('ページの編集欄は書き換わっていない', JSON.stringify(await values(page)) === JSON.stringify(UNCHANGED), JSON.stringify(await values(page)))
	await ta.click()
	await fb.esc()
	ok('Esc でモードを抜ける', (await fb.rootCount()) === 0)
	// 抜けたら元どおり
	const tabindex = await page.evaluate((ids) => ids.map((id) => document.getElementById(id)!.getAttribute('tabindex')), EDITORS)
	ok('抜けたら tabindex が元に戻る（付けたものが残らない）', tabindex.every((t) => t === null), JSON.stringify(tabindex))
	await page.evaluate(() => document.getElementById('e2e-before')!.focus())
	await page.keyboard.press('Tab')
	ok('抜けたら Tab で入力欄に入る', (await activeId(page)) === 'e2e-in', await activeId(page))
	await page.keyboard.type('ok')
	await fb.act(page.locator('#e2e-ta'))
	await page.keyboard.type('ok')
	ok('抜けたら入力欄・テキストエリアに書ける', (await values(page)).input === 'ok' && (await values(page)).textarea === 'ok', JSON.stringify(await values(page)))
})
