// 終わらせ方ごとに「自前の UI も toolbar の状態も（PC ではリスナーも）残らない」こと。Feedback と Memo。
// 各機能の列の中にも同じ確認はあるが、ここは終わらせ方を網羅して、残り物だけを見る。
import { test, ok, waitFor } from './helpers/fixtures'
import { attachListenerProbe } from './helpers/listeners'
import type { Fb } from './helpers/fb'

/** 自前のカスタム要素（katachi-*）が DOM に残っていない。 */
const leftovers = (fb: Fb) => fb.page.evaluate(() => [...document.querySelectorAll('*')].map((e) => e.tagName.toLowerCase()).filter((t) => t.startsWith('katachi-')))

async function expectClean(fb: Fb, what: string, baseline?: { list: () => Promise<string>; base: string }) {
	await fb.page.waitForTimeout(400)
	// 保存のあとの通知（katachi-feedback-toast）は数秒で自分で消える。消えるところまで待つ。
	await waitFor(async () => (await leftovers(fb)).length === 0, 8000)
	ok(`${what}: 自前の要素が残らない`, (await leftovers(fb)).length === 0, JSON.stringify(await leftovers(fb)))
	ok(`${what}: toolbar のアプリが閉じる`, !(await fb.appActive()) && !(await fb.memoBtn.evaluate((b) => b.hasAttribute('data-active') || b.hasAttribute('active'))))
	ok(`${what}: 選択が残らない`, (await fb.page.evaluate(() => getSelection()!.toString())) === '')
	if (baseline) ok(`${what}: リスナーが元に戻る`, (await baseline.list()) === baseline.base, `${baseline.base} vs ${await baseline.list()}`)
}

test.describe('Feedback の終わらせ方', () => {
	const routes: [string, (fb: Fb) => Promise<void>][] = [
		['モードに入って Esc', async (fb) => { await fb.enter(); await fb.esc() }],
		['要素を選んで入力欄を開き、Esc', async (fb) => { await fb.enter(); await fb.pickEl(fb.para); await fb.esc() }],
		['要素を選んで入力欄を開き、キャンセル', async (fb) => { await fb.enter(); await fb.pickEl(fb.para); await fb.act(fb.inUi('.dialog .actions button:not(.primary):not(.copy)')) }],
		['要素を選んで送信（スクショなし。保存の通知は自分で消える）', async (fb) => {
			await fb.enter()
			await fb.pickEl(fb.para)
			await fb.openInfo()
			await fb.inUi('#chk-screenshot').uncheck()
			await fb.inUi('.dialog textarea').fill('終わらせ方の確認')
			await fb.submitAndWait(15_000)
		}],
		['モードに入って toolbar をもう一度押す', async (fb) => { await fb.enter(); await fb.revealToolbar(); await fb.act(fb.fbBtn) }],
	]
	for (const [name, run] of routes) {
		test(`${name}: UI も toolbar の状態も残らない`, async ({ fb, page }) => {
			await fb.fresh()
			const probe = fb.mobile ? undefined : await attachListenerProbe(page)
			const base = probe ? await probe.list() : ''
			await run(fb)
			await expectClean(fb, name, probe ? { list: () => probe.list(), base } : undefined)
		})
	}
	test('スマホ: 「やめる」ボタンで UI も toolbar の状態も残らない', async ({ fb }) => {
		test.skip(!fb.mobile, 'スマホの列')
		await fb.fresh()
		await fb.enter()
		await fb.act(fb.inUi('.exit'))
		await expectClean(fb, 'やめる')
	})
})

test.describe('Memo の終わらせ方', () => {
	const open = async (fb: Fb) => {
		await fb.revealToolbar()
		await fb.act(fb.memoBtn)
		await fb.memoUi('.panel').waitFor({ state: 'visible', timeout: 5000 })
	}
	test('閉じるボタン: UI もリスナーも残らない', async ({ fb, page }) => {
		await fb.fresh()
		const probe = fb.mobile ? undefined : await attachListenerProbe(page)
		const base = probe ? await probe.list() : ''
		await open(fb)
		await fb.act(fb.memoUi('.close'))
		await expectClean(fb, '閉じるボタン', probe ? { list: () => probe.list(), base } : undefined)
	})
	test('PC: Esc で閉じても UI もリスナーも残らない', async ({ fb, page }) => {
		test.skip(fb.mobile, 'PC の列')
		await fb.fresh()
		const probe = await attachListenerProbe(page)
		const base = await probe.list()
		await open(fb)
		await page.keyboard.press('Escape')
		await expectClean(fb, 'Esc', { list: () => probe.list(), base })
	})
	test('箇所を選ぶ操作の途中で閉じても、枠・帯・印が残らない', async ({ fb, page }) => {
		test.skip(fb.mobile, 'PC の列')
		await fb.fresh()
		await open(fb)
		await fb.memoUi('.pick').click()
		await page.mouse.move(500, 400)
		await page.keyboard.press('Escape')
		await page.keyboard.press('Escape')
		await expectClean(fb, '選ぶ途中の Esc')
	})
})
