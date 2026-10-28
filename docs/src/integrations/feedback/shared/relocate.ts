// 保存した場所の情報から、今のページ上の要素や範囲を引き直す（ブラウザ側）。Memo の印・「場所を表示」に使う。
// 試す順: XPath → CSS セレクタ → テキストフラグメント → ソースの行。テキストの選択だけは、範囲まで戻せるテキストフラグメントを先に試す。
import { SRC_ATTR } from '../core/protocol'
import { parseSrc } from './srcmap'

/** 引き直しの手がかり。無いものは試さない。 */
export type Place = {
	kind?: 'element' | 'text'
	xpath?: string
	selector?: string
	/** `#:~:text=` 付きの URL。 */
	fragment?: string
	/** `path:12-14`。 */
	source?: string
}

export type How = 'xpath' | 'selector' | 'fragment' | 'source'

/** 引き直した結果。range があれば文字の範囲まで戻せた（テキストフラグメント）。el はその範囲を含む要素。 */
export type Relocated = { how: How; el: Element; range?: Range }

/** 画面上の矩形。 */
export const rectOfRelocated = (r: Relocated): DOMRect => (r.range ?? r.el).getBoundingClientRect()

/** XPathResult.ORDERED_NODE_SNAPSHOT_TYPE（happy-dom には XPathResult の定数が無いので数で書く）。 */
const ORDERED_NODE_SNAPSHOT_TYPE = 7

/** XPath がちょうど 1 つの要素に当たるならそれ。 */
export function byXPath(xpath: string, doc: Document = document): Element | null {
	try {
		const r = doc.evaluate(xpath, doc, null, ORDERED_NODE_SNAPSHOT_TYPE, null)
		return r.snapshotLength === 1 ? (r.snapshotItem(0) as Element) : null
	} catch {
		return null
	}
}

/** セレクタがちょうど 1 つの要素に当たるならそれ。 */
export function bySelector(selector: string, doc: Document = document): Element | null {
	try {
		const all = doc.querySelectorAll(selector)
		return all.length === 1 ? all[0]! : null
	} catch {
		return null
	}
}

/**
 * `path:start-end` に最も合う `data-fb-src` の要素。範囲が一致するもの → 範囲を含むもの（狭い順）→ 重なるもの（狭い順）。
 */
export function bySource(source: string, doc: Document = document): Element | null {
	const want = parseSrc(source)
	if (!want) return null
	let best: { el: Element; rank: number; span: number } | null = null
	for (const el of doc.querySelectorAll(`[${SRC_ATTR}]`)) {
		const s = parseSrc(el.getAttribute(SRC_ATTR))
		if (!s || s.path !== want.path) continue
		const span = s.end - s.start
		const rank = s.start === want.start && s.end === want.end ? 0 : s.start <= want.start && s.end >= want.end ? 1 : s.start <= want.end && s.end >= want.start ? 2 : 3
		if (rank === 3) continue
		if (!best || rank < best.rank || (rank === best.rank && span < best.span)) best = { el, rank, span }
	}
	return best?.el ?? null
}

// ---- テキストフラグメント ----

export type FragmentSpec = { prefix: string | null; head: string; tail: string | null }

/** `...#:~:text=prefix-,head,tail` を分解する。text= が無ければ null。 */
export function parseFragment(url: string): FragmentSpec | null {
	const i = url.indexOf('#:~:text=')
	if (i < 0) return null
	try {
		const parts = url.slice(i + '#:~:text='.length).split('&')[0]!.split(',').map(decodeURIComponent)
		let prefix: string | null = null
		if (parts[0]?.endsWith('-') && parts.length >= 2) prefix = parts.shift()!.slice(0, -1)
		const head = parts.shift()
		if (!head) return null
		return { prefix, head, tail: parts[0] ?? null }
	} catch {
		return null
	}
}

const SKIP = 'script, style, noscript, template, astro-dev-toolbar'
const BLOCK = 'p, li, td, th, h1, h2, h3, h4, h5, h6, pre, blockquote, dd, dt, figcaption, summary, div, section, article, tr'

type Indexed = { text: string; at: Array<{ node: Text; offset: number }> }

/** ページの文字を、空白を 1 つに詰めた 1 本の文字列にする。各文字が元のどのテキストノードの何文字目かも持つ。 */
function indexText(doc: Document): Indexed {
	let text = ''
	const at: Indexed['at'] = []
	let lastBlock: Element | null = null
	const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT)
	for (let n = walker.nextNode() as Text | null; n; n = walker.nextNode() as Text | null) {
		const parent = n.parentElement
		if (!parent || parent.closest(SKIP)) continue
		const block = parent.closest(BLOCK)
		// ブロックが変わるところは、空白があるものとして扱う（段落をまたいだ単語がくっつかないように）。
		if (text && block !== lastBlock && !text.endsWith(' ')) {
			text += ' '
			at.push({ node: n, offset: 0 })
		}
		lastBlock = block
		const raw = n.data
		for (let i = 0; i < raw.length; i++) {
			if (/\s/.test(raw[i]!)) {
				if (text.endsWith(' ')) continue
				text += ' '
			} else {
				text += raw[i]
			}
			at.push({ node: n, offset: i })
		}
	}
	return { text, at }
}

/** テキストフラグメントの指す文字の範囲。prefix・tail まで合うものだけ。無ければ null。 */
export function byFragment(url: string, doc: Document = document): Range | null {
	const spec = parseFragment(url)
	if (!spec) return null
	const { text, at } = indexText(doc)
	const squash = (s: string) => s.replace(/\s+/g, ' ').trim()
	const head = squash(spec.head)
	const prefix = spec.prefix ? squash(spec.prefix) : null
	const tail = spec.tail ? squash(spec.tail) : null
	for (let i = text.indexOf(head); i >= 0; i = text.indexOf(head, i + 1)) {
		if (prefix && !text.slice(0, i).trimEnd().endsWith(prefix)) continue
		let end = i + head.length
		if (tail) {
			const j = text.indexOf(tail, end)
			if (j < 0) continue
			end = j + tail.length
		}
		const a = at[i]
		const b = at[end - 1]
		if (!a || !b) continue
		const range = doc.createRange()
		range.setStart(a.node, a.offset)
		range.setEnd(b.node, b.offset + 1)
		return range
	}
	return null
}

const elementOf = (n: Node): Element => (n.nodeType === Node.ELEMENT_NODE ? (n as Element) : n.parentElement ?? (n.ownerDocument ?? document).body)

/** 手がかりから今のページ上の場所を引き直す。どれでも見つからなければ null。 */
export function relocate(place: Place, doc: Document = document): Relocated | null {
	const tryFragment = (): Relocated | null => {
		const range = place.fragment ? byFragment(place.fragment, doc) : null
		return range ? { how: 'fragment', el: elementOf(range.commonAncestorContainer), range } : null
	}
	const tryElement = (how: How, el: Element | null): Relocated | null => (el ? { how, el } : null)
	const steps: Array<() => Relocated | null> = [
		() => (place.xpath ? tryElement('xpath', byXPath(place.xpath, doc)) : null),
		() => (place.selector ? tryElement('selector', bySelector(place.selector, doc)) : null),
		tryFragment,
		() => (place.source ? tryElement('source', bySource(place.source, doc)) : null),
	]
	// テキストの選択は、範囲まで戻せるテキストフラグメントを先に試す（XPath・セレクタは範囲を含む要素までしか戻せない）。
	if (place.kind === 'text') steps.unshift(steps.splice(2, 1)[0]!)
	for (const step of steps) {
		const hit = step()
		if (hit) return hit
	}
	return null
}
