// モードの外で、テキストを選んで右クリックして送る列（PC だけ）。常設のリスナーは contextmenu 1 つだけで、閉じたら元に戻る。
import { test, ok } from './helpers/fixtures'
import { files, readRecord } from './helpers/store'
import { gap } from './helpers/geometry'
import { attachListenerProbe } from './helpers/listeners'

test.describe('モードの外の右クリック（PC）', () => {
	test('テキストを選んで右クリック → メニュー → popover → 送信。リスナーも toolbar も元に戻る', async ({ fb, page }) => {
		await fb.fresh()
		const probe = await attachListenerProbe(page)
		const lBaseList = await probe.list()
		const lBase = probe.count(lBaseList)
		ok('常設のリスナーは contextmenu 1 つだけ', lBaseList === 'document:contextmenu', lBaseList)
		const attrsBefore = await fb.attrs()
		const before = files().length
		const sel = await fb.selectRange(0, 14)
		const tb = await page.evaluate(() => {
			const r = getSelection()!.getRangeAt(0).getBoundingClientRect()
			return { x: r.x, y: r.y, width: r.width, height: r.height }
		})
		await page.mouse.click(sel.x, sel.y, { button: 'right' })
		await page.waitForTimeout(300)
		ok('自前メニューが出る', await fb.inUi('.menu button').isVisible())
		ok('メニューの下に Shift+右クリックの案内', (await fb.inUi('.menu .hint').innerText()).includes('Shift+右クリックでブラウザのメニュー'))
		ok('案内バナーは出ない', (await fb.inUi('.banner').count()) === 0)
		const lOpen = probe.count(await probe.list())
		ok('開いている間だけリスナーが増える', lOpen > lBase, `${lBase} -> ${lOpen}`)
		await fb.shot('5-quick-menu')
		await fb.inUi('.menu button').click()
		await page.waitForTimeout(300)
		const pop = fb.inUi('.popover')
		ok('popover が出る', await pop.isVisible())
		ok('popover は選択のそば', gap(tb, (await pop.boundingBox())!) <= 24)
		ok('textarea にフォーカス', (await fb.activeTag()) === 'TEXTAREA')
		await fb.shot('5b-quick-popover')
		await fb.inUi('.popover textarea').fill('モードの外から送るテスト')
		await fb.inUi('.popover .primary').click()
		await fb.waitSaved(before)
		const md = files().length === before + 1 ? readRecord(files().at(-1)!) : ''
		ok('選んだテキストが記録に入る', md.includes(sel.text.trim()) && /## 選んだ内容/.test(md), JSON.stringify(sel.text))
		ok('UI が残らない', (await fb.rootCount()) === 0)
		const lAfter = probe.count(await probe.list())
		ok('リスナーが元に戻る', lAfter === lBase, `${lBase} vs ${lAfter}`)
		ok('toolbar アプリの状態が変わらない', (await fb.attrs()) === attrsBefore && !(await fb.appActive()), `${attrsBefore} / ${await fb.attrs()}`)
		await page.mouse.click(300, 300, { button: 'right' })
		await page.waitForTimeout(200)
		ok('閉じた後、選択なしの右クリックは何も出ない', (await fb.rootCount()) === 0)
	})

	for (const how of ['Esc', '外クリック', 'キャンセル', 'メニューで外クリック'] as const) {
		test(`${how}で閉じると、UI もリスナーも toolbar の状態も残らない`, async ({ fb, page }) => {
			await fb.fresh()
			const probe = await attachListenerProbe(page)
			const lBase = probe.count(await probe.list())
			const s = await fb.selectRange(0, 14)
			await page.mouse.click(s.x, s.y, { button: 'right' })
			await page.waitForTimeout(200)
			if (how !== 'メニューで外クリック') {
				await fb.inUi('.menu button').click()
				await page.waitForTimeout(200)
			}
			if (how === 'Esc') await page.keyboard.press('Escape')
			else if (how === 'キャンセル') await fb.inUi('.popover .actions button:not(.primary):not(.copy)').click()
			else await page.locator('main h1').first().click({ position: { x: 2, y: 2 } })
			await page.waitForTimeout(300)
			ok('UI もリスナーも残らない', (await fb.rootCount()) === 0 && probe.count(await probe.list()) === lBase && !(await fb.appActive()))
		})
	}

	test('書きかけの popover は外クリックで閉じず、Esc では閉じる', async ({ fb, page }) => {
		await fb.fresh()
		const s = await fb.selectRange(0, 14)
		await page.mouse.click(s.x, s.y, { button: 'right' })
		await fb.inUi('.menu button').click()
		await fb.inUi('.popover textarea').fill('書きかけ')
		await page.locator('main h1').first().click({ position: { x: 2, y: 2 } })
		await page.waitForTimeout(300)
		ok('モードの外でも、書きかけは外クリックで閉じない', (await fb.inUi('.popover').count()) === 1)
		await fb.esc()
		ok('Esc では閉じる', (await fb.rootCount()) === 0)
	})

	test('自前のメニューが出ないケース: Shift+右クリック・選択なし・input・contenteditable・Astro の toolbar の上', async ({ fb, page }) => {
		await fb.fresh()
		const s = await fb.selectRange(0, 14)
		await page.keyboard.down('Shift')
		await page.mouse.click(s.x, s.y, { button: 'right' })
		await page.keyboard.up('Shift')
		await page.waitForTimeout(200)
		ok('Shift+右クリックでは自前メニューが出ない', (await fb.rootCount()) === 0)
		await page.evaluate(() => getSelection()!.removeAllRanges())
		await page.mouse.click(300, 300, { button: 'right' })
		await page.waitForTimeout(200)
		ok('テキストを選ばない右クリックでは出ない', (await fb.rootCount()) === 0)
		await page.evaluate(() => {
			const i = document.createElement('input')
			i.id = 'qa-input'
			i.value = 'hello'
			i.style.cssText = 'position:fixed;left:20px;top:20px;z-index:99999'
			const d = document.createElement('div')
			d.id = 'qa-ce'
			d.contentEditable = 'true'
			d.textContent = 'editable'
			d.style.cssText = 'position:fixed;left:20px;top:60px;z-index:99999;background:#fff'
			document.body.append(i, d)
		})
		await fb.selectRange(0, 14)
		await page.locator('#qa-input').click({ button: 'right' })
		await page.waitForTimeout(200)
		ok('input の中の右クリックでは出ない（別の場所にテキスト選択があっても）', (await fb.rootCount()) === 0)
		await fb.selectRange(0, 14)
		await page.locator('#qa-ce').click({ button: 'right' })
		await page.waitForTimeout(200)
		ok('contenteditable の中の右クリックでは出ない', (await fb.rootCount()) === 0)
		await fb.selectRange(0, 14)
		await page.mouse.move(640, 790)
		await page.waitForTimeout(400)
		await fb.fbBtn.click({ button: 'right' })
		await page.waitForTimeout(200)
		ok('Astro の toolbar の上の右クリックでは出ない', (await fb.rootCount()) === 0)
	})
})
