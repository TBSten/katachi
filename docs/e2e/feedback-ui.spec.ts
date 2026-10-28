// 入力欄の小物: 下書き（Esc → 開き直しで戻る）・親を選ぶ・header のドラッグ（Feedback）。
import { test, ok } from './helpers/fixtures'
import { files } from './helpers/store'
import type { Locator } from '@playwright/test'

test('下書き: 書いて Esc → 同じ場所を開き直すと戻り、別の場所には出ず、送ると消える', async ({ fb, page }) => {
	await fb.fresh()
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.inUi('.dialog textarea').fill('書きかけのコメント')
	// 件数を変えるため、ブラウザの節のチェックを外す
	await fb.inUi('.dialog summary').first().click().catch(() => {})
	await page.waitForTimeout(100)
	if (await fb.inUi('#chk-browser').count()) {
		await fb.openInfo()
		await fb.inUi('#chk-browser').uncheck()
	}
	await fb.esc()
	ok('Esc で UI が消える', (await fb.rootCount()) === 0)
	await fb.reenter()
	await fb.pickEl(fb.para)
	ok('同じ場所をもう一度開くと、コメントが戻っている', (await fb.inUi('.dialog textarea').inputValue()) === '書きかけのコメント')
	await fb.openInfo()
	ok('チェックの状態も戻っている（ブラウザは外れたまま）', (await fb.inUi('#chk-browser').isChecked()) === false)
	ok('送信ボタンが押せる（コメントがあるので）', await fb.inUi('.dialog .primary').isEnabled())
	await fb.esc()
	await fb.reenter()
	await fb.pickEl(page.locator('main h2').first(), { x: 4, y: 4 })
	ok('別の場所では下書きが出ない', (await fb.inUi('.dialog textarea').inputValue()) === '')
	await fb.esc()
	// 送ると消える
	await fb.reenter()
	await fb.pickEl(fb.para)
	const before = files().length
	const sent = await fb.submitAndWait()
	ok('送信で記録ができる', sent !== null, `${before}->${files().length}`)
	await page.waitForTimeout(500)
	ok('送ったあと UI が残らない', (await fb.rootCount()) === 0)
	await fb.reenter()
	await fb.pickEl(fb.para)
	ok('送ったあと、同じ場所は空に戻っている', (await fb.inUi('.dialog textarea').inputValue()) === '')
	const ssKeys = await page.evaluate(() => Object.keys(sessionStorage).filter((k) => k.startsWith('katachi-feedback:draft:')))
	ok('sessionStorage に下書きが残っていない', ssKeys.length === 0, JSON.stringify(ssKeys))
	await fb.esc()
})

test('親を選ぶ: 枠が親に広がってもコメントを引き継ぎ（PC は Alt+↑ も）、送ると記録の XPath が親になる', async ({ fb, page }) => {
	await fb.fresh()
	await fb.enter()
	const codeEl = page.locator('main p code').first()
	await fb.pickEl(codeEl, { x: 3, y: 3 })
	await fb.inUi('.dialog textarea').fill('親に広げる前のコメント')
	const frameBefore = (await fb.inUi('.frame.picked').boundingBox())!
	const codeBox = (await codeEl.boundingBox())!
	ok('最初は <code> を囲んでいる', Math.abs(frameBefore.width - codeBox.width) < 2 && Math.abs(frameBefore.height - codeBox.height) < 2, JSON.stringify([frameBefore, codeBox]))
	ok('「親を選ぶ」ボタンがある', (await fb.inUi('.dialog .parent').count()) === 1)
	await fb.act(fb.inUi('.dialog .parent'))
	await page.waitForTimeout(400)
	const parentBox = await codeEl.evaluate((e) => {
		const r = e.parentElement!.getBoundingClientRect()
		return { x: r.x, y: r.y, width: r.width, height: r.height }
	})
	const frameAfter = (await fb.inUi('.frame.picked').boundingBox())!
	ok('枠が親（段落）に広がる', Math.abs(frameAfter.width - parentBox.width) < 2 && Math.abs(frameAfter.height - parentBox.height) < 2 && Math.abs(frameAfter.x - parentBox.x) < 2, JSON.stringify([frameAfter, parentBox]))
	ok('入力欄は開いたまま、コメントを引き継ぐ', (await fb.inUi('.dialog textarea').inputValue()) === '親に広げる前のコメント')
	if (!fb.mobile) {
		await fb.inUi('.dialog textarea').focus()
		await page.keyboard.press('Alt+ArrowUp')
		await page.waitForTimeout(400)
		const f3 = (await fb.inUi('.frame.picked').boundingBox())!
		ok('Alt+↑ でさらに親へ広がる', f3.width >= frameAfter.width && (f3.height > frameAfter.height || f3.width > frameAfter.width), JSON.stringify([frameAfter, f3]))
		ok('Alt+↑ のあともコメントは残る', (await fb.inUi('.dialog textarea').inputValue()) === '親に広げる前のコメント')
	}
	const sent = await fb.submitAndWait()
	ok('親に広げて送ると記録ができる', sent !== null)
	if (sent) {
		const md = fb.read(sent)
		ok('記録の XPath が <code> ではなく親になった', !/^xpath: .*code/m.test(md) && /^xpath: /m.test(md), md.match(/^xpath: (.*)$/m)?.[1])
		ok('コメントが記録にある', md.includes('親に広げる前のコメント'))
	}
})

const box = (loc: Locator) => loc.boundingBox()

test('header のドラッグ（Feedback）: PC は popover が動いて画面内に留まり、スマホのシートは動かない', async ({ fb, page }) => {
	await fb.fresh()
	await fb.enter()
	await fb.pickEl(fb.para)
	if (fb.mobile) {
		ok('シートには「つかめる」見た目が付かない', (await fb.inUi('.dialog .head.draggable').count()) === 0)
		const sheet0 = (await box(fb.inUi('.dialog')))!
		const hb = (await box(fb.inUi('.dialog .head')))!
		// タッチでの縦横のドラッグ（pointer events を直接）
		await page.evaluate(([x, y]) => {
			const h = document.querySelector('katachi-feedback-root')!.shadowRoot!.querySelector('.dialog .head')!
			const o = { bubbles: true, composed: true, pointerId: 7, pointerType: 'touch', isPrimary: true, button: 0 }
			h.dispatchEvent(new PointerEvent('pointerdown', { ...o, clientX: x, clientY: y }))
			h.dispatchEvent(new PointerEvent('pointermove', { ...o, clientX: x - 60, clientY: y - 80 }))
			h.dispatchEvent(new PointerEvent('pointerup', { ...o, clientX: x - 60, clientY: y - 80 }))
		}, [hb.x + 20, hb.y + 10])
		await page.waitForTimeout(300)
		const sheet1 = (await box(fb.inUi('.dialog')))!
		ok('シートは動かない', Math.abs(sheet1.x - sheet0.x) < 1 && Math.abs(sheet1.y - sheet0.y) < 1, JSON.stringify([sheet0, sheet1]))
		return
	}
	const drag = async (from: { x: number; y: number }, to: { x: number; y: number }) => {
		await page.mouse.move(from.x, from.y)
		await page.mouse.down()
		await page.mouse.move((from.x + to.x) / 2, (from.y + to.y) / 2, { steps: 4 })
		await page.mouse.move(to.x, to.y, { steps: 4 })
		await page.mouse.up()
		await page.waitForTimeout(250)
	}
	const pop = fb.inUi('.dialog.popover')
	ok('header は「つかめる」見た目（cursor: move）', (await fb.inUi('.dialog .head').evaluate((e) => getComputedStyle(e).cursor)) === 'move')
	const p0 = (await box(pop))!
	const h0 = (await box(fb.inUi('.dialog .head')))!
	await drag({ x: h0.x + 30, y: h0.y + h0.height / 2 }, { x: h0.x + 230, y: h0.y + h0.height / 2 + 150 })
	const p1 = (await box(pop))!
	ok('header のドラッグで popover が動く', Math.abs(p1.x - (p0.x + 200)) < 3 && Math.abs(p1.y - (p0.y + 150)) < 3, JSON.stringify([p0, p1]))
	await page.mouse.wheel(0, 300)
	await page.waitForTimeout(500)
	const p2 = (await box(pop))!
	ok('スクロールしても動かした位置のまま', Math.abs(p2.x - p1.x) < 1 && Math.abs(p2.y - p1.y) < 1, JSON.stringify([p1, p2]))
	const pb = (await box(fb.inUi('.dialog .head .parent')))!
	await drag({ x: pb.x + pb.width / 2, y: pb.y + pb.height / 2 }, { x: pb.x + 100, y: pb.y + 200 })
	const p3 = (await box(pop))!
	ok('「親を選ぶ」ボタンの上からのドラッグでは動かない', Math.abs(p3.x - p2.x) < 1 && Math.abs(p3.y - p2.y) < 1, JSON.stringify([p2, p3]))
	await page.waitForTimeout(300)
	const h1 = (await box(fb.inUi('.dialog .head')))!
	await drag({ x: h1.x + 30, y: h1.y + h1.height / 2 }, { x: -500, y: -500 })
	const p4 = (await box(pop))!
	ok('左上の外へドラッグしても画面の中に留まる', p4.x >= 0 && p4.y >= 0 && p4.x < 10 && p4.y < 10, JSON.stringify(p4))
	const h2 = (await box(fb.inUi('.dialog .head')))!
	await drag({ x: h2.x + 30, y: h2.y + h2.height / 2 }, { x: 5000, y: 5000 })
	const p5 = (await box(pop))!
	const vp = page.viewportSize()!
	ok('右下の外へドラッグしても画面の中に留まる', p5.x + p5.width <= vp.width && p5.y + p5.height <= vp.height && p5.x + p5.width > vp.width - 12 && p5.y + p5.height > vp.height - 12, JSON.stringify(p5))
	const h3 = (await box(fb.inUi('.dialog .head')))!
	await page.mouse.dblclick(h3.x + 30, h3.y + h3.height / 2)
	await page.waitForTimeout(300)
	const p6 = (await box(pop))!
	ok('header のダブルクリックで選択のそばの元の位置に戻る', Math.abs(p6.x - p0.x) < 3, JSON.stringify([p0, p6]))
	await page.evaluate(() => scrollTo(0, 0))
	await page.waitForTimeout(400)
	const y0 = (await box(pop))!.y
	await page.mouse.wheel(0, 100)
	await page.waitForTimeout(400)
	ok('戻ったあとは再び選んだ場所を追う（スクロールで位置が変わる）', Math.abs((await box(pop))!.y - y0) > 20)
	// 位置はその回だけ: 閉じて開き直すと選択のそば
	const hh = (await box(fb.inUi('.dialog .head')))!
	await drag({ x: hh.x + 30, y: hh.y + 10 }, { x: hh.x + 330, y: hh.y + 210 })
	await fb.esc()
	await page.evaluate(() => scrollTo(0, 0))
	await fb.enter()
	await fb.pickEl(fb.para)
	const pn = (await box(fb.inUi('.dialog.popover')))!
	const eb = (await box(fb.para))!
	ok('閉じて開き直すと位置は忘れ、選んだ場所のそばに出る', Math.abs(pn.y - (eb.y + eb.height + 8)) < 40 || Math.abs(pn.y + pn.height - eb.y) < 40, JSON.stringify([pn, eb]))
	ok('位置を localStorage に覚えていない', (await page.evaluate(() => Object.keys(localStorage).filter((k) => /position|pos/i.test(k) && k.includes('feedback')).length)) === 0)
	await fb.esc()
})

test('モード中のキー: ページのショートカットには届かず、Space ではスクロールでき、Enter でリンクは動かない', async ({ fb, page }) => {
	test.skip(fb.mobile, 'PC の列（キーボード）')
	await fb.fresh()
	await fb.enter()
	await page.evaluate(() => {
		const w = window as unknown as { __keys: number }
		w.__keys = 0
		const count = () => { w.__keys++ }
		for (const t of ['keydown', 'keyup', 'keypress']) {
			window.addEventListener(t, count)
			document.addEventListener(t, count)
		}
		// Enter で動かないことを確かめるためのリンク（ハッシュだけ変わる）。
		document.body.insertAdjacentHTML('afterbegin', '<a id="e2e-link" href="#e2e-hash" style="position:absolute;top:0;left:0">e2e</a>')
		;(document.activeElement as HTMLElement | null)?.blur()
		scrollTo(0, 0)
	})
	const keys = () => page.evaluate(() => (window as unknown as { __keys: number }).__keys)
	await page.keyboard.press('/')
	await page.keyboard.press('Control+k')
	ok('ページ上で押したキーはショートカットに届かない', (await keys()) === 0, `count=${await keys()}`)
	await page.keyboard.press('Space')
	await page.waitForTimeout(600)
	const y = await page.evaluate(() => scrollY)
	ok('Space でページがスクロールする（既定の動作は止めない）', y > 100, `scrollY=${y}`)
	ok('Space もショートカットには届かない', (await keys()) === 0, `count=${await keys()}`)
	await page.evaluate(() => (document.getElementById('e2e-link') as HTMLElement).focus())
	await page.keyboard.press('Enter')
	await page.waitForTimeout(300)
	ok('Enter でページのリンクは動かない（click の横取りで止まる）', new URL(page.url()).hash === '', page.url())
	await page.evaluate(() => document.getElementById('e2e-link')?.remove())
	await fb.esc()
})
