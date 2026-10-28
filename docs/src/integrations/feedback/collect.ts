import type { Section, SectionKey } from './protocol'

/** 利用者が選んだもの。要素か、ドラッグで選んだテキストのどちらか。 */
export type Pick =
	| { kind: 'element'; el: Element }
	| { kind: 'text'; range: Range; text: string }

const clip = (s: string, n: number) => (s.length > n ? s.slice(0, n) + '…' : s)
const squash = (s: string) => s.replace(/\s+/g, ' ').trim()

/** id があればそれ、無ければ祖先をたどって `tag:nth-of-type(n)` をつなぐ。 */
export function cssSelector(el: Element): string {
	const parts: string[] = []
	let cur: Element | null = el
	while (cur && cur !== document.documentElement) {
		if (cur.id && document.querySelectorAll(`#${CSS.escape(cur.id)}`).length === 1) {
			parts.unshift(`#${CSS.escape(cur.id)}`)
			break
		}
		const tag = cur.tagName.toLowerCase()
		const sameTag = cur.parentElement ? [...cur.parentElement.children].filter((c) => c.tagName === cur!.tagName) : []
		parts.unshift(sameTag.length > 1 ? `${tag}:nth-of-type(${sameTag.indexOf(cur) + 1})` : tag)
		cur = cur.parentElement
	}
	return parts.join(' > ')
}

const rectOf = (r: DOMRect) =>
	`x=${Math.round(r.x)}, y=${Math.round(r.y)}, width=${Math.round(r.width)}, height=${Math.round(r.height)}`

/** 選んだテキストの前後の文脈。選択を含む最も近いブロックのテキストから切り出す。 */
function textContext(range: Range, text: string): { before: string; after: string } {
	const node = range.commonAncestorContainer
	const block = (node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as Element))?.closest('p, li, td, th, h1, h2, h3, h4, h5, h6, pre, div') ?? document.body
	// 選択文字列は表示上の空白（改行は半角スペース）なので、ソース側の空白もそろえてから探す。
	const full = squash(block.textContent ?? '')
	const needle = squash(text)
	const i = full.indexOf(needle)
	if (i < 0) return { before: '', after: '' }
	return { before: full.slice(Math.max(0, i - 80), i), after: full.slice(i + needle.length, i + needle.length + 80) }
}

/**
 * ダイアログを開いた時点の値を集める。選んだものに当てはまらない節（要素を選んでいないのに「選んだ要素」など）は
 * キーごと作らない。ダイアログはこの結果を見て、出す項目を決める。
 */
export function collect(pick: Pick, now = new Date()): Partial<Record<SectionKey, Section>> {
	const sections: Partial<Record<SectionKey, Section>> = {}
	sections.page = [
		['URL', location.href],
		['title', document.title],
		['言語', document.documentElement.lang || '(未指定)'],
	]
	if (pick.kind === 'element') {
		const { el } = pick
		sections.element = [
			['セレクタ', cssSelector(el)],
			['タグ', el.tagName.toLowerCase()],
			['テキストの抜粋', clip(squash(el.textContent ?? ''), 200)],
			['outerHTML の抜粋', clip(el.outerHTML, 500)],
		]
	} else {
		const ctx = textContext(pick.range, pick.text)
		sections.text = [
			['選んだテキスト', pick.text],
			['前の文脈', ctx.before],
			['後ろの文脈', ctx.after],
		]
	}
	const rect = pick.kind === 'element' ? pick.el.getBoundingClientRect() : pick.range.getBoundingClientRect()
	sections.position = [
		['getBoundingClientRect', rectOf(rect)],
		['scroll', `scrollX=${Math.round(scrollX)}, scrollY=${Math.round(scrollY)}`],
	]
	const theme = document.documentElement.dataset.theme ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
	sections.screen = [
		['viewport', `${innerWidth} x ${innerHeight}`],
		['devicePixelRatio', String(devicePixelRatio)],
		['テーマ', theme === 'dark' ? 'ダーク' : 'ライト'],
	]
	sections.browser = [['UserAgent', navigator.userAgent]]
	sections.datetime = [['日時', now.toISOString()]]
	return sections
}
