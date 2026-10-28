// 選んだもの（Pick）と、その場所の情報。Feedback と Memo の両方が使う。
// 場所の情報（source・location の節）はここで作り、Feedback は節にして送り、Memo は構造化して保存する。
import type { Section } from '../core/protocol'
import { blockOf, fragmentParts, fragmentUrl, norm, offsetIn, previousText, squash, type FragmentParts } from './fragment'
import { cssSelectorOf, landmarkOf, nearestHeading, xpathOf } from './locate'
import { findForElement, findForRange, formatSrc, pageSourceFile } from './srcmap'

/** 利用者が選んだもの。要素か、ドラッグで選んだテキストのどちらか。 */
export type Pick =
	| { kind: 'element'; el: Element }
	| { kind: 'text'; range: Range; text: string }
	/** 何も選ばず、ページ全体・サイト全体について書く。 */
	| { kind: 'page' }

/** 選んだものの画面上の矩形。要素なら要素、テキストなら選択範囲の外接矩形。 */
export function rectOfPick(pick: Exclude<Pick, { kind: 'page' }>): DOMRect {
	return pick.kind === 'element' ? pick.el.getBoundingClientRect() : pick.range.getBoundingClientRect()
}

/** 選んだものを含む要素（テキストなら範囲全体を含む最も内側の要素）。 */
export const containerOf = (pick: Exclude<Pick, { kind: 'page' }>): Element => {
	if (pick.kind === 'element') return pick.el
	const node = pick.range.commonAncestorContainer
	return node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement ?? document.body
}

/** 値が無いものは行ごと作らない（読み手に「不明」を書くのは source 節だけ）。 */
export const present = (rows: Array<[string, string | null | undefined]>): Section =>
	rows.filter((r): r is [string, string] => r[1] !== null && r[1] !== undefined && r[1] !== '')

export function sourceSection(pick: Exclude<Pick, { kind: 'page' }>): Section {
	const page = pageSourceFile()
	const pageRow: [string, string] = ['ページのソースファイル', page.guessed ? `${page.path}（URL からの推定）` : page.path]
	if (pick.kind === 'element') {
		const found = findForElement(pick.el)
		return found
			? [['ソース', formatSrc(found.src)], ['拾った要素', found.via], pageRow]
			: [['ソース', '不明（この要素の近くに行番号が付いていません）'], pageRow]
	}
	const { start, end, merged } = findForRange(pick.range)
	const rows: Array<[string, string | null]> = [['ソース', merged ? formatSrc(merged) : '不明（この範囲の近くに行番号が付いていません）']]
	if (start && end && formatSrc(start.src) !== formatSrc(end.src)) {
		rows.push(['開始のソース', `${formatSrc(start.src)}（${start.via}）`], ['終了のソース', `${formatSrc(end.src)}（${end.via}）`])
	} else if (start ?? end) {
		rows.push(['拾った要素', (start ?? end)!.via])
	}
	return present([...rows, pageRow])
}

export function locationSection(pick: Exclude<Pick, { kind: 'page' }>): Section {
	const container = containerOf(pick)
	const heading = nearestHeading(pick.kind === 'element' ? pick.el : pick.range.startContainer)
	const lang = document.documentElement.lang
	const pageText = norm(document.body.innerText ?? '')
	let parts: FragmentParts | null = null
	if (pick.kind === 'text') {
		const block = blockOf(pick.range.startContainer)
		const full = squash(block.textContent ?? '')
		const before = previousText(block)
		const first = squash(pick.text.split('\n').find((l) => l.trim()) ?? pick.text)
		// 範囲の開始位置で一致を確かめ、ずれていれば最初の出現にする。
		const off = offsetIn(block, pick.range.startContainer, pick.range.startOffset)
		const i = off !== null && full.startsWith(first, off) ? off : full.indexOf(first)
		const endBlock = blockOf(pick.range.endContainer)
		if (i >= 0 && endBlock !== block) {
			// 複数のブロックにまたがる選択: 開始は最初のブロックの選択の先頭から、終了は最後のブロックの選択の終わりまで。
			// 終わりの単語の境界は、最後のブロック全体の文字列で決める（選んだ文字列の端は単語の途中かもしれない）。
			const endFull = squash(endBlock.textContent ?? '')
			const endOff = offsetIn(endBlock, pick.range.endContainer, pick.range.endOffset)
			const head = fragmentParts(full, i, i + 24, lang, pageText, before)
			const tail = endOff ? fragmentParts(endFull, Math.max(0, endOff - 24), endOff, lang, pageText) : null
			parts = head && tail ? { prefix: head.prefix, head: head.head, tail: tail.head } : head
		} else if (i >= 0) {
			parts = fragmentParts(full, i, i + first.length, lang, pageText, before)
		}
	} else {
		const full = squash((pick.el as HTMLElement).innerText ?? pick.el.textContent ?? '')
		// 要素は、そのテキストの先頭から（先頭 40 字ぶん）。
		parts = full ? fragmentParts(full, 0, Math.min(full.length, 40), lang, pageText, previousText(pick.el)) : null
	}
	const fragment = parts ? fragmentUrl(parts) : null
	return present([
		['XPath', xpathOf(container)],
		['CSSセレクタ', cssSelectorOf(container) || null],
		['近くの見出し', heading ? `${'#'.repeat(heading.level)} ${heading.text}` : null],
		['見出しの階層', heading?.path],
		['見出しのアンカーURL', heading?.anchor],
		['テキストフラグメントURL', fragment],
		['ランドマーク', landmarkOf(container)],
	])
}
