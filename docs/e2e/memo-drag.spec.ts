// Memo の header のドラッグ: PC は popover が動き、位置を localStorage に覚える。スマホのシートは動かない。
import { test, ok } from './helpers/fixtures'

test('header のドラッグ（Memo）: 動く・ボタンの上では動かない・覚える・画面内に収まる・ダブルクリックで戻る', async ({ fb }) => {
	const { page } = fb
	const memo = fb.memoUi
	const box = (sel: string) => memo(sel).boundingBox()
	const drag = async (from: { x: number; y: number }, to: { x: number; y: number }) => {
		await page.mouse.move(from.x, from.y)
		await page.mouse.down()
		await page.mouse.move((from.x + to.x) / 2, (from.y + to.y) / 2, { steps: 4 })
		await page.mouse.move(to.x, to.y, { steps: 4 })
		await page.mouse.up()
		await page.waitForTimeout(250)
	}
	const open = async () => {
		await fb.revealToolbar()
		await fb.act(fb.memoBtn)
		await memo('.panel').waitFor({ state: 'visible', timeout: 5000 })
		await page.waitForTimeout(500)
	}
	await fb.fresh()
	await page.evaluate(() => localStorage.removeItem('katachi-memo:position'))
	await open()
	if (fb.mobile) {
		ok('シートには「つかめる」見た目が付かない', (await memo('.head.draggable').count()) === 0)
		const s0 = (await box('.panel'))!
		const hb = (await box('.head'))!
		await page.evaluate(([x, y]) => {
			const h = document.querySelector('katachi-memo-root')!.shadowRoot!.querySelector('.head')!
			const o = { bubbles: true, composed: true, pointerId: 8, pointerType: 'touch', isPrimary: true, button: 0 }
			h.dispatchEvent(new PointerEvent('pointerdown', { ...o, clientX: x, clientY: y }))
			h.dispatchEvent(new PointerEvent('pointermove', { ...o, clientX: x - 60, clientY: y - 80 }))
			h.dispatchEvent(new PointerEvent('pointerup', { ...o, clientX: x - 60, clientY: y - 80 }))
		}, [hb.x + 20, hb.y + 10])
		await page.waitForTimeout(300)
		const s1 = (await box('.panel'))!
		ok('シートは動かない', Math.abs(s1.x - s0.x) < 1 && Math.abs(s1.y - s0.y) < 1, JSON.stringify([s0, s1]))
		ok('位置を保存していない', (await page.evaluate(() => localStorage.getItem('katachi-memo:position'))) === null)
		return
	}
	const m0 = (await box('.panel'))!
	const mh = (await box('.head'))!
	await drag({ x: mh.x + 30, y: mh.y + mh.height / 2 }, { x: mh.x + 30 - 400, y: mh.y + mh.height / 2 + 150 })
	const m1 = (await box('.panel'))!
	ok('header のドラッグで popover が動く', Math.abs(m1.x - (m0.x - 400)) < 3 && Math.abs(m1.y - (m0.y + 150)) < 3, JSON.stringify([m0, m1]))
	const mc = (await box('.close'))!
	await drag({ x: mc.x + mc.width / 2, y: mc.y + mc.height / 2 }, { x: mc.x - 300, y: mc.y + 100 })
	const m2 = await box('.panel').catch(() => null)
	ok('ボタンの上からのドラッグでは動かない', !!m2 && Math.abs(m2.x - m1.x) < 1 && Math.abs(m2.y - m1.y) < 1, JSON.stringify([m1, m2]))
	ok('位置を localStorage に保存した', (await page.evaluate(() => localStorage.getItem('katachi-memo:position'))) !== null)
	await fb.act(memo('.close'))
	await page.waitForTimeout(400)
	await fb.act(fb.memoBtn)
	await memo('.panel').waitFor({ state: 'visible' })
	await page.waitForTimeout(500)
	const m3 = (await box('.panel'))!
	ok('閉じて開き直しても同じ位置に出る', Math.abs(m3.x - m1.x) < 1 && Math.abs(m3.y - m1.y) < 1, JSON.stringify([m1, m3]))
	await page.reload({ waitUntil: 'networkidle' })
	await page.waitForTimeout(1200)
	await open()
	const m4 = (await box('.panel'))!
	ok('ページを読み直しても同じ位置', Math.abs(m4.x - m1.x) < 1 && Math.abs(m4.y - m1.y) < 1, JSON.stringify([m1, m4]))
	const mh2 = (await box('.head'))!
	await drag({ x: mh2.x + 30, y: mh2.y + mh2.height / 2 }, { x: -800, y: -800 })
	const m5 = (await box('.panel'))!
	ok('左上の外へドラッグしても画面の中', m5.x >= 0 && m5.y >= 0 && m5.x < 10 && m5.y < 10, JSON.stringify(m5))
	await drag({ x: m5.x + 30, y: m5.y + 20 }, { x: 5000, y: 5000 })
	const m6 = (await box('.panel'))!
	await page.setViewportSize({ width: 900, height: 600 })
	await page.waitForTimeout(500)
	const m7 = (await box('.panel'))!
	ok('画面を小さくしても外へはみ出さず中に戻る', m7.x + m7.width <= 900 && m7.y + m7.height <= 600 && m7.x >= 0 && m7.y >= 0, JSON.stringify([m6, m7]))
	await page.setViewportSize({ width: 1280, height: 800 })
	await page.waitForTimeout(400)
	const mh3 = (await box('.head'))!
	await page.mouse.dblclick(mh3.x + 30, mh3.y + mh3.height / 2)
	await page.waitForTimeout(400)
	const m8 = (await box('.panel'))!
	ok('ダブルクリックで元の位置（右上）に戻る', Math.abs(m8.x - m0.x) < 2 && Math.abs(m8.y - m0.y) < 2, JSON.stringify([m0, m8]))
	ok('戻したら保存した位置も消える', (await page.evaluate(() => localStorage.getItem('katachi-memo:position'))) === null)
	await fb.act(memo('.close'))
	await page.waitForTimeout(300)
	ok('閉じると UI も残らない', (await fb.memoRootCount()) === 0)
})
