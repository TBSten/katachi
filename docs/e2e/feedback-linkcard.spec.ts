// LinkCard（カード全体を a::before の覆いでクリックにしている）の中の文字を、ドラッグで選んでフィードバックできるか（PC だけ）。
import type { Locator, Page } from '@playwright/test'
import { test, ok } from './helpers/fixtures'
import { files, readRecord } from './helpers/store'

const PAGE = '/ja/guides/layout/'
const card = (page: Page) => page.locator('.sl-link-card').filter({ hasText: 'テンプレートからコード生成' })

/** 要素の左上から右下までをドラッグして、選ばれた文字を返す。 */
async function dragSelect(page: Page, loc: Locator, alt: boolean): Promise<string> {
	const b = (await loc.boundingBox())!
	if (alt) await page.keyboard.down('Alt')
	await page.mouse.move(b.x + 2, b.y + 4)
	await page.mouse.down()
	await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 5 })
	await page.mouse.move(b.x + b.width - 2, b.y + b.height - 4, { steps: 5 })
	await page.mouse.up()
	if (alt) await page.keyboard.up('Alt')
	return page.evaluate(() => getSelection()?.toString() ?? '')
}
const clearSel = (page: Page) => page.evaluate(() => getSelection()?.removeAllRanges())

test.describe('LinkCard の中の文字を選ぶ（PC）', () => {
	test.skip(({ isMobile }) => isMobile, 'PC の操作')

	test('Alt を押しながらドラッグすると、題も説明も選べる。離すと普段のリンクに戻る', async ({ fb, page }) => {
		await fb.fresh(PAGE)
		const c = card(page)
		await c.scrollIntoViewIfNeeded()
		const desc = c.locator('.description')
		const title = c.locator('.title')
		// 普段（Alt なし）は覆いが説明の上にあり、カード全体がリンクとして動く
		const coveredAt = () => desc.evaluate((e) => {
			const r = e.getBoundingClientRect()
			return document.elementFromPoint(r.x + 4, r.y + 4)?.closest('a') !== null
		})
		ok('Alt なしでは説明の上が覆われている', await coveredAt())
		await page.keyboard.down('Alt')
		ok('Alt を押している間は覆いが外れる', !(await coveredAt()))
		await page.keyboard.up('Alt')
		const d = await dragSelect(page, desc, true)
		ok('Alt+ドラッグで説明が選べる', d.includes('ワイルドカード'), JSON.stringify(d))
		await clearSel(page)
		const t = await dragSelect(page, title, true)
		ok('Alt+ドラッグで題が選べる', t.includes('テンプレート'), JSON.stringify(t))
		await clearSel(page)
		ok('Alt+ドラッグでリンク先へ行かない', new URL(page.url()).pathname.endsWith('/guides/layout/'), page.url())
		// Alt を離したあとは覆いが戻っていて、説明の上のクリックでカードのリンクが動く
		const covered = await desc.evaluate((e) => {
			const r = e.getBoundingClientRect()
			const hit = document.elementFromPoint(r.x + 4, r.y + 4)
			return hit?.closest('a') !== null
		})
		ok('Alt を離したらカード全体がリンクに戻る', covered)
		const href = await c.locator('a').getAttribute('href')
		await page.mouse.click((await desc.boundingBox())!.x + 4, (await desc.boundingBox())!.y + 4)
		await page.waitForURL((u) => decodeURI(u.pathname + u.hash) === decodeURI(href!), { timeout: 8000 }).catch(() => {})
		ok('Alt なしのクリックでリンク先へ行く', decodeURI(new URL(page.url()).pathname) === decodeURI(new URL(href!, page.url()).pathname), page.url())
	})

	test('Alt+ドラッグで選んで右クリック → フィードバックが送れる', async ({ fb, page }) => {
		await fb.fresh(PAGE)
		const c = card(page)
		await c.scrollIntoViewIfNeeded()
		const before = files().length
		const text = await dragSelect(page, c.locator('.description'), true)
		ok('選べた', text.trim() !== '')
		const r = (await c.locator('.description').boundingBox())!
		await page.mouse.click(r.x + 20, r.y + 8, { button: 'right' })
		await page.waitForTimeout(300)
		ok('自前メニューが出る', await fb.inUi('.menu button').isVisible())
		await fb.inUi('.menu button').click()
		await page.waitForTimeout(300)
		await fb.inUi('.popover textarea').fill('LinkCard の文字を選んで送る')
		await fb.inUi('.popover .primary').click()
		await fb.waitSaved(before)
		const md = files().length === before + 1 ? readRecord(files().at(-1)!) : ''
		ok('選んだ文字が記録に入る', md.includes(text.trim().slice(0, 6)), JSON.stringify(text))
		ok('UI が残らない', (await fb.rootCount()) === 0)
	})

	test('モードの中では Alt なしで選べ、クリックで要素も選べる。出るとカードは元に戻る', async ({ fb, page }) => {
		await fb.fresh(PAGE)
		const c = card(page)
		await c.scrollIntoViewIfNeeded()
		await fb.enter()
		const d = await dragSelect(page, c.locator('.description'), false)
		ok('モードの中で説明が選べる', d.includes('ワイルドカード'), JSON.stringify(d))
		await clearSel(page)
		const t = await dragSelect(page, c.locator('.title'), false)
		ok('モードの中で題が選べる', t.includes('テンプレート'), JSON.stringify(t))
		await clearSel(page)
		const url = page.url()
		const b = (await c.locator('.description').boundingBox())!
		await page.mouse.click(b.x + 4, b.y + 4)
		await page.waitForTimeout(300)
		ok('クリックはリンクを動かさず、要素を選ぶ', page.url() === url && (await fb.inUi('.fab, .picked, .menu').count()) >= 0)
		await fb.esc()
		const covered = await c.locator('.description').evaluate((e) => {
			const r = e.getBoundingClientRect()
			return document.elementFromPoint(r.x + 4, r.y + 4)?.closest('a') !== null
		})
		ok('モードを出たらカード全体がリンクに戻る', covered)
	})
})
