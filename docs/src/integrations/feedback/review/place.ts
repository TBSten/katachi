// 指摘の行から、ページの上の要素を探す（ブラウザ側）。本文の要素には dev のときだけ data-fb-src="path:12-14" が付いている。
import { SRC_ATTR } from '../core/protocol'
import { parseSrc, type Src } from '../shared/srcmap'
import type { LineRange } from './model'

export type Target = {
	/** data-fb-src を持つ要素。 */
	el: Element
	/** コードブロックの中の行なら、その行の要素（EC の `.ec-line`）。光らせる範囲はこちら。 */
	lines: Element[]
}

/** 見えていなければ（閉じた details・選ばれていないタブの中）、見えている最も近い祖先。枠と印はこちらに置く。 */
export function shownOf(el: Element): Element {
	for (let cur: Element | null = el; cur; cur = cur.parentElement) if (visible(cur)) return cur
	return el
}

export const isHidden = (t: Target) => !visible(t.el)

const span = (s: Src) => s.end - s.start
/**
 * 見えているか。閉じた details の中身は、Chrome では大きさを持ったまま隠れる（content-visibility）ので、矩形だけでは分からない。
 * checkVisibility があればそれも見て、閉じた details の中（summary を除く）は見えないとみなす。
 */
const visible = (el: Element) => {
	if (typeof el.checkVisibility === 'function' && !el.checkVisibility({ visibilityProperty: true })) return false
	const closed = el.closest('details:not([open])')
	if (closed && closed !== el && !closed.querySelector(':scope > summary')?.contains(el)) return false
	const r = el.getBoundingClientRect()
	return r.width > 0 || r.height > 0
}

/**
 * 行を含む要素のうち、いちばん狭いものを選ぶ。同じ広さなら後ろ（内側）の要素。行を含む要素が無ければ、範囲が重なる要素から選ぶ。
 * 見えていない要素（閉じた details の中など）も選ぶ。見せるのは popup を開くとき（session.ts の unhide）。
 */
export function findTarget(root: ParentNode, file: string, line: LineRange, hints: readonly string[] = []): Target | null {
	const all = [...root.querySelectorAll(`[${SRC_ATTR}]`)]
		.map((el) => ({ el, src: parseSrc(el.getAttribute(SRC_ATTR)) }))
		.filter((c): c is { el: Element; src: Src } => c.src !== null && c.src.path === file)
	const containing = all.filter((c) => c.src.start <= line.start && line.start <= c.src.end)
	const overlapping = all.filter((c) => c.src.start <= line.end && line.start <= c.src.end)
	const pick = (list: typeof all) => {
		let best: (typeof all)[number] | null = null
		for (const c of list) if (!best || span(c.src) <= span(best.src)) best = c
		return best
	}
	const best = pick(containing) ?? pick(overlapping)
	if (best) return { el: best.el, lines: codeLines(best.el, best.src, line) }
	const found = findByText(root, all, line, hints)
	return found ? { el: found, lines: [] } : null
}

const normText = (s: string) => s.replace(/\s+/g, ' ').trim()
const SKIP = 'script, style, template, katachi-review-root, katachi-feedback-root, astro-dev-toolbar'

/**
 * 行を含む要素が無いとき（タブの見出し・LinkCard の属性など、コンポーネントの行）、前後の要素のあいだにある文字から探す。
 * 前 = 行より前で終わるいちばん後ろの要素、後 = 行より後ろで始まるいちばん前の要素。その間（文書の順）で、手がかりの文字を含むいちばん内側の要素。
 * 本文の外（ヘッダー・サイドバー）は見ない（main の中だけ）。見つからなければ null（ページの見出しに出す）。
 */
function findByText(root: ParentNode, all: { el: Element; src: Src }[], line: LineRange, hints: readonly string[]): Element | null {
	if (!hints.length) return null
	const main = root.querySelector('main') ?? root.querySelector('body')
	if (!main) return null
	let prev: Element | null = null
	let next: Element | null = null
	for (const c of all) {
		if (c.src.end < line.start && (!prev || prev.compareDocumentPosition(c.el) & Node.DOCUMENT_POSITION_FOLLOWING)) prev = c.el
		if (c.src.start > line.end && (!next || next.compareDocumentPosition(c.el) & Node.DOCUMENT_POSITION_PRECEDING)) next = c.el
	}
	const inRange = (el: Element) =>
		!el.closest(SKIP) &&
		(!prev || (prev.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING && !prev.contains(el))) &&
		(!next || (next.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_PRECEDING && !el.contains(next)))
	const elements = [...main.querySelectorAll('*')].filter(inRange)
	for (const hint of hints) {
		const h = normText(hint)
		let hit = elements.find((el) => normText(el.textContent ?? '').includes(h))
		if (!hit) continue
		for (let deeper: Element | undefined = hit; deeper; ) {
			hit = deeper
			deeper = [...hit.children].find((c) => inRange(c) && normText(c.textContent ?? '').includes(h))
		}
		return hit
	}
	return null
}

/**
 * コードブロック（EC の `.expressive-code`）の中の行。フェンスの開きの行（src.start）の次がコードの 1 行目。
 * 見えない行（畳まれた部分）や、行の数が合わないときは空（ブロック全体を光らせる）。
 */
export function codeLines(el: Element, src: Src, line: LineRange): Element[] {
	if (!el.matches('.expressive-code') && !el.querySelector('.ec-line')) return []
	const rows = [...el.querySelectorAll('.ec-line')]
	const from = line.start - src.start - 1
	const to = Math.min(line.end, src.end - 1) - src.start - 1
	if (from < 0 || to < from || to >= rows.length) return []
	const picked = rows.slice(from, to + 1)
	return picked.every(visible) ? picked : []
}

/** コードの行を畳んでいる入れ物（expressive-code-collapsible の畳んだ状態）。無ければ null。 */
export const collapsedOf = (t: Target): HTMLElement | null =>
	(t.lines[0]?.closest('.ec-collapse--collapsed') as HTMLElement | null) ?? null

/** 畳んだコードブロックを開く（「Show more」を押す）。開いたら true。 */
export function expandCode(t: Target): boolean {
	const box = collapsedOf(t)
	const toggle = box?.querySelector<HTMLElement>(':scope > .ec-collapse__toggle, .ec-collapse__toggle')
	if (!toggle) return false
	toggle.click()
	return true
}

const union = (rects: DOMRect[]) => {
	const left = Math.min(...rects.map((r) => r.left))
	const top = Math.min(...rects.map((r) => r.top))
	const right = Math.max(...rects.map((r) => r.right))
	const bottom = Math.max(...rects.map((r) => r.bottom))
	return new DOMRect(left, top, right - left, bottom - top)
}

/**
 * 印・枠を置く矩形（コードの行があればその行たち、無ければ要素。見えていなければ見えている祖先）。
 * 畳んだコードブロックの中の行は、見えている部分に切り詰める。見えている部分に無ければブロック全体。
 */
export function rectOfTarget(t: Target): DOMRect {
	if (!t.lines.length) return shownOf(t.el).getBoundingClientRect()
	let r = union(t.lines.map((e) => e.getBoundingClientRect()))
	// 長い行は横にはみ出す（pre の中で横スクロール）ので、見えている幅に切り詰める。
	const pre = t.lines[0]?.closest('pre')?.getBoundingClientRect()
	if (pre && pre.width > 0) {
		const left = Math.max(r.left, pre.left)
		const right = Math.min(r.right, pre.right)
		if (right > left) r = new DOMRect(left, r.top, right - left, r.height)
	}
	const box = collapsedOf(t)?.querySelector('.ec-collapse__content')?.getBoundingClientRect()
	if (!box) return r
	const top = Math.max(r.top, box.top)
	const bottom = Math.min(r.bottom, box.bottom)
	return bottom > top ? new DOMRect(r.left, top, r.width, bottom - top) : t.el.getBoundingClientRect()
}
