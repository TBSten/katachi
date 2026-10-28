// メモの型と、field のひな形・保存先パスの純粋な規則。DOM も node: も使わない（ブラウザとサーバーの両方から import する）。

/** メモ 1 件（ページ全体、または選んだ箇所 1 つ）の中身。 */
export type Note = {
	/** field 名 → 値（1 行）。ひな形から外された field の値もここに残る。 */
	values: Record<string, string>
	/** 大きい textarea に書いた本文。 */
	text: string
	/** 中身（values か text）が最後に変わった時刻（ISO 8601）。画面には出さず、ファイルにだけ書く。 */
	updatedAt?: string
}

/** 選んだ箇所の場所の情報。引き直し（shared/relocate.ts）と、AI が読む Markdown の両方に使う。 */
export type Locator = {
	kind: 'element' | 'text'
	/** 元の Markdown / MDX の位置 `path:12-14`。 */
	source?: string
	xpath?: string
	selector?: string
	/** 近くの見出し `## 見出し`。 */
	heading?: string
	/** テキストフラグメントの URL。 */
	fragment?: string
	/** 本文の抜粋（箇所の見出しと、見つからないときの手がかり）。 */
	excerpt: string
}

export type Spot = Note & { id: string; locator: Locator; /** 箇所を足した時刻（ISO 8601）。 */ createdAt?: string }

export type MemoPage = {
	/** `location.pathname`。 */
	path: string
	lang: string
	title: string
	/** ページのソースファイル。 */
	source: string
}

export type MemoDoc = { page: MemoPage; note: Note; spots: Spot[] }

export const MEMO_LIMITS = {
	/** 本文 1 つの文字数。 */
	textChars: 100_000,
	/** field 名の文字数。 */
	fieldNameChars: 40,
	/** field 値の文字数。 */
	fieldValueChars: 500,
	/** ひな形の field の数。 */
	fields: 30,
	/** 1 ページの箇所の数。 */
	spots: 200,
	/** 保存する Markdown 全体の文字数（受け口の検査）。 */
	docChars: 2_000_000,
} as const

export const emptyNote = (): Note => ({ values: {}, text: '' })

/** values と text が同じなら true（時刻は比べない）。最終更新時刻を、中身が変わったときだけ進めるため。 */
export const sameNoteContent = (a: Note, b: Note) =>
	a.text === b.text &&
	Object.keys(a.values).length === Object.keys(b.values).length &&
	Object.entries(a.values).every(([k, v]) => b.values[k] === v)

export const isNoteEmpty = (n: Note) => n.text.trim() === '' && Object.values(n.values).every((v) => v.trim() === '')

// ---- field のひな形 ----

/** 最初のひな形。 */
export const DEFAULT_FIELDS: readonly string[] = ['読みやすさ']

/** field 名として使える形にする。空・長すぎるものは null。 */
export function normalizeFieldName(name: string): string | null {
	const n = name.replace(/\s+/g, ' ').trim()
	return n === '' || n.length > MEMO_LIMITS.fieldNameChars ? null : n
}

/** ひな形に足す。既にあれば同じ配列を返す。 */
export function addField(template: readonly string[], name: string): string[] {
	const n = normalizeFieldName(name)
	if (!n || template.includes(n) || template.length >= MEMO_LIMITS.fields) return [...template]
	return [...template, n]
}

/** ひな形から外す。メモの値は触らない（外した field の値はファイルに残る）。 */
export function removeField(template: readonly string[], name: string): string[] {
	return template.filter((f) => f !== name)
}

/** `_fields.json` の中身を読む。壊れていたり形が違えば既定。 */
export function parseFields(json: string | null): string[] {
	if (json === null) return [...DEFAULT_FIELDS]
	try {
		const v = JSON.parse(json) as { fields?: unknown }
		if (!Array.isArray(v.fields)) return [...DEFAULT_FIELDS]
		return sanitizeFields(v.fields)
	} catch {
		return [...DEFAULT_FIELDS]
	}
}

/** 文字列以外・空・重複・多すぎるものを落とす。 */
export function sanitizeFields(list: unknown[]): string[] {
	let out: string[] = []
	for (const f of list) {
		if (typeof f !== 'string') continue
		out = addField(out, f)
	}
	return out
}

export const serializeFields = (fields: readonly string[]) => `${JSON.stringify({ fields }, null, 2)}\n`

// ---- 保存先 ----

/** 置き場の中のひな形のパス。 */
export const FIELDS_PATH = 'memo/_fields.json'

const LANG = /^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$/
const SEGMENT = /^[\p{L}\p{N}_][\p{L}\p{N}._-]*$/u

/**
 * `memo/<言語>/<ページのパス>.md`（置き場からの相対）。pagePath は base と言語を除いた `guides/x/`（空ならトップ）。
 * 使えない文字・`..` を含むものは投げる。
 */
export function memoDocPath(lang: string, pagePath: string): string {
	if (!LANG.test(lang)) throw new Error(`言語が使えません: ${JSON.stringify(lang)}`)
	const segs = pagePath.split('/').filter((s) => s !== '')
	for (const s of segs) if (!SEGMENT.test(s)) throw new Error(`ページのパスが使えません: ${JSON.stringify(pagePath)}`)
	return `memo/${lang}/${segs.length ? segs.join('/') : 'index'}.md`
}

// ---- 見える field ----

/** 画面に出す field。ひな形の順。値は無ければ空。 */
export const visibleFields = (template: readonly string[], note: Note): Array<[name: string, value: string]> =>
	template.map((f) => [f, note.values[f] ?? ''])

/** 値を書き換えた新しい Note（空の値は消す。外した field の値は残る）。 */
export function withValue(note: Note, name: string, value: string): Note {
	const values = { ...note.values }
	if (value === '') delete values[name]
	else values[name] = value.slice(0, MEMO_LIMITS.fieldValueChars)
	return { ...note, values }
}
