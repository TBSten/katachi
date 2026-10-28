// Feedback / Memo の画面操作の共通の手順。テストの操作の列は spec に残し、ここには 1 手ごとの動きだけを置く。
import { expect, type Locator, type Page, type TestInfo } from '@playwright/test'
import { BASE } from '../env.mjs'
import { files, latestRecord, readRecord } from './store'

export const BASIC = '/ja/guides/basic-api/'
export const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

/** 失敗しても次の確認へ進む確認（1 つの列で見つかる不具合を一度に全部出すため）。 */
export const ok = (name: string, cond: unknown, detail = ''): void => {
	expect.soft(!!cond, detail ? `${name}  ${detail}` : name).toBe(true)
}

/** 条件が真になるまで待つ（最大 ms）。 */
export async function waitFor(fn: () => boolean | Promise<boolean>, ms = 4000): Promise<boolean> {
	const t = Date.now()
	while (Date.now() - t < ms) {
		if (await fn()) return true
		await sleep(100)
	}
	return false
}

export class Fb {
	readonly pageErrors: string[] = []
	allowPageErrors = false
	constructor(
		readonly page: Page,
		readonly mobile: boolean,
		readonly project: string,
		readonly info: TestInfo,
	) {
		page.on('pageerror', (e) => {
			// 画面の外の第三者のスクリプトの失敗（addEventListener）は対象外
			if (!/addEventListener/.test(e.message)) this.pageErrors.push(e.message)
		})
	}

	// ---- 場所 ----
	inUi = (sel: string): Locator => this.page.locator(`katachi-feedback-root ${sel}`)
	memoUi = (sel: string): Locator => this.page.locator(`katachi-memo-root ${sel}`)
	get fbBtn(): Locator { return this.page.locator('astro-dev-toolbar button[data-app-id="katachi-feedback"]') }
	get memoBtn(): Locator { return this.page.locator('astro-dev-toolbar button[data-app-id="katachi-memo"]') }
	/** 本文の最初の段落。 */
	get para(): Locator { return this.page.locator('main p').first() }
	rootCount = (): Promise<number> => this.page.evaluate(() => document.querySelectorAll('katachi-feedback-root').length)
	memoRootCount = (): Promise<number> => this.page.evaluate(() => document.querySelectorAll('katachi-memo-root, katachi-memo-toast').length)
	appActive = (): Promise<boolean> => this.fbBtn.evaluate((b) => b.hasAttribute('data-active') || b.classList.contains('active') || b.getAttribute('aria-pressed') === 'true' || b.hasAttribute('active'))
	attrs = (): Promise<string> => this.fbBtn.evaluate((b) => [...b.attributes].map((a) => `${a.name}=${a.value}`).join(' '))
	/** PC はクリック、スマホはタップ。 */
	act = (loc: Locator, opts?: Parameters<Locator['click']>[0]): Promise<void> => (this.mobile ? loc.tap(opts) : loc.click(opts))
	shot = (name: string) => this.page.screenshot({ path: this.info.outputPath(`${this.project}-${name}.png`) })

	// ---- 画面を開く・モードに入る ----
	async fresh(path = BASIC): Promise<void> {
		await this.page.goto(BASE + path, { waitUntil: 'networkidle' })
		await this.page.waitForTimeout(1500)
	}
	/** 隠れた toolbar を出す（PC は下端への hover。スマホは常に見えている）。 */
	async revealToolbar(): Promise<void> {
		if (!this.mobile) {
			const h = this.page.viewportSize()!.height
			await this.page.mouse.move(600, h - 200)
			await this.page.mouse.move(640, h - 10)
		}
		await this.page.waitForTimeout(400)
	}
	async enter(): Promise<void> {
		await this.revealToolbar()
		await this.act(this.fbBtn)
		await this.page.waitForTimeout(300)
	}
	/** スマホは Esc などで閉じたあとの toolbar が落ち着かないので、読み直してから入る。 */
	async reenter(): Promise<void> {
		if (this.mobile) await this.fresh()
		await this.enter()
	}
	async esc(): Promise<void> {
		await this.page.keyboard.press('Escape')
		await this.page.waitForTimeout(300)
	}

	// ---- 選ぶ → 入力欄を開く ----
	/** PC は右クリックのメニュー、スマホは浮かぶボタンで入力欄を開く。 */
	async pickEl(loc: Locator, pos = { x: 6, y: 6 }): Promise<void> {
		await this.act(loc, { position: pos })
		await this.page.waitForTimeout(200)
		if (this.mobile) await this.act(this.inUi('.fab'))
		else {
			await loc.click({ button: 'right', position: pos })
			await this.page.waitForTimeout(150)
			await this.inUi('.menu button').click()
		}
		await this.page.waitForTimeout(400)
	}
	/** 自前の UI の中でフォーカスされている要素のタグ。 */
	activeTag = (): Promise<string> => this.page.evaluate(() => document.querySelector('katachi-feedback-root')?.shadowRoot?.activeElement?.tagName ?? '')
	/** 最初の段落のテキストを範囲選択し、座標を返す。 */
	async selectRange(from = 0, to = 14): Promise<{ text: string; x: number; y: number }> {
		return this.page.evaluate(([f, t]) => {
			const p = document.querySelector('main p')!
			const n = [...p.childNodes].find((x) => x.nodeType === 3 && (x.textContent ?? '').trim().length > 14)!
			const r = document.createRange()
			r.setStart(n, f)
			r.setEnd(n, t)
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		}, [from, to])
	}
	/** テキストを選んで入力欄を開く（PC は選択の上の右クリック、スマホは浮かぶボタン）。 */
	async selectText(from = 0, to = 14): Promise<void> {
		const p = await this.selectRange(from, to)
		await this.page.waitForTimeout(500)
		if (this.mobile) await this.act(this.inUi('.fab'))
		else {
			await this.page.mouse.click(p.x, p.y, { button: 'right' })
			await this.page.waitForTimeout(150)
			await this.inUi('.menu button').click()
		}
		await this.page.waitForTimeout(400)
	}
	/** 「載せる情報」を開く（開いてあれば何もしない）。 */
	async openInfo(): Promise<void> {
		if (await this.inUi('.dialog details.info').count()) await this.inUi('.dialog details.info').evaluate((d: HTMLDetailsElement) => (d.open = true))
	}

	// ---- 送る ----
	/** 送信を押し、記録が 1 件増えるまで待つ。増えなければ null。 */
	async submitAndWait(timeout = 40_000): Promise<string | null> {
		const before = files().length
		await this.act(this.inUi('.dialog .primary'))
		const t = Date.now()
		while (Date.now() - t < timeout && files().length === before) await sleep(250)
		await this.page.waitForTimeout(400)
		return files().length === before + 1 ? files().at(-1)! : null
	}
	/** 押した直後から、記録が増えるまで待つ（スクショ込みの送信は数秒〜十数秒かかる）。 */
	async waitSaved(before: number, timeout = 40_000): Promise<void> {
		const t = Date.now()
		while (Date.now() - t < timeout && files().length === before) await this.page.waitForTimeout(250)
		await this.page.waitForTimeout(500)
	}
	latest = latestRecord
	read = readRecord

	/** ページエラーが無いことを確かめる。fixture が各テストの最後に呼ぶ。 */
	assertNoPageErrors(): void {
		if (!this.allowPageErrors) expect.soft(this.pageErrors, 'ページエラーが出ない').toEqual([])
	}
}
