import { mkdir, open } from 'node:fs/promises'
import { join } from 'node:path'
import { toMarkdown } from './markdown'
import type { SavePayload } from './protocol'

/** `yymmdd`。日付はサーバーのローカル時刻で決める。 */
export function datePrefix(now: Date): string {
	const two = (n: number) => String(n).padStart(2, '0')
	return `${two(now.getFullYear() % 100)}${two(now.getMonth() + 1)}${two(now.getDate())}`
}

/**
 * `<dir>/yymmdd-NN.md` に排他的に書く。
 * 先に ls して番号を決めると同時の保存で取り合うので、`wx` で作れた番号を採用する。
 * 既にある番号は EEXIST で次へ進む。
 */
export async function saveFeedback(dir: string, payload: SavePayload, now = new Date()): Promise<string> {
	await mkdir(dir, { recursive: true })
	const body = toMarkdown(payload, now)
	for (let n = 1; n <= 99; n++) {
		const name = `${datePrefix(now)}-${String(n).padStart(2, '0')}.md`
		let handle
		try {
			handle = await open(join(dir, name), 'wx')
		} catch (e) {
			if ((e as NodeJS.ErrnoException).code === 'EEXIST') continue
			throw e
		}
		try {
			await handle.writeFile(body, 'utf8')
		} finally {
			await handle.close()
		}
		return name
	}
	throw new Error('その日の連番（99）を使い切りました')
}
