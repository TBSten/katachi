// 「場所を一意に指す情報」の検証の手順（ソースの行・XPath・セレクタ・テキストフラグメント・git）。
// 保存された記録を読み、写しのソースの行・同じページ・新しいページで確かめる。
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { join } from 'node:path'
import type { JSHandle, Locator, Page } from '@playwright/test'
import { COPY_DOCS, COPY_ROOT, REPO_ROOT, BASE } from '../env.mjs'
import { ok, sleep, type Fb } from './fb'
import { files, latestRecord, parseRecord } from './store'

const sharp = createRequire(join(COPY_DOCS, 'package.json'))('sharp') as (input: Buffer) => import('sharp').Sharp

/** `path:行` または `path:開始-終了` の行の中身（写しのソースから）。 */
export function sourceLines(src: string | undefined): { path: string; start: number; end: number; text: string } | null {
	const m = /^(.+):(\d+)(?:-(\d+))?$/.exec(src ?? '')
	if (!m) return null
	const lines = readFileSync(join(COPY_ROOT, m[1]), 'utf8').split('\n')
	return { path: m[1], start: +m[2], end: +(m[3] ?? m[2]), text: lines.slice(+m[2] - 1, +(m[3] ?? m[2])).join('\n') }
}
/** 文字列の中で長い「語」の上位。ソースの行にこのどれかが入っていれば、その行を読んだことになる。 */
export const topRuns = (s: string, n = 5): string[] => [...new Set(s.match(/[\p{L}\p{N}_]{3,}/gu) ?? [])].sort((a, b) => b.length - a.length).slice(0, n)
/** 他の作業で HEAD が進むことがあるので、確かめる直前に求める。 */
export const head = (): string => execFileSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: REPO_ROOT }).toString().trim()

/** 画面のハイライト（テキストフラグメントの一致）を画素で数える。Chrome の ::target-text は薄い紫。clip はページ座標。 */
export async function markCount(page: Page, clip?: { x: number; y: number; width: number; height: number }): Promise<number> {
	const buf = await page.screenshot(clip ? { clip, fullPage: true } : {})
	const { data, info } = await sharp(buf).raw().toBuffer({ resolveWithObject: true })
	let n = 0
	for (let i = 0; i < data.length; i += info.channels) if (data[i] >= 200 && data[i + 2] >= 235 && data[i + 2] - data[i + 1] >= 30) n++
	return n
}

export class Locate {
	constructor(readonly fb: Fb) {}
	get page() { return this.fb.page }
	pop = () => this.fb.inUi('.popover')
	go = async (path: string) => { await this.fb.fresh(path) }

	/** 要素を選んでポップオーバーまで開く。返すのは、クリックが実際に当たった要素のハンドル。 */
	async openElement(loc: Locator, pos = { x: 6, y: 6 }) {
		const { page } = this
		await loc.scrollIntoViewIfNeeded()
		const box = (await loc.boundingBox())!
		const x = box.x + pos.x
		const y = box.y + pos.y
		const handle = await page.evaluateHandle(([a, b]) => document.elementFromPoint(a, b), [x, y])
		await page.mouse.move(x, y)
		await page.mouse.click(x, y)
		await sleep(150)
		await page.mouse.click(x, y, { button: 'right' })
		await sleep(150)
		await this.fb.inUi('.menu button').click()
		await sleep(300)
		return handle
	}
	/** テキストを選んで（モードの外で）右クリック → popover。コンテナ要素のハンドルを返す。 */
	async openText(select: () => { text: string; x: number; y: number }) {
		const { page } = this
		const r = await page.evaluate(select)
		const handle = await page.evaluateHandle(() => {
			const n = getSelection()!.getRangeAt(0).commonAncestorContainer
			return n.nodeType === 1 ? (n as Element) : n.parentElement!
		})
		await page.mouse.click(r.x, r.y, { button: 'right' })
		await sleep(250)
		await this.fb.inUi('.menu button').click()
		await sleep(300)
		return { handle, ...r }
	}
	/** 「載せる情報」を開き、必要なら節を外し、コメントを書いて送る。記録の本文を返す（できなければ null）。 */
	async submit({ comment = '場所の検証', uncheck = [], previewShot, previewChecks }: { comment?: string; uncheck?: string[]; previewShot?: string; previewChecks?: () => Promise<void> } = {}): Promise<string | null> {
		const { fb, page } = this
		await fb.inUi('.popover details.info > summary').click()
		await sleep(200)
		if (previewChecks) await previewChecks()
		if (previewShot) await fb.shot(previewShot)
		for (const k of uncheck) await fb.inUi(`.popover #chk-${k}`).uncheck()
		await fb.inUi('.popover textarea').fill(comment)
		const before = files().length
		await fb.inUi('.popover .primary').click()
		await fb.waitSaved(before)
		if (files().length !== before + 1) return null
		await page.evaluate(() => localStorage.clear())
		return latestRecord()
	}
	/** 保存した XPath / セレクタを同じページで引き直し、ハンドルの要素にちょうど当たるか。 */
	async resolves(front: Record<string, any>, handle: JSHandle<any>) {
		return this.page.evaluate(([xp, sel, el]) => {
			const r: { xpath?: boolean; selector?: boolean } = {}
			try {
				const s = document.evaluate(xp as string, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null)
				r.xpath = s.snapshotLength === 1 && s.snapshotItem(0) === el
			} catch { r.xpath = false }
			try {
				const q = document.querySelectorAll(sel as string)
				r.selector = q.length === 1 && q[0] === el
			} catch { r.selector = false }
			return r
		}, [front.xpath ?? '', front.selector ?? '', handle] as const)
	}
	/** ソースの行の検証（選んだ要素・文字列の語がその行にある） */
	async sourceOk(name: string, md: string, handle: JSHandle<any>, extraText?: string) {
		const { front } = parseRecord(md)
		const lines = sourceLines(front.source)
		const text = extraText ?? (await handle.evaluate((e) => (e as Element).textContent ?? ''))
		const runs = topRuns(text)
		const hit = lines && runs.find((r) => lines.text.includes(r))
		ok(`${name}: ソースの行にその語が含まれる`, !!hit, `${front.source} runs=${JSON.stringify(runs.slice(0, 3))} hit=${hit}`)
		return { front, lines }
	}
	async locationOk(name: string, md: string, handle: JSHandle<any>) {
		const { front } = parseRecord(md)
		const r = await this.resolves(front, handle)
		ok(`${name}: XPath が同じ要素にちょうど 1 つ当たる`, r.xpath, String(front.xpath))
		ok(`${name}: CSS セレクタが同じ要素にちょうど 1 つ当たる`, r.selector, String(front.selector))
		return front
	}
	/** テキストフラグメント URL を新しいページで開き、保存した XPath の要素の矩形の中がハイライトされるか。 */
	async fragmentHighlight(page2: Page, name: string, frag: string, xpath: string, shotName: string) {
		await page2.setViewportSize({ width: 1280, height: 800 })
		await page2.goto('about:blank')
		await page2.goto(frag, { waitUntil: 'networkidle' })
		await page2.waitForTimeout(1800)
		const rect = await page2.evaluate((xp) => {
			const e = document.evaluate(xp, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue as Element | null
			if (!e) return null
			const b = e.getBoundingClientRect()
			return { x: b.x + scrollX, y: b.y + scrollY, width: b.width, height: b.height }
		}, xpath)
		const inView = !!rect && rect.width > 0 && rect.height > 0
		const y = inView ? await markCount(page2, rect!) : 0
		await page2.screenshot({ path: this.fb.info.outputPath(`locate-fragment-${shotName}.png`) })
		ok(`${name}: 開いたページで選んだ場所がハイライト（薄紫）される`, inView && y >= 30, `rect=${JSON.stringify(rect)} 矩形内のハイライト画素=${y}`)
	}
}
export { BASE }
