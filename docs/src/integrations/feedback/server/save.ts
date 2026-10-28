// 保存の依頼 → 記録の Markdown → Store。採番・排他的な書き込みは Store の putEntry がする。
import { toMarkdown } from '../core/record'
import type { SavePayload } from '../core/protocol'
import type { Store } from './store'

/** 保存して、作られたファイル名（`yymmdd-NNN.md`）を返す。 */
export async function saveFeedback(store: Store, payload: SavePayload, now = new Date()): Promise<string> {
	const entry = await store.putEntry({ text: toMarkdown(payload, now), now })
	return `${entry.id}.md`
}
