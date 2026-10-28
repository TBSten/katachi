// メモの Markdown の組み立てと読み直し。AI が読む見た目（frontmatter + 節）に、読み直し用の JSON を末尾の HTML コメントで持つ。
// 読み直しはコメントの JSON から行う（見える部分は AI 向けの出力。手で直しても読み直しには効かない）。
import { emptyNote, isNoteEmpty, MEMO_LIMITS, type Locator, type MemoDoc, type MemoPage, type Note, type Spot } from './model'

const MARKER = '<!-- katachi-memo:data v1'
export const SCHEMA = 'katachi-memo/1'

/** YAML の 1 行の値。二重引用符の文字列は JSON と同じ書き方で YAML としても正しい。 */
const yamlStr = (s: string) => JSON.stringify(s)

/** 箇所の見出しに使う短い名前。 */
export function spotTitle(l: Locator): string {
	return l.heading?.replace(/^#+\s*/, '') || (l.excerpt ? `「${l.excerpt}」` : l.source ?? '（場所の手がかりなし）')
}

// TODO: 本文の行頭の # をエスケープしない。core/record.ts の escapeHeadings を使う（review L13）。
function noteLines(note: Note): string[] {
	const out: string[] = []
	for (const [k, v] of Object.entries(note.values)) if (v.trim() !== '') out.push(`- ${k}: ${v.replace(/\s*\n\s*/g, ' ')}`)
	if (out.length && note.text.trim() !== '') out.push('')
	if (note.text.trim() !== '') out.push(note.text.replace(/\s+$/, ''))
	return out
}

/** 時刻の行。画面には出さないが、AI がメモの新しさを判断できるようにファイルには書く。 */
function timeLines(n: { createdAt?: string; updatedAt?: string }): string[] {
	const rows: string[] = []
	if (n.createdAt) rows.push(`- 作成: ${n.createdAt}`)
	if (n.updatedAt) rows.push(`- 最終更新: ${n.updatedAt}`)
	return rows
}

function locatorLines(l: Locator): string[] {
	const rows: Array<[string, string | undefined]> = [
		['種類', l.kind === 'text' ? 'テキストの選択' : '要素'],
		['source', l.source],
		['heading', l.heading],
		['excerpt', l.excerpt || undefined],
		['xpath', l.xpath],
		['selector', l.selector],
		['text fragment', l.fragment],
	]
	return ['- 場所', ...rows.filter((r): r is [string, string] => !!r[1]).map(([k, v]) => `  - ${k}: ${v}`)]
}

/** メモ全体を Markdown にする。ページのメモも箇所のメモも空なら、本文は空の節だけ（呼び出し側が書くかを決める）。 */
export function toMarkdown(doc: MemoDoc, now = new Date()): string {
	const { page } = doc
	const out: string[] = [
		'---',
		`schema: ${SCHEMA}`,
		`page: ${yamlStr(page.path)}`,
		`lang: ${yamlStr(page.lang)}`,
		`title: ${yamlStr(page.title)}`,
		`source: ${yamlStr(page.source)}`,
		`updated: ${now.toISOString()}`,
		'---',
		'',
		`# メモ: ${page.title}`,
		'',
		'## ページのメモ',
		'',
		...(isNoteEmpty(doc.note) ? ['（なし）'] : [...timeLines(doc.note), ...(doc.note.updatedAt ? [''] : []), ...noteLines(doc.note)]),
	]
	if (doc.spots.length) {
		out.push('', '## 箇所のメモ')
		doc.spots.forEach((s, i) => {
			out.push('', `### ${i + 1}. ${spotTitle(s.locator)}`, '', ...locatorLines(s.locator), ...timeLines(s))
			const body = noteLines(s)
			if (body.length) out.push('', ...body)
		})
	}
	// `>` を \u003e にして、JSON の中身がコメントを閉じられないようにする。
	const json = JSON.stringify({ page, note: doc.note, spots: doc.spots }).replace(/</g, '\\u003c').replace(/>/g, '\\u003e')
	out.push('', MARKER, json, '-->', '')
	return out.join('\n')
}

// ---- 読み直し ----

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v)
const str = (v: unknown, max: number = MEMO_LIMITS.textChars): string => (typeof v === 'string' ? v.slice(0, max) : '')
const optStr = (v: unknown): string | undefined => (typeof v === 'string' && v !== '' ? v : undefined)

function coerceNote(v: unknown): Note {
	if (!isObj(v)) return emptyNote()
	const values: Record<string, string> = {}
	if (isObj(v.values)) {
		for (const [k, val] of Object.entries(v.values)) {
			if (typeof val === 'string' && k.length <= MEMO_LIMITS.fieldNameChars && val !== '') values[k] = val.slice(0, MEMO_LIMITS.fieldValueChars)
		}
	}
	const updatedAt = optStr(v.updatedAt)?.slice(0, 40)
	return { values, text: str(v.text), ...(updatedAt ? { updatedAt } : {}) }
}

function coerceSpot(v: unknown): Spot | null {
	if (!isObj(v) || !isObj(v.locator) || typeof v.id !== 'string' || v.id === '') return null
	const l = v.locator
	const locator: Locator = {
		kind: l.kind === 'text' ? 'text' : 'element',
		excerpt: str(l.excerpt, 200),
		...(optStr(l.source) ? { source: optStr(l.source) } : {}),
		...(optStr(l.xpath) ? { xpath: optStr(l.xpath) } : {}),
		...(optStr(l.selector) ? { selector: optStr(l.selector) } : {}),
		...(optStr(l.heading) ? { heading: optStr(l.heading) } : {}),
		...(optStr(l.fragment) ? { fragment: optStr(l.fragment) } : {}),
	}
	const createdAt = optStr(v.createdAt)?.slice(0, 40)
	return { id: v.id.slice(0, 40), locator, ...coerceNote(v), ...(createdAt ? { createdAt } : {}) }
}

/** 読み直せなければ null（メモが無いのではなく、壊れている・形が違う）。 */
export function parseMarkdown(text: string): MemoDoc | null {
	const start = text.lastIndexOf(MARKER)
	if (start < 0) return null
	const rest = text.slice(start + MARKER.length)
	const end = rest.indexOf('\n-->')
	if (end < 0) return null
	let raw: unknown
	try {
		raw = JSON.parse(rest.slice(0, end))
	} catch {
		return null
	}
	if (!isObj(raw) || !isObj(raw.page)) return null
	const page: MemoPage = { path: str(raw.page.path, 2000), lang: str(raw.page.lang, 20), title: str(raw.page.title, 500), source: str(raw.page.source, 2000) }
	const spots = (Array.isArray(raw.spots) ? raw.spots : []).slice(0, MEMO_LIMITS.spots).map(coerceSpot).filter((s): s is Spot => s !== null)
	return { page, note: coerceNote(raw.note), spots }
}
