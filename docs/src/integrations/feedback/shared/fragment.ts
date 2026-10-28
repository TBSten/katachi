// ブロック（段落など）の全文と、ブロックの中の文字位置、テキストフラグメントの URL（`#:~:text=開始,終了`）を作る。

export const norm = (s: string) => s.replace(/\s+/g, ' ')
export const squash = (s: string) => norm(s).trim()
export const clip = (s: string, n: number) => (s.length > n ? s.slice(0, n) + '…' : s)

const BLOCKS = 'p, li, td, th, h1, h2, h3, h4, h5, h6, pre, blockquote, dd, dt, figcaption, summary'

/** 選んだ場所を含むブロック。要素なら、それがブロックならそのもの。 */
export function blockOf(node: Node): Element {
	const el = node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement
	return el?.closest(BLOCKS) ?? el?.closest('div, section, article') ?? document.body
}

/** ブロックの全文（空白を詰め、上限で切る）。 */
export function blockText(block: Element, max = 2000): string {
	return clip(squash(block.textContent ?? ''), max)
}

/** ブロックの中での文字位置（blockText と同じ詰め方での 0 始まり）。見つからなければ null。 */
export function offsetIn(block: Element, node: Node, offset: number): number | null {
	try {
		const full = norm(block.textContent ?? '')
		const lead = full.length - full.trimStart().length
		const r = document.createRange()
		r.setStart(block, 0)
		r.setEnd(node, offset)
		return Math.max(0, norm(r.toString()).length - lead)
	} catch {
		return null
	}
}

/**
 * ブロックの直前にある、文字を持つ最も近い兄弟の最後の 1 行（innerText の改行 = ブロックの境目なので、行の頭は必ず単語の境界）。
 * ブロックの先頭から始まるフラグメントの prefix に使う。
 */
export function previousText(block: Element): string {
	for (let cur: Element | null = block; cur && cur !== document.body; cur = cur.parentElement) {
		for (let sib = cur.previousElementSibling; sib; sib = sib.previousElementSibling) {
			const t = squash(((sib as HTMLElement).innerText ?? '').trimEnd().split('\n').pop() ?? '')
			if (t) return t
		}
	}
	return ''
}

/** 単語の切れ目（日本語も ICU の分かち書きで切る）。テキストフラグメントは単語の境界でしか一致しない。 */
function boundaries(text: string, lang: string): Set<number> {
	const set = new Set<number>([0, text.length])
	try {
		for (const seg of new Intl.Segmenter(lang || 'ja', { granularity: 'word' }).segment(text)) {
			set.add(seg.index)
			set.add(seg.index + seg.segment.length)
		}
	} catch {
		// Segmenter が無い環境では境界を足さない（端と全文の端だけ）。
	}
	return set
}

const snapDown = (b: Set<number>, i: number) => { while (i > 0 && !b.has(i)) i--; return i }
const snapUp = (b: Set<number>, i: number, max: number) => { while (i < max && !b.has(i)) i++; return i }

/** URL に載せる形。`-` と `,` はフラグメントの区切りなので必ず %エンコードする。 */
const enc = (s: string) => encodeURIComponent(s).replace(/-/g, '%2D')

export type FragmentParts = { prefix: string; head: string; tail: string | null }

/**
 * `full` の [start, end) を指すテキストフラグメントの部品。
 * 短ければ全文、長ければ先頭と末尾を `開始,終了` にする。先頭の文字列がページに複数あれば、直前の文字を prefix に足す。
 */
export function fragmentParts(full: string, start: number, end: number, lang: string, pageText: string, before = ''): FragmentParts | null {
	const b = boundaries(full, lang)
	const s = snapDown(b, Math.max(0, start))
	const e = snapUp(b, Math.min(full.length, end), full.length)
	if (e <= s) return null
	const WHOLE = 60
	const PIECE = 24
	let head: string
	let tail: string | null = null
	if (e - s <= WHOLE) {
		head = full.slice(s, e)
	} else {
		head = full.slice(s, snapUp(b, s + PIECE, e))
		tail = full.slice(snapDown(b, e - PIECE), e).trim()
	}
	head = head.trim()
	if (!head) return null
	let prefix = ''
	if (pageText.split(head).length > 2) {
		// ページに同じ文字列が複数ある。直前の文字（ブロックの先頭なら直前のブロックの最終行）を prefix にし、
		// 「prefix + 開始」がページで 1 つになるまで 12 → 24 → 48 → 96 字と伸ばす。
		const ctx = (before ? `${before} ` : '') + full.slice(0, s)
		const cb = boundaries(ctx, lang)
		for (const len of [12, 24, 48, 96]) {
			const raw = ctx.slice(snapDown(cb, Math.max(0, ctx.length - len)))
			const cand = raw.trim()
			if (!cand) break
			prefix = cand
			// 空白も含めた生の並び（ブロックの境目の空白もそのまま）で、ページにちょうど 1 か所だけあれば足りる。
			if (pageText.split(norm(raw + full.slice(s, e))).length === 2) break
		}
	}
	return { prefix, head, tail }
}

/** 部品を `#:~:text=` の URL にする。 */
export function fragmentUrl(p: FragmentParts): string {
	const directive = `${p.prefix ? `${enc(p.prefix)}-,` : ''}${enc(p.head)}${p.tail ? `,${enc(p.tail)}` : ''}`
	return `${location.origin}${location.pathname}${location.search}#:~:text=${directive}`
}
