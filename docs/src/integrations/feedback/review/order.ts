// 指摘の絞り込み・並び・ページごとの数（純粋な計算。DOM を使わない）。
import type { ReviewView } from '../core/protocol'
import { pageUrlOf } from './model'

export type Filter = {
	/** この優先度以上だけ出す（0 なら全部。優先度の無い指摘は常に出す）。 */
	minPriority: number
	/** 解決済みも出す。 */
	showResolved: boolean
}

export const DEFAULT_FILTER: Filter = { minPriority: 0, showResolved: false }
export const PRIORITY_STEPS = [0, 3, 5, 7] as const

/** 保存した文字列から読む。壊れていれば既定。 */
export function parseFilter(raw: string | null): Filter {
	try {
		const o = JSON.parse(raw ?? '') as Partial<Filter>
		return {
			minPriority: (PRIORITY_STEPS as readonly number[]).includes(o.minPriority as number) ? (o.minPriority as number) : 0,
			showResolved: o.showResolved === true,
		}
	} catch {
		return { ...DEFAULT_FILTER }
	}
}

export const passes = (v: ReviewView, f: Filter) =>
	(f.showResolved || v.status !== 'resolved') && (v.priority === null || v.priority >= f.minPriority)

/** URL の末尾の `/` と `index.html` をそろえる。 */
export const samePath = (a: string, b: string) => {
	const n = (s: string) => decodeURI(s).replace(/index\.html$/, '').replace(/\/?$/, '/')
	return n(a) === n(b)
}

/**
 * 全体の並び: ページのあるファイル（パス順）→ ページの無いファイル。ファイルの中は、ページ全体の指摘 → 行の順。
 * 同じ行なら優先度の高い順。この並びが popup の「前・次」と「何件め / 総数」になる。
 */
export function sortItems(items: ReviewView[], base: string): ReviewView[] {
	const key = (v: ReviewView) => [pageUrlOf(v.file, base) ? 0 : 1, v.file, v.line?.start ?? 0, -(v.priority ?? -1), v.title] as const
	return [...items].sort((a, b) => {
		const ka = key(a)
		const kb = key(b)
		for (let i = 0; i < ka.length; i++) {
			if (ka[i]! < kb[i]!) return -1
			if (ka[i]! > kb[i]!) return 1
		}
		return 0
	})
}

/** そのページの指摘か（URL かソースのパスが同じ）。 */
export const onPage = (v: ReviewView, o: { pathname: string; source: string | null; base: string }) => {
	const url = pageUrlOf(v.file, o.base)
	return (url !== null && samePath(url, o.pathname)) || (o.source !== null && v.file === o.source)
}

export type PageGroup = { file: string; url: string | null; count: number; first: ReviewView }

/** ファイルごとの数（並びは sortItems の順）。 */
export function groupByFile(sorted: ReviewView[], base: string): PageGroup[] {
	const out = new Map<string, PageGroup>()
	for (const v of sorted) {
		const g = out.get(v.file)
		if (g) g.count++
		else out.set(v.file, { file: v.file, url: pageUrlOf(v.file, base), count: 1, first: v })
	}
	return [...out.values()]
}

/** 一覧に出す短い名前（`docs/src/content/docs/ja/guides/x.mdx` → `ja/guides/x.mdx`）。 */
export const shortFile = (file: string) => file.replace(/^docs\/src\/content\/docs\//, '')
