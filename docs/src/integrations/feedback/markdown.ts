import { SECTION_KEYS, SECTION_TITLES, type SavePayload } from './protocol'

/** YAML の1行に安全に載せるため、JSON 文字列として書く（JSON の文字列は YAML のダブルクォートと互換）。 */
const yamlString = (value: string) => JSON.stringify(value)

const valueOf = (payload: SavePayload, key: 'page' | 'element', label: string) =>
	payload.sections[key]?.find(([l]) => l === label)?.[1]

/**
 * フィードバック1件を Markdown にする。AI に読ませる用途なので、
 * 見出し（コメントの1行目）→ 本文（コメント全文）→ チェックされた節、の固定の並びにする。
 */
export function toMarkdown(payload: SavePayload, created: Date): string {
	const comment = payload.comment.trim()
	const title = comment.split('\n')[0]!.trim()

	const front: string[] = []
	const url = valueOf(payload, 'page', 'URL')
	if (url) front.push(`page: ${yamlString(url)}`)
	const selector = valueOf(payload, 'element', 'セレクタ')
	if (selector) front.push(`selector: ${yamlString(selector)}`)
	front.push(`created: ${yamlString(created.toISOString())}`)

	const parts = [`---\n${front.join('\n')}\n---`, `# ${title}`, comment]
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
