// field のひな形（`memo/_fields.json`）の型と、読み書き・足す・外すの純粋な規則。DOM も node: も使わない。
//
// ファイルの 1 要素は、文字列（テキストの field）か `{ "name", "type", "options" }`。
// 読むときの決まり（壊れた要素は、名前が使えれば text として読み、名前が使えなければ捨てる）:
// - 文字列・名前が使えるオブジェクト以外（数・null・配列・名前の無いオブジェクト）は捨てる
// - type が無い・未知の type は text として読む（名前と値を失わないように）
// - select の options は、文字列以外・空・長すぎるもの・重複を落とし、多すぎる分は先頭から上限まで。1 つも残らなければ text として読む
// - 同じ名前が 2 回出たら先のものを使う
import { MEMO_LIMITS, type Note } from './model'

export const FIELD_TYPES = ['text', 'check', 'select'] as const
export type FieldType = (typeof FIELD_TYPES)[number]

/** ひな形の field 1 つ。options は select のときだけあり、1 個以上。 */
export type FieldDef =
	| { name: string; type: 'text' | 'check' }
	| { name: string; type: 'select'; options: string[] }

/** check の field で「入っている」を表す値。外すと値ごと消す。 */
export const CHECKED = 'true'

/** 最初のひな形。 */
export const DEFAULT_FIELDS: readonly FieldDef[] = [{ name: '読みやすさ', type: 'text' }]

const squash = (s: string) => s.replace(/\s+/g, ' ').trim()

/** field 名として使える形にする。空・長すぎるものは null。 */
export function normalizeFieldName(name: string): string | null {
	const n = squash(name)
	return n === '' || n.length > MEMO_LIMITS.fieldNameChars ? null : n
}

/** 選択肢 1 つを使える形にする。空・長すぎるものは null。 */
export function normalizeOption(option: string): string | null {
	const n = squash(option)
	return n === '' || n.length > MEMO_LIMITS.fieldOptionChars ? null : n
}

/** 選択肢の並びを整える（使えないもの・重複を落とし、上限まで）。 */
export function sanitizeOptions(list: unknown[]): string[] {
	const out: string[] = []
	for (const o of list) {
		if (out.length >= MEMO_LIMITS.fieldOptions) break
		const n = typeof o === 'string' ? normalizeOption(o) : null
		if (n && !out.includes(n)) out.push(n)
	}
	return out
}

/** 「+ field を追加」で入れた選択肢の文字（カンマ・全角カンマ・改行区切り）を分ける。整える前の並び。 */
export const splitOptions = (text: string): string[] => text.split(/[,，\n]/).map(squash).filter((s) => s !== '')

/** ひな形の 1 要素（ファイルの形・通信の形のどちらでも）を FieldDef にする。使えなければ null。 */
export function toFieldDef(v: unknown): FieldDef | null {
	if (typeof v === 'string') {
		const name = normalizeFieldName(v)
		return name ? { name, type: 'text' } : null
	}
	if (typeof v !== 'object' || v === null || Array.isArray(v)) return null
	const o = v as Record<string, unknown>
	const name = typeof o.name === 'string' ? normalizeFieldName(o.name) : null
	if (!name) return null
	if (o.type === 'check') return { name, type: 'check' }
	if (o.type === 'select') {
		const options = Array.isArray(o.options) ? sanitizeOptions(o.options) : []
		if (options.length) return { name, type: 'select', options }
	}
	return { name, type: 'text' }
}

const sameDef = (a: FieldDef, b: FieldDef) =>
	a.name === b.name && a.type === b.type && (a.type !== 'select' || (b.type === 'select' && a.options.join('\n') === b.options.join('\n')))

/** 2 つのひな形が同じなら true。 */
export const sameFields = (a: readonly FieldDef[], b: readonly FieldDef[]) => a.length === b.length && a.every((f, i) => sameDef(f, b[i]))

/**
 * ひな形に足す。文字列はテキストの field。同じ名前があれば、種類・選択肢をその位置で置き換える（値は名前で引くので残る）。
 * 使えないもの・上限を超える新しい名前は足さない（同じ中身の新しい配列を返す）。
 */
export function addField(template: readonly FieldDef[], field: string | FieldDef): FieldDef[] {
	const def = toFieldDef(field)
	if (!def) return [...template]
	const i = template.findIndex((f) => f.name === def.name)
	if (i >= 0) return template.map((f, j) => (j === i ? def : f))
	if (template.length >= MEMO_LIMITS.fields) return [...template]
	return [...template, def]
}

/** ひな形から外す。メモの値は触らない（外した field の値はファイルに残る）。 */
export function removeField(template: readonly FieldDef[], name: string): FieldDef[] {
	return template.filter((f) => f.name !== name)
}

/** ひな形の並びを整える（使えないものを捨て、重複は先のものを使い、上限まで）。 */
export function sanitizeFields(list: unknown[]): FieldDef[] {
	const out: FieldDef[] = []
	for (const v of list) {
		if (out.length >= MEMO_LIMITS.fields) break
		const def = toFieldDef(v)
		if (def && !out.some((f) => f.name === def.name)) out.push(def)
	}
	return out
}

/** `_fields.json` の中身を読む。壊れていたり形が違えば既定。 */
export function parseFields(json: string | null): FieldDef[] {
	if (json === null) return [...DEFAULT_FIELDS]
	try {
		const v = JSON.parse(json) as { fields?: unknown }
		if (!Array.isArray(v.fields)) return [...DEFAULT_FIELDS]
		return sanitizeFields(v.fields)
	} catch {
		return [...DEFAULT_FIELDS]
	}
}

/** `_fields.json` に書く形。テキストの field は文字列のまま（前からのファイルが変わらないように）、それ以外はオブジェクト。 */
export function serializeFields(fields: readonly FieldDef[]): string {
	const list = fields.map((f) => (f.type === 'text' ? f.name : f.type === 'select' ? { name: f.name, type: f.type, options: f.options } : { name: f.name, type: f.type }))
	return `${JSON.stringify({ fields: list }, null, 2)}\n`
}

/** 画面に出す field。ひな形の順。値は無ければ空。 */
export const visibleFields = (template: readonly FieldDef[], note: Note): Array<[field: FieldDef, value: string]> =>
	template.map((f) => [f, note.values[f.name] ?? ''])

/** Markdown の見える部分に書く値。check の field で入っていれば `[x]`、それ以外は値のまま（型の合わない値もそのまま）。 */
export function displayValue(field: FieldDef | undefined, value: string): string {
	return field?.type === 'check' && value === CHECKED ? '[x]' : value
}
