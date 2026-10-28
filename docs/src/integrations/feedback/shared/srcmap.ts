// 選んだものから、元の Markdown / MDX のどのファイルの何行目かを求める（ブラウザ側）。
// 本文の要素には dev のときだけ `data-fb-src="path:12-14"` が付いている（source.ts）。
import { SRC_ATTR } from '../core/protocol'

export type Src = { path: string; start: number; end: number }

/** どの要素の属性から拾ったか。要素自身以外は、近い所から拾ったことを読み手に伝える。 */
export type Found = { src: Src; via: string }

export function parseSrc(value: string | null | undefined): Src | null {
	const m = /^(.+):(\d+)(?:-(\d+))?$/.exec(value ?? '')
	return m ? { path: m[1]!, start: Number(m[2]), end: Number(m[3] ?? m[2]) } : null
}

export const formatSrc = (s: Src) => `${s.path}:${s.start === s.end ? s.start : `${s.start}-${s.end}`}`

const tagOf = (el: Element) => el.tagName.toLowerCase()
const srcOf = (el: Element) => parseSrc(el.getAttribute(SRC_ATTR))

/** 複数の範囲を1つにまとめる（同じファイルのものだけ。別のファイルが混ざれば最初のファイルのものだけ）。 */
export function union(list: Src[]): Src | null {
	const first = list[0]
	if (!first) return null
	const same = list.filter((s) => s.path === first.path)
	return { path: first.path, start: Math.min(...same.map((s) => s.start)), end: Math.max(...same.map((s) => s.end)) }
}

/**
 * 要素の位置。要素自身 → 子孫の範囲 → 最も近い祖先 → 直前の兄弟、の順で探す。
 * 自身に無くても子孫が持っていれば、祖先（より広い範囲）よりそちらのほうが正確。
 */
export function findForElement(el: Element): Found | null {
	const own = srcOf(el)
	if (own) return { src: own, via: `${tagOf(el)}（要素自身）` }
	const inner = union([...el.querySelectorAll(`[${SRC_ATTR}]`)].map(srcOf).filter((s): s is Src => s !== null))
	if (inner) return { src: inner, via: `${tagOf(el)} の子孫の範囲` }
	const up = el.parentElement?.closest(`[${SRC_ATTR}]`)
	if (up) return { src: srcOf(up)!, via: `${tagOf(up)}（祖先）` }
	for (let cur: Element | null = el; cur; cur = cur.parentElement) {
		for (let sib = cur.previousElementSibling; sib; sib = sib.previousElementSibling) {
			const hit = srcOf(sib) ?? srcOf(sib.querySelector(`[${SRC_ATTR}]`) ?? sib)
			if (hit) return { src: hit, via: `${tagOf(sib)}（直前の兄弟。付いていなかったので近くから推定）` }
		}
	}
	return null
}

const elementOf = (node: Node) => (node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement)

/** テキストの範囲の位置。開始と終了それぞれの最も近い属性から拾い、またがる場合は開始から終了までにする。 */
export function findForRange(range: Range): { start: Found | null; end: Found | null; merged: Src | null } {
	const at = (node: Node, offset: number, isStart: boolean) => {
		// 要素の境界を指す範囲（ブロックごと選んだときなど）は、その境界の子を基準にする。
		let el = elementOf(node)
		if (node.nodeType === Node.ELEMENT_NODE) {
			const child = (node as Element).childNodes[isStart ? offset : Math.max(0, offset - 1)]
			if (child) el = elementOf(child) ?? el
		}
		return el ? findForElement(el) : null
	}
	const start = at(range.startContainer, range.startOffset, true)
	const end = at(range.endContainer, range.endOffset, false)
	const merged = start && end && start.src.path === end.src.path
		? { path: start.src.path, start: Math.min(start.src.start, end.src.start), end: Math.max(start.src.end, end.src.end) }
		: (start ?? end)?.src ?? null
	return { start, end, merged }
}

/**
 * ページ全体のソースファイル。本文のどれかの属性のパスを使い、無ければ URL から推定する。
 * 推定は `/katachi/ja/guides/x/` → `docs/src/content/docs/ja/guides/x.mdx`（拡張子は見て確かめられないので .mdx と仮定）。
 */
export function pageSourceFile(): { path: string; guessed: boolean } {
	const hit = document.querySelector(`main [${SRC_ATTR}]`)
	const src = hit ? srcOf(hit) : null
	if (src) return { path: src.path, guessed: false }
	const base = import.meta.env.BASE_URL.replace(/\/$/, '')
	const rel = location.pathname.slice(base.length).replace(/^\/|\/$/g, '')
	return { path: `docs/src/content/docs/${rel || 'index'}.mdx`, guessed: true }
}
