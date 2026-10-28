// 記録（schema 1）の Markdown を組み立てる。保存（server/save.ts）と「Markdown をコピー」（feedback/copy.ts）が同じ関数を通る。
// DOM も node: も使わない。組み立ての最後に redact を 1 回だけ掛ける。
import { redact } from './redact'
import { GIT_PLACEHOLDER, SECTION_KEYS, SECTION_TITLES, SERVER_SECTION_KEYS, type SavePayload, type SectionKey } from './protocol'

/** 記録の形の版。frontmatter の `schema`。 */
export const RECORD_SCHEMA = 1

/** サーバーが保存のときに決める値。コピーには入らない。 */
export type RecordMeta = {
	created: Date
	/** `yymmdd-NNN`。コピーには無い。 */
	id?: string
	/** ソースのファイルに未コミットの変更があるか。分からなければ書かない。 */
	dirty?: boolean
	/** ページを開いた後にソースのファイルが変わったか。分からなければ書かない。 */
	stale?: boolean
	/** 添付（スクリーンショットなど）の参照。 */
	attachments?: string[]
}

export type RecordKind = NonNullable<SavePayload['kind']>

/** 読む Agent への案内の 1 行。 */
export const GUIDE_LINE =
	'案内: katachi の docs サイトへのフィードバック。日本語が原本（docs/src/content/docs/ja/）、英語は翻訳。文の決まりは docs/AGENTS.md と docs/prh.yml。'

/** YAML の1行に安全に載せるため、JSON 文字列として書く（JSON の文字列は YAML のダブルクォートと互換）。 */
const yamlString = (value: string) => JSON.stringify(value)

/** 節の中の行を、見出し（label）で引く。 */
const valueOf = (payload: SavePayload, key: SectionKey, label: string) =>
	payload.sections[key]?.find(([l]) => l === label)?.[1]

const KNOWN_SOURCE = (v: string | undefined) => (v && !v.startsWith('不明') ? v : undefined)

/** ページ全体のソースファイル（page 節）。URL からの推定の注記は外す。 */
const pageSourceFile = (payload: SavePayload) => valueOf(payload, 'page', 'ページのソースファイル')?.replace(/（URL からの推定）$/, '')

const clipOne = (s: string, n: number) => {
	const one = s.replace(/\s+/g, ' ').trim()
	return one.length > n ? one.slice(0, n) + '…' : one
}

/** 何を選んだか。送られていなければ節の中身から推す。 */
export function kindOf(payload: SavePayload): RecordKind {
	if (payload.target) return payload.target
	if (payload.kind) return payload.kind
	return valueOf(payload, 'content', '選んだテキスト') !== undefined ? 'text' : 'element'
}

/** コメント中の行頭の `#` を見出しにしない（節の見出しと紛れるため）。行頭の空白があっても同じ。 */
export const escapeHeadings = (text: string) => text.replace(/^([ \t]*)#/gm, '$1\\#')

/**
 * AI がまず読む 1 行の要約。ソースの位置・近くの見出し・選んだものの先頭を並べる。
 * 載せない節にあたる部分は省く（ソースを外したら、ソースの部分は出ない）。
 */
function summaryLine(payload: SavePayload): string {
	const kind = kindOf(payload)
	if (kind === 'page' || kind === 'site') {
		const url = valueOf(payload, 'page', 'URL') ?? '(ページの情報なし)'
		if (kind === 'site') return `対象: サイト全体（${url} から）`
		const title = valueOf(payload, 'page', 'title')
		return `対象: ${pageSourceFile(payload) ?? url}（ページ全体）${title ? `「${title}」` : ''}`
	}
	const source = KNOWN_SOURCE(valueOf(payload, 'source', 'ソース'))
	const heading = valueOf(payload, 'location', '近くの見出し')
	const tag = valueOf(payload, 'content', 'タグ')
	const excerpt = valueOf(payload, 'content', 'テキストの全文') ?? valueOf(payload, 'content', '選んだテキスト')
	const where = [heading, tag && (heading ? `の ${tag}` : tag)].filter(Boolean).join(' ')
	const quoted = excerpt ? `「${clipOne(excerpt, 30)}」` : ''
	return `対象: ${source ?? valueOf(payload, 'page', 'URL') ?? '(場所の情報なし)'}${where ? `（${where}）` : ''}${quoted}`
}

/** frontmatter。値が無いキーは書かない（schema・kind・created・omitted は必ず書く）。 */
function frontmatter(payload: SavePayload, meta: RecordMeta, omitted: SectionKey[]): string {
	const kind = kindOf(payload)
	const lang = valueOf(payload, 'page', '言語')
	const commit = valueOf(payload, 'git', 'commit')
	const strings: Array<[string, string | undefined]> = [
		['id', meta.id],
		['kind', kind],
		['page', valueOf(payload, 'page', 'URL')],
		['lang', lang && lang !== '(未指定)' ? lang : undefined],
		['version', valueOf(payload, 'page', '版')],
		['source', kind === 'page' || kind === 'site' ? pageSourceFile(payload) : KNOWN_SOURCE(valueOf(payload, 'source', 'ソース'))],
		['heading', valueOf(payload, 'location', '近くの見出し')],
		['xpath', valueOf(payload, 'location', 'XPath')],
		['selector', valueOf(payload, 'location', 'CSSセレクタ')],
		['fragment', valueOf(payload, 'location', 'テキストフラグメントURL')],
		['created', meta.created.toISOString()],
		['commit', commit && commit !== GIT_PLACEHOLDER ? commit : undefined],
	]
	const lines = [`schema: ${RECORD_SCHEMA}`]
	for (const [key, value] of strings) if (value) lines.push(`${key}: ${yamlString(value)}`)
	if (meta.dirty !== undefined) lines.push(`dirty: ${meta.dirty}`)
	if (meta.stale !== undefined) lines.push(`stale: ${meta.stale}`)
	lines.push(`omitted: ${JSON.stringify(omitted)}`)
	if (meta.attachments?.length) lines.push(`attachments: ${JSON.stringify(meta.attachments)}`)
	return `---\n${lines.join('\n')}\n---`
}

/** 載せなかった節。ダイアログで外したもの。 */
function omittedOf(payload: SavePayload): SectionKey[] {
	const set = new Set(payload.omitted ?? [])
	return SECTION_KEYS.filter((k) => set.has(k) && !payload.sections[k])
}

/**
 * フィードバック1件を Markdown にする。AI に読ませる用途なので、
 * frontmatter（機械用）→ 要約の 1 行 → 案内の 1 行 → 載せなかった情報 → 見出し（コメントの1行目）→ 本文（コメント全文）→ チェックされた節、の固定の並びにする。
 */
export function toMarkdown(payload: SavePayload, meta: RecordMeta): string {
	const comment = escapeHeadings(payload.comment.trim())
	const title = comment.split('\n')[0]!.trim()
	const omitted = omittedOf(payload)

	const head = [frontmatter(payload, meta, omitted), summaryLine(payload), GUIDE_LINE]
	if (meta.stale) head.push('注意: ページを開いた後にソースのファイルが変わっています。記録の位置はずれているかもしれません。')
	if (omitted.length) head.push(`（載せなかった情報: ${omitted.map((k) => SECTION_TITLES[k]).join('、')}）`)
	const parts = [...head, `# ${title}`, comment]
	for (const key of SECTION_KEYS) {
		const section = payload.sections[key]
		if (!section) continue
		const lines = section.map(([label, value]) =>
			// 複数行の値はコードブロックに、1行の値は箇条書きにする。
			value.includes('\n')
				? `- ${label}:\n\n${fence(value)}`
				: `- ${label}: ${value}`,
		)
		parts.push(`## ${SECTION_TITLES[key]}\n\n${lines.join('\n')}`)
	}
	return redact(parts.join('\n\n') + '\n')
}

/**
 * 「Markdown をコピー」の文。保存と同じ関数を通すが、サーバーが保存のときに求める節（git・ビルド）と
 * サーバーが決める値（id・commit・dirty・stale）は入らない。
 */
export function toCopyMarkdown(payload: SavePayload, created: Date): string {
	const sections = { ...payload.sections }
	for (const k of SERVER_SECTION_KEYS) delete sections[k]
	return toMarkdown({ ...payload, sections }, { created })
}

/** 値の中に ``` があっても閉じられないよう、本文より長いフェンスを使う。 */
function fence(value: string): string {
	const longest = Math.max(2, ...(value.match(/`+/g) ?? []).map((s) => s.length))
	const bar = '`'.repeat(longest + 1)
	return `${bar}\n${value}\n${bar}`
}
