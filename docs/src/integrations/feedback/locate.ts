// 選んだ場所を「ページ上で迷わず同じ場所を指す」ための情報にする（ブラウザ側）: XPath・CSS セレクタ・近くの見出し・ランドマーク。
// XPath とセレクタは、保存の前に実際に引き直して元の要素に戻ることを確かめ、戻らなければ null を返す（書かない）。

const XHTML = 'http://www.w3.org/1999/xhtml'

const quoteXPath = (s: string) => (s.includes('"') ? `'${s}'` : `"${s}"`)

/** 同じ要素名の兄弟の中での 1 始まりの位置。 */
function siblingIndex(el: Element): number {
	let n = 1
	for (let s = el.previousElementSibling; s; s = s.previousElementSibling) if (s.localName === el.localName && s.namespaceURI === el.namespaceURI) n++
	return n
}

const xpathName = (el: Element) => (el.namespaceURI === XHTML ? el.localName : `*[local-name()=${quoteXPath(el.localName)}]`)

/** XPath を引いて、ちょうど 1 つの要素に当たるならそれを返す。 */
function evalXPath(xpath: string): Element | null {
	try {
		const r = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null)
		return r.snapshotLength === 1 ? (r.snapshotItem(0) as Element) : null
	} catch {
		return null
	}
}

/**
 * id が一意ならそれを起点に、無ければ `/html/body/...` から、index 付きの XPath にする。
 * 引き直して `el` に戻らなければ null。
 */
export function xpathOf(el: Element): string | null {
	const steps: string[] = []
	for (let cur: Element | null = el; cur; cur = cur.parentElement) {
		if (cur.id && !cur.id.includes('"') && document.querySelectorAll(`[id=${JSON.stringify(cur.id)}]`).length === 1) {
			steps.unshift(`//*[@id=${quoteXPath(cur.id)}]`)
			break
		}
		steps.unshift(`/${xpathName(cur)}[${siblingIndex(cur)}]`)
	}
	const xpath = steps.join('')
	return evalXPath(xpath) === el ? xpath : null
}

// ---- CSS セレクタ ----

/** 手がかりにしてよい class。Starlight の `sl-` で始まるものだけ（Astro のハッシュ付き `astro-xxxx` や Tailwind の class は使わない）。 */
const stableClasses = (el: Element) => [...el.classList].filter((c) => /^sl-[\w-]+$/.test(c))
const LANDMARKS = new Set(['main', 'header', 'nav', 'aside', 'footer', 'article', 'section'])

const idUnique = (id: string) => document.querySelectorAll(`#${CSS.escape(id)}`).length === 1

/** 兄弟の中で見分けが要るなら `:nth-of-type(n)` を付けた 1 段ぶん。 */
function segment(el: Element, withClass: boolean): string {
	const tag = el.localName
	const same = el.parentElement ? [...el.parentElement.children].filter((c) => c.localName === tag) : [el]
	const cls = withClass ? stableClasses(el).slice(0, 1).map((c) => `.${CSS.escape(c)}`).join('') : ''
	return `${tag}${cls}${same.length > 1 ? `:nth-of-type(${siblingIndex(el)})` : ''}`
}

/** 起点にできる祖先の書き方（id・ランドマーク・sl- の class）。無ければ null。 */
function anchorOf(el: Element): string | null {
	if (el.id && idUnique(el.id)) return `#${CSS.escape(el.id)}`
	if (LANDMARKS.has(el.localName) && document.querySelectorAll(el.localName).length === 1) return el.localName
	const cls = stableClasses(el)[0]
	return cls ? `${el.localName}.${CSS.escape(cls)}` : null
}

const hitsOnly = (sel: string, el: Element) => {
	try {
		const all = document.querySelectorAll(sel)
		return all.length === 1 && all[0] === el
	} catch {
		return false
	}
}

/**
 * 一意に当たる中で一番短い CSS セレクタ。
 * 候補は「起点（id・ランドマーク・sl- の class を持つ祖先）+ そこから要素までの末尾 k 段」と、起点なしの末尾 k 段。
 * 最後は必ず当たる全段の `html > body > …` を保険に持つ。
 */
export function cssSelectorOf(el: Element): string {
	const chain: Element[] = []
	for (let cur: Element | null = el; cur && cur !== document.documentElement; cur = cur.parentElement) chain.push(cur)
	const candidates: string[] = []
	// chain[0] が el。末尾 k 段 = chain[k-1..0]。段ごとに class あり・なしの 2 通りを試す。
	const tail = (k: number, withClass: boolean) => chain.slice(0, k).reverse().map((e) => segment(e, withClass)).join(' > ')
	for (let k = 1; k <= chain.length; k++) {
		for (const withClass of [false, true]) {
			candidates.push(tail(k, withClass))
			for (let a = k; a < chain.length; a++) {
				const anchor = anchorOf(chain[a]!)
				// 起点そのものの段は anchor の書き方で置き換え、間の段は子孫結合子で省いて末尾 k 段だけ残す。
				if (anchor) candidates.push(`${anchor} ${tail(k, withClass)}`, ...(a === k ? [`${anchor} > ${tail(k, withClass)}`] : []))
			}
		}
	}
	candidates.push(`html > body > ${chain.slice().reverse().map((e) => segment(e, false)).join(' > ')}`)
	const ok = candidates.filter((c) => c && hitsOnly(c, el))
	ok.sort((a, b) => a.length - b.length)
	return ok[0] ?? ''
}

// ---- 近くの見出し・ランドマーク ----

export type HeadingInfo = { text: string; id: string; level: number; path: string; anchor: string | null }

/** 見出しの文字（アンカーの記号などを含まない）。 */
const headingText = (h: Element) => (h.textContent ?? '').replace(/\s+/g, ' ').trim()

/**
 * 選んだ場所より前（見出し自身の中を含む）で最も近い見出し。階層（h2 > h3）は、手前の見出しを積んで求める。
 * 本文（main）の中の見出しだけを見る。無ければ null。
 */
export function nearestHeading(target: Node): HeadingInfo | null {
	const stack: Element[] = []
	for (const h of document.querySelectorAll('main h1, main h2, main h3, main h4, main h5, main h6')) {
		const pos = h.compareDocumentPosition(target)
		// 見出しが target より後ろ（target が見出しの前）なら、そこで終わり。target が見出しの中なら含める。
		if (!(pos & Node.DOCUMENT_POSITION_FOLLOWING) && !(pos & Node.DOCUMENT_POSITION_CONTAINED_BY) && h !== target) break
		const level = Number(h.localName[1])
		while (stack.length && Number(stack[stack.length - 1]!.localName[1]) >= level) stack.pop()
		stack.push(h)
	}
	const h = stack[stack.length - 1]
	if (!h) return null
	const url = location.origin + location.pathname + location.search
	return {
		text: headingText(h),
		id: h.id,
		level: Number(h.localName[1]),
		path: stack.map((x) => `${x.localName} ${headingText(x)}`).join(' > '),
		anchor: h.id ? `${url}#${h.id}` : null,
	}
}

/** 選んだ場所が、ページのどの領域にあるか。 */
export function landmarkOf(el: Element): string {
	const table: Array<[string, string]> = [
		['main', '本文（main）'],
		['#starlight__sidebar, nav.sidebar', 'サイドバー'],
		['.right-sidebar-container, starlight-toc', '目次'],
		['header', 'ヘッダ'],
		['footer', 'フッタ'],
	]
	for (const [sel, name] of table) if (el.closest(sel)) return name
	return 'その他'
}
