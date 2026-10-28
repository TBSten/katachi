import { SECTION_KEYS, SECTION_TITLES, type SavePayload, type SectionKey } from './protocol'

/** YAML の1行に安全に載せるため、JSON 文字列として書く（JSON の文字列は YAML のダブルクォートと互換）。 */
const yamlString = (value: string) => JSON.stringify(value)

/** 節の中の行を、見出し（label）で引く。 */
const valueOf = (payload: SavePayload, key: SectionKey, label: string) =>
	payload.sections[key]?.find(([l]) => l === label)?.[1]

const KNOWN_SOURCE = (v: string | undefined) => (v && !v.startsWith('不明') ? v : undefined)

/**
 * AI がまず読む 1 行の要約。ソースの位置・近くの見出し・選んだものの先頭を並べる。
 * 載せない節にあたる部分は省く（ソースを外したら、ソースの部分は出ない）。
 */
function summaryLine(payload: SavePayload): string {
	if (payload.target) {
		const url = valueOf(payload, 'page', 'URL') ?? '(ページの情報なし)'
		if (payload.target === 'site') return `対象: サイト全体（${url} から）`
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

/** ページ全体のソースファイル（page 節）。URL からの推定の注記は外す。 */
const pageSourceFile = (payload: SavePayload) => valueOf(payload, 'page', 'ページのソースファイル')?.replace(/（URL からの推定）$/, '')

const clipOne = (s: string, n: number) => {
	const one = s.replace(/\s+/g, ' ').trim()
	return one.length > n ? one.slice(0, n) + '…' : one
}

/**
 * フィードバック1件を Markdown にする。AI に読ませる用途なので、
 * frontmatter（機械用）→ 要約の 1 行 → 見出し（コメントの1行目）→ 本文（コメント全文）→ チェックされた節、の固定の並びにする。
 */
export function toMarkdown(payload: SavePayload, created: Date): string {
	const comment = payload.comment.trim()
	const title = comment.split('\n')[0]!.trim()

	const front: string[] = []
	const fields: Array<[string, string | undefined]> = [
		['page', valueOf(payload, 'page', 'URL')],
		['target', payload.target],
		['source', payload.target === 'page' ? pageSourceFile(payload) : KNOWN_SOURCE(valueOf(payload, 'source', 'ソース'))],
		['xpath', valueOf(payload, 'location', 'XPath')],
		['selector', valueOf(payload, 'location', 'CSSセレクタ')],
		['heading', valueOf(payload, 'location', '近くの見出し')],
	]
	for (const [key, value] of fields) if (value) front.push(`${key}: ${yamlString(value)}`)
	front.push(`created: ${yamlString(created.toISOString())}`)
	const commit = valueOf(payload, 'git', 'commit')
	if (commit) front.push(`commit: ${yamlString(commit)}`)

	const parts = [`---\n${front.join('\n')}\n---`, summaryLine(payload), `# ${title}`, comment]
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
	return parts.join('\n\n') + '\n'
}

/** 値の中に ``` があっても閉じられないよう、本文より長いフェンスを使う。 */
function fence(value: string): string {
	const longest = Math.max(2, ...(value.match(/`+/g) ?? []).map((s) => s.length))
	const bar = '`'.repeat(longest + 1)
	return `${bar}\n${value}\n${bar}`
}
