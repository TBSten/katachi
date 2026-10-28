// 今見ているページの情報と、選んだものの場所の情報（ブラウザ側）。場所の取り方は shared/pick.ts（Feedback と同じ）を使う。
import type { Section } from '../core/protocol'
import { clip, squash } from '../shared/fragment'
import { locationSection, sourceSection, type SinglePick } from '../shared/pick'
import { pageSourceFile } from '../shared/srcmap'
import type { Locator, MemoPage } from './model'

/** `location.pathname` から base と言語の接頭辞を除いたもの。`/katachi/ja/guides/x/` → `guides/x/`。 */
export function pagePathOf(pathname: string, base: string, lang: string): string {
	let rest = pathname.startsWith(base) ? pathname.slice(base.length) : pathname
	rest = rest.replace(/^\/+/, '')
	const first = rest.split('/')[0]
	if (first === lang) rest = rest.slice(first.length).replace(/^\/+/, '')
	return rest
}

export type PageInfo = { page: MemoPage; pagePath: string }

export function pageInfo(): PageInfo {
	const lang = document.documentElement.lang || 'ja'
	const base = import.meta.env.BASE_URL.replace(/\/$/, '')
	return {
		pagePath: pagePathOf(location.pathname, base, lang),
		page: { path: location.pathname, lang, title: document.title, source: pageSourceFile().path },
	}
}

const rowOf = (section: Section, label: string): string | undefined => section.find((r) => r[0] === label)?.[1]

/** 選んだものの場所。値が無い項目は入れない（ソースが「不明（…）」のときも入れない）。 */
export function locatorOf(pick: SinglePick): Locator {
	const src = sourceSection(pick)
	const loc = locationSection(pick)
	const source = rowOf(src, 'ソース')
	const text = pick.kind === 'text' ? pick.text : (pick.el as HTMLElement).innerText ?? pick.el.textContent ?? ''
	const out: Locator = { kind: pick.kind, excerpt: clip(squash(text), 60) }
	const set = <K extends keyof Locator>(k: K, v: Locator[K] | undefined) => { if (v) out[k] = v }
	set('source', source && !source.startsWith('不明') ? source : undefined)
	set('xpath', rowOf(loc, 'XPath'))
	set('selector', rowOf(loc, 'CSSセレクタ'))
	set('heading', rowOf(loc, '近くの見出し'))
	set('fragment', rowOf(loc, 'テキストフラグメントURL'))
	return out
}
