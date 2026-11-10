// 指摘（Review）のファイルの形と、読み書き・行の引き直しの純粋な規則。DOM も node: も使わない（ブラウザ・サーバー・cli から import する）。
//
// 指摘のファイルは置き場の `review/<名前>.md`（1 ファイル = 指摘のまとまり 1 つ）。形は README.md。
//
//   ## [p3] dsl.gradle を個別 import している
//   - file: ja/get-started/first-architecture.mdx
//   - line: 168
//   - quote: import me.tbsten.katachi.dsl.gradle.gradle
//
//   本文（Markdown）
//
// 流れ: agent が指摘を書く（open）→ 人がページの popup で「解決済みにする」（resolved）→ agent が確かめて、
// 直っていれば消す（cli の review remove）、直っていなければ差し戻す（review reopen。ai-note に理由）。

export type ReviewStatus = 'open' | 'resolved'
export const REVIEW_STATUSES: readonly ReviewStatus[] = ['open', 'resolved']

export type LineRange = { start: number; end: number }

/** 指摘 1 件（ファイルから読んだまま）。 */
export type ReviewItem = {
	/** `<まとまりの名前>/<file と見出しのハッシュ>`。ほかの指摘を消しても変わらない。 */
	id: string
	/** まとまりの名前（ファイル名から .md を除いたもの）。 */
	set: string
	title: string
	/** 見出しの `[pN]` か `- priority:`。無ければ null。 */
	priority: number | null
	/** 書かれたままのパス（`ja/…mdx` の省略形もある。リポジトリ直下からの形にするのはサーバー）。 */
	file: string
	/** 無ければページ全体への指摘（quote があれば quote で探す）。 */
	line: LineRange | null
	/** 行がずれたときに探し直す手がかり（その行に含まれる文字列）。 */
	quote?: string
	status: ReviewStatus
	/** 人が「解決済みにする」で書いた一言。 */
	note?: string
	/** 解決済みにした時刻（ISO 8601）。 */
	resolvedAt?: string
	/** agent の一言（差し戻しの理由など）。 */
	aiNote?: string
	/** 本文（Markdown）。 */
	body: string
	/** ファイルの中の位置（0 始まりの行。書き戻しに使う）。見出しの行・メタの行の終わり（その行は含まない）・指摘の終わり。 */
	span: { heading: number; metaEnd: number; end: number }
}

export type ReviewSet = {
	name: string
	title: string
	/** 行番号を数えたときのコミット（`base:`）。表示だけに使う。 */
	base?: string
	items: ReviewItem[]
	/** 読めなかった指摘など（`L12: file がありません`）。 */
	errors: string[]
}

/** 指摘のメタとして読むキー。本文の箇条書き（`- 該当: …` など）を飲み込まないよう、ここにあるキーだけをメタにする。 */
const META_KEYS = ['file', 'line', 'priority', 'quote', 'status', 'note', 'resolved-at', 'ai-note', 'id'] as const
type MetaKey = (typeof META_KEYS)[number]
const META_LINE = /^- (file|line|priority|quote|status|note|resolved-at|ai-note|id):(?:\s+(.*))?$/
/** `- id:` に書く形（まとまりの名前を除いた id。`k3x9a2b` / `k3x9a2b-2`）。 */
const PINNED_ID = /^[0-9a-z]{1,13}(?:-\d{1,4})?$/

/** まとまりの名前（ファイル名）に使える形。 */
export const SET_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/

/** 1 行に書けるメモの文字数。 */
export const NOTE_CHARS = 500

const FENCE = /^\s*(```|~~~)/

/** 32bit の FNV-1a を 36 進で。id を短く安定させるためだけ（暗号ではない）。 */
function hash(s: string): string {
	let h = 0x811c9dc5
	for (let i = 0; i < s.length; i++) {
		h ^= s.charCodeAt(i)
		h = Math.imul(h, 0x01000193)
	}
	return (h >>> 0).toString(36).padStart(7, '0')
}

/** `168` / `160-186` / `L160〜L186` / `L168`。読めなければ null。 */
export function parseLine(v: string): LineRange | null {
	const m = /^L?(\d+)(?:\s*[-〜~–]\s*L?(\d+))?$/.exec(v.trim())
	if (!m) return null
	const start = Number(m[1])
	const end = Number(m[2] ?? m[1])
	if (start < 1 || end < start) return null
	return { start, end }
}

export const formatLine = (l: LineRange) => (l.start === l.end ? `L${l.start}` : `L${l.start}-${l.end}`)

const unquote = (v: string) => v.replace(/^`+|`+$/g, '').trim()

/** frontmatter の `key: value` を拾う（title と base だけ）。 */
function readFront(lines: string[]): { front: Record<string, string>; next: number } {
	if (lines[0]?.trim() !== '---') return { front: {}, next: 0 }
	const front: Record<string, string> = {}
	for (let i = 1; i < lines.length; i++) {
		if (lines[i]!.trim() === '---') return { front, next: i + 1 }
		const kv = /^([A-Za-z][\w-]*):\s*(.*)$/.exec(lines[i]!)
		if (kv) front[kv[1]!] = kv[2]!.replace(/^["']|["']$/g, '').trim()
	}
	return { front: {}, next: 0 }
}

/** ファイルの行。改行は \n にそろえる（書き戻しも \n で書く）。 */
export const splitLines = (text: string) => text.replace(/\r\n?/g, '\n').split('\n')

/** 指摘のファイルを読む。壊れた指摘は飛ばして errors に理由を書く（投げない）。 */
export function parseReview(name: string, text: string): ReviewSet {
	const lines = splitLines(text)
	const { front, next } = readFront(lines)
	const errors: string[] = []
	const items: ReviewItem[] = []
	// 見出しの行（コードフェンスの中の `## ` は見出しにしない）。
	const heads: number[] = []
	let inFence = false
	for (let i = next; i < lines.length; i++) {
		if (FENCE.test(lines[i]!)) inFence = !inFence
		else if (!inFence && /^## \S/.test(lines[i]!)) heads.push(i)
	}
	/** id を決める前の指摘（key は file と見出し、pin は `- id:` に書いてある id）。 */
	const pending: { item: Omit<ReviewItem, 'id'>; key: string; pin: string | null }[] = []
	heads.forEach((h, k) => {
		const end = heads[k + 1] ?? lines.length
		const where = `L${h + 1}`
		let title = lines[h]!.slice(3).trim()
		let priority: number | null = null
		const p = /^\[p(\d{1,3})\]\s*(.*)$/i.exec(title)
		if (p) {
			priority = Number(p[1])
			title = p[2]!.trim()
		}
		const meta: Partial<Record<MetaKey, string>> = {}
		let metaEnd = h + 1
		for (let i = h + 1; i < end; i++) {
			const m = META_LINE.exec(lines[i]!)
			if (!m) break
			meta[m[1] as MetaKey] = (m[2] ?? '').trim()
			metaEnd = i + 1
		}
		if (!title) return void errors.push(`${where}: 見出しが空です`)
		if (!meta.file) return void errors.push(`${where}: 「- file:」がありません（${title}）`)
		let line: LineRange | null = null
		if (meta.line) {
			line = parseLine(meta.line)
			if (!line) errors.push(`${where}: line の形が読めません: ${meta.line}（ページ全体への指摘として出します）`)
		}
		if (meta.priority) {
			const n = Number(meta.priority.replace(/^p/i, ''))
			if (Number.isInteger(n) && n >= 0) priority = n
			else errors.push(`${where}: priority が数ではありません: ${meta.priority}`)
		}
		let status: ReviewStatus = 'open'
		if (meta.status) {
			if ((REVIEW_STATUSES as readonly string[]).includes(meta.status)) status = meta.status as ReviewStatus
			else errors.push(`${where}: status は open か resolved です: ${meta.status}（open として出します）`)
		}
		const file = unquote(meta.file).replace(/^\.\//, '')
		const key = `${file}\n${title}`
		let pin: string | null = null
		if (meta.id) {
			if (PINNED_ID.test(meta.id)) pin = `${name}/${meta.id}`
			else errors.push(`${where}: id の形が読めません: ${meta.id}（見出しから id を作ります）`)
		}
		const body = lines.slice(metaEnd, end).join('\n').trim()
		pending.push({ key, pin, item: {
			set: name,
			title,
			priority,
			file,
			line,
			...(meta.quote ? { quote: unquote(meta.quote) } : {}),
			status,
			...(meta.note ? { note: meta.note } : {}),
			...(meta['resolved-at'] ? { resolvedAt: meta['resolved-at'] } : {}),
			...(meta['ai-note'] ? { aiNote: meta['ai-note'] } : {}),
			body,
			span: { heading: h, metaEnd, end },
		} })
	})
	// id: `- id:` に書いてあればそれ（解決済みにしたときに書く。ほかの指摘を消してもずれない）。
	// 無ければ file と見出しのハッシュで、同じものが並ぶと 2 件目から `-2`・`-3`（書いてある id とは重ねない）。
	const taken = new Set<string>()
	for (const p of pending) {
		if (!p.pin) continue
		if (taken.has(p.pin)) {
			errors.push(`L${p.item.span.heading + 1}: 同じ id がもうあります: ${p.pin}（見出しから id を作ります）`)
			p.pin = null
		} else taken.add(p.pin)
	}
	for (const p of pending) {
		let id = p.pin
		if (!id) {
			const h = `${name}/${hash(p.key)}`
			let n = 1
			while (taken.has(n > 1 ? `${h}-${n}` : h)) n++
			id = n > 1 ? `${h}-${n}` : h
			taken.add(id)
		}
		items.push({ id, ...p.item })
	}
	return { name, title: front.title || name, ...(front.base ? { base: front.base } : {}), items, errors }
}

// ---- 書き戻し ----

/** 1 行に収める（改行・連続する空白は 1 つの空白に）。 */
export const oneLine = (s: string, max = NOTE_CHARS) => s.replace(/\s+/g, ' ').trim().slice(0, max)

export type StatusPatch =
	/** 人が解決済みにした。 */
	| { status: 'resolved'; note?: string; at: string }
	/** 人が戻した（解決済みの印と一言を消す）。 */
	| { status: 'open' }
	/** agent が差し戻した（解決済みの印を消し、理由を ai-note に）。 */
	| { status: 'open'; aiNote: string }

/**
 * id の指摘のメタを書き換えた全文を返す。メタのうち status・note・resolved-at（差し戻しなら ai-note も）だけを入れ替え、
 * ほかの行・本文・ほかの指摘には触らない。見つからなければ投げる（ファイルが書き換わった）。
 */
export function applyStatus(name: string, text: string, id: string, patch: StatusPatch): string {
	const set = parseReview(name, text)
	const item = set.items.find((i) => i.id === id)
	if (!item) throw new Error(`指摘が見つかりません（ファイルが書き換わったかもしれません。読み込み直してください）: ${id}`)
	const lines = splitLines(text)
	const drop = new Set<string>(['status', 'note', 'resolved-at', 'id'])
	if ('aiNote' in patch) drop.add('ai-note')
	const before = lines.slice(item.span.heading + 1, item.span.metaEnd)
	const meta = before.filter((l) => !drop.has(META_LINE.exec(l)?.[1] ?? ''))
	// 同じ file・見出しの指摘がほかにもあるなら、印を付け外しした時点で id を書いて固定する。
	// Feedback の記録や判断の記録が id で指すので、前の指摘を消しても（`x-2` が `x` に詰まらず）同じ指摘を指し続ける。
	// 見出しが 1 つだけの指摘は id がずれないので書かない（ファイルを汚さない）。
	const pinned = before.some((l) => META_LINE.exec(l)?.[1] === 'id')
	if (pinned || set.items.some((x) => x !== item && x.file === item.file && x.title === item.title)) meta.push(`- id: ${item.id.slice(name.length + 1)}`)
	if (patch.status === 'resolved') {
		meta.push('- status: resolved', `- resolved-at: ${patch.at}`)
		const note = oneLine(patch.note ?? '')
		if (note) meta.push(`- note: ${note}`)
	} else if ('aiNote' in patch) {
		const ai = oneLine(patch.aiNote)
		if (ai) meta.push(`- ai-note: ${ai}`)
	}
	return [...lines.slice(0, item.span.heading + 1), ...meta, ...lines.slice(item.span.metaEnd)].join('\n')
}

/** id の指摘を見出しから次の見出しの手前まで消した全文を返す。同じ見出しの残りの指摘は id を書いて固定する。見つからなければ投げる。 */
export function removeItem(name: string, text: string, id: string): string {
	const set = parseReview(name, text)
	const item = set.items.find((i) => i.id === id)
	if (!item) throw new Error(`指摘が見つかりません: ${id}`)
	const lines = splitLines(text)
	// 同じ file・見出しの残りの指摘は、消すと `x-2` が `x` に詰まる。まだ id を書いていないものには今の id を書いて固定する
	// （人に渡したリンク・Feedback の記録・判断の記録が、消したあとも同じ指摘を指すように）。
	// 書き足しと消すのを後ろの行から順にして、まだ触っていない場所の行番号をずらさない。
	const edits: { at: number; run: () => void }[] = [{ at: item.span.heading, run: () => lines.splice(item.span.heading, item.span.end - item.span.heading) }]
	for (const x of set.items) {
		if (x === item || x.file !== item.file || x.title !== item.title) continue
		if (lines.slice(x.span.heading + 1, x.span.metaEnd).some((l) => META_LINE.exec(l)?.[1] === 'id')) continue
		edits.push({ at: x.span.metaEnd, run: () => lines.splice(x.span.metaEnd, 0, `- id: ${x.id.slice(name.length + 1)}`) })
	}
	for (const e of edits.sort((a, b) => b.at - a.at)) e.run()
	return lines.join('\n')
}

// ---- 行の引き直し ----

/** 置けたか。ok = 書かれた行のまま、moved = quote で探し直して動かした、lost = quote が見つからない（書かれた行のまま出す）、page = 行が無い。 */
export type Placement = 'ok' | 'moved' | 'lost' | 'page'

const norm = (s: string) => s.replace(/`/g, '').replace(/\s+/g, ' ').trim()

/** 行が quote を含むか。quote の `…`・`...` は省略とみなし、前後の部分がこの順で含まれていればよい。 */
export function quoteMatches(text: string, quote: string): boolean {
	const parts = norm(quote).split(/\s*(?:…|\.\.\.)\s*/).filter((p) => p !== '')
	if (!parts.length) return false
	const t = norm(text)
	let from = 0
	for (const p of parts) {
		const i = t.indexOf(p, from)
		if (i < 0) return false
		from = i + p.length
	}
	return true
}

/**
 * 指摘の行を、今のソースに合わせる。指摘を書いたあとでソースが変わっても、quote を含む行が近くにあればそこへ動かす。
 * quote は最初の空でない行だけを使う。行が無く quote があれば、quote を含む最初の行にする。
 */
export function relocate(line: LineRange | null, quote: string | undefined, source: string[]): { line: LineRange | null; place: Placement } {
	const q = quote?.split('\n').find((l) => norm(l) !== '') ?? ''
	const len = source.length
	if (!line) {
		if (!norm(q)) return { line: null, place: 'page' }
		const i = source.findIndex((l) => quoteMatches(l, q))
		return i < 0 ? { line: null, place: 'page' } : { line: { start: i + 1, end: i + 1 }, place: 'ok' }
	}
	if (!norm(q)) return { line, place: line.start <= len ? 'ok' : 'lost' }
	if (quoteMatches(source.slice(line.start - 1, line.end).join(' '), q)) return { line, place: 'ok' }
	let best = -1
	source.forEach((l, i) => {
		if (quoteMatches(l, q) && (best < 0 || Math.abs(i + 1 - line.start) < Math.abs(best + 1 - line.start))) best = i
	})
	if (best < 0) return { line, place: 'lost' }
	// 範囲の指摘は、quote の行が範囲の先頭にあるとみなして範囲ごと動かす（幅はそのまま）。
	const delta = best + 1 - line.start
	return { line: { start: line.start + delta, end: line.end + delta }, place: 'moved' }
}

/**
 * 本文に出ない行（タブの見出しの `<TabItem label="…">`、`<LinkCard description="…">` など）を、ページの文字から探す手がかり。
 * 行（3 行まで）に書かれた label / title / description / text 属性の値と、quote の `…` で区切った部分のうち長いもの。2 文字未満は使わない。
 */
export function textHints(line: LineRange | null, quote: string | undefined, source: string[]): string[] {
	const out: string[] = []
	if (line) {
		const text = source.slice(line.start - 1, Math.min(line.end, line.start + 2)).join('\n')
		for (const m of text.matchAll(/\b(?:label|title|description|text)=(?:"([^"]+)"|'([^']+)')/g)) out.push(m[1] ?? m[2] ?? '')
	}
	const q = quote?.split('\n').find((l) => norm(l) !== '')
	if (q) out.push(...norm(q).split(/\s*(?:…|\.\.\.)\s*/).sort((a, b) => b.length - a.length).slice(0, 1))
	return [...new Set(out.map(norm).filter((h) => h.length >= 2))]
}

// ---- ページとソースの対応 ----

/** docs の本文の置き場（リポジトリ直下から）。 */
export const CONTENT_DIR = 'docs/src/content/docs/'

/**
 * ソースのパスからページの URL（`/katachi/ja/guides/x/`）。本文の置き場の .md / .mdx でなければ null。
 * `index.mdx` はフォルダの URL。
 */
export function pageUrlOf(file: string, base: string): string | null {
	if (!file.startsWith(CONTENT_DIR) || !/\.mdx?$/.test(file)) return null
	const rel = file.slice(CONTENT_DIR.length).replace(/\.mdx?$/, '').replace(/(^|\/)index$/, '')
	const b = base.replace(/\/$/, '')
	return rel ? `${b}/${rel}/` : `${b}/`
}

/** 印の色の段（p7 以上 = high、p5〜6 = mid、p3〜4 = low、それ未満 = min、無し = none）。 */
export function priorityTier(p: number | null): 'high' | 'mid' | 'low' | 'min' | 'none' {
	if (p === null) return 'none'
	if (p >= 7) return 'high'
	if (p >= 5) return 'mid'
	if (p >= 3) return 'low'
	return 'min'
}
