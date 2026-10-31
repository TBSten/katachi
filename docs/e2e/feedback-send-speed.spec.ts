// 送信の速さ: ページを開いてすぐ（画面外の lazy の画像がまだ読み込まれていないうちに）送っても、数秒で保存が終わること。
// 前は modern-screenshot が画面外の lazy の画像（LinkCard の OGP 画像）を待ち、既定の時間切れ 30 秒まで止まっていた。
// あわせて、送信中のボタンの文字が段階で変わること（撮影中… → 保存中…）を見る。
import type { Page } from '@playwright/test'
import { test, ok, sleep, BASIC } from './helpers/fixtures'
import { files } from './helpers/store'
import { BASE } from './env.mjs'

/** 送信を押してから保存が終わるまでの上限（ms）。30 秒の待ちが戻れば超える。重いマシンでの撮影（〜6 秒）は収まる値。 */
const SAVE_LIMIT = 12_000

/** 送信ボタンの文字の移り変わりを、ページの中で記録し始める（MutationObserver は複製の途中でも取りこぼさない）。 */
async function watchLabels(page: Page): Promise<void> {
	await page.evaluate(() => {
		const btn = document.querySelector('katachi-feedback-root')!.shadowRoot!.querySelector('.dialog .primary')!
		const w = window as unknown as { __labels: string[] }
		w.__labels = [btn.textContent ?? '']
		new MutationObserver(() => {
			const t = btn.textContent ?? ''
			if (w.__labels.at(-1) !== t) w.__labels.push(t)
		}).observe(btn, { childList: true, characterData: true, subtree: true })
	})
}
const labels = (page: Page) => page.evaluate(() => (window as unknown as { __labels?: string[] }).__labels ?? [])

/** まだ読み込まれていない lazy の画像（画面外）。 */
const pendingLazy = (page: Page) =>
	page.evaluate(() => [...document.querySelectorAll('img')].filter((i) => i.loading === 'lazy' && !i.complete).map((i) => i.currentSrc || i.src))

test('ページを開いてすぐ送っても、画面外の lazy の画像を待たずに数秒で保存が終わる', async ({ fb, page }) => {
	// 画像が読み込まれる前に送りたいので、fb.fresh（networkidle とその後の待ち）を使わない
	await page.goto(BASE + BASIC, { waitUntil: 'load' })
	await page.locator('astro-dev-toolbar button[data-app-id="katachi-feedback"]').waitFor({ state: 'attached' })
	const pending = await pendingLazy(page)
	ok(`画面外に、まだ読み込まれていない lazy の画像がある（${pending.length} 枚）`, pending.length > 0, JSON.stringify(pending))
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.openInfo()
	ok('スクリーンショットが既定でオン', await fb.inUi('#chk-screenshot').isChecked())
	await fb.inUi('.dialog textarea').fill(`速さ ${fb.project}`)
	ok('送る直前にも、まだ読み込まれていない lazy の画像がある', (await pendingLazy(page)).length > 0)
	await page.evaluate(() => document.querySelectorAll('img').forEach((i) => i.loading === 'lazy' && !i.complete && i.setAttribute('data-e2e-pending', '')))
	await watchLabels(page)
	const before = files().length
	const t0 = Date.now()
	await fb.act(fb.inUi('.dialog .primary'))
	while (Date.now() - t0 < 40_000 && files().length === before) await sleep(100)
	const took = Date.now() - t0
	ok(`記録ができる`, files().length === before + 1)
	ok(`保存が ${SAVE_LIMIT / 1000} 秒以内に終わる（${(took / 1000).toFixed(1)} 秒）`, took < SAVE_LIMIT, `${took}ms`)
	await page.waitForTimeout(500)
	const seen = await labels(page)
	const shotAt = seen.findIndex((l) => l.startsWith('撮影中…'))
	const saveAt = seen.findIndex((l) => l.startsWith('保存中…'))
	ok('ボタンの文字が 送信 → 撮影中… → 保存中… の順に変わる', seen[0] === '送信' && shotAt > 0 && saveAt > shotAt, JSON.stringify(seen))
	// 撮影のあいだだけ eager にして読み込ませ、撮ったあとは元の lazy に戻す（読み込みに失敗した OGP 画像は LinkCard が消す）
	const after = await page.evaluate(() => [...document.querySelectorAll<HTMLImageElement>('img[data-e2e-pending]')].map((i) => ({ loading: i.loading, complete: i.complete })))
	ok('撮影のあと、画像の loading は lazy に戻っている', after.every((i) => i.loading === 'lazy'), JSON.stringify(after))
	ok('画面外だった画像も読み込みが終わっている（eager にしたので）', after.every((i) => i.complete), JSON.stringify(after))
})

test('スクリーンショットを外して送ると、ボタンの文字は 保存中… になる', async ({ fb, page }) => {
	await fb.fresh()
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.openInfo()
	await fb.inUi('#chk-screenshot').uncheck()
	await fb.inUi('.dialog textarea').fill('スクショ無しの速さ')
	await watchLabels(page)
	const f = await fb.submitAndWait(15_000)
	ok('記録ができる', f !== null)
	const seen = await labels(page)
	ok('撮影中… を出さずに 保存中… を出す', seen.includes('保存中…') && !seen.some((l) => l.startsWith('撮影中…')), JSON.stringify(seen))
})

test('撮影が 2 秒を超えると、ボタンに経過秒数が出る（CPU を遅くして確かめる・PC だけ）', async ({ fb, page, browserName, isMobile }) => {
	test.skip(browserName !== 'chromium' || isMobile, 'CPU の制限は Chromium の CDP で入れる')
	await fb.fresh()
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.inUi('.dialog textarea').fill('遅いときの表示')
	await watchLabels(page)
	const cdp = await page.context().newCDPSession(page)
	await cdp.send('Emulation.setCPUThrottlingRate', { rate: 8 })
	try {
		const f = await fb.submitAndWait(60_000)
		ok('記録ができる', f !== null)
	} finally {
		await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 })
	}
	const seen = await labels(page)
	ok('「撮影中…（N 秒）」が出る', seen.some((l) => /^撮影中…（\d+ 秒）$/.test(l)), JSON.stringify(seen))
})
