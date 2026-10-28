import { mkdir, open, readdir } from 'node:fs/promises'
import { join } from 'node:path'
import { toMarkdown } from './markdown'
import type { SavePayload } from './protocol'

const MAX_PER_DAY = 999

/** `yymmdd`。日付はサーバーのローカル時刻で決める。 */
export function datePrefix(now: Date): string {
	const two = (n: number) => String(n).padStart(2, '0')
	return `${two(now.getFullYear() % 100)}${two(now.getMonth() + 1)}${two(now.getDate())}`
}

/**
 * その日の番号のうち最大のもの。無ければ 0。
 * 2 桁だったころのファイル（`yymmdd-NN.md`）も数えるので、桁を変えても番号が巻き戻らない。
 */
async function lastNumber(dir: string, prefix: string): Promise<number> {
	const pattern = new RegExp(`^${prefix}-(\\d+)\\.md$`)
	let max = 0
	for (const name of await readdir(dir)) {
		const m = pattern.exec(name)
		if (m) max = Math.max(max, Number(m[1]))
	}
	return max
}

/**
 * `<dir>/yymmdd-NNN.md` に排他的に書く。
 * ls で決めた番号は同時の保存で取り合うので、そこから `wx` で作れた番号を採用する。
 * 既にある番号は EEXIST で次へ進む。
 */
export async function saveFeedback(dir: string, payload: SavePayload, now = new Date()): Promise<string> {
	await mkdir(dir, { recursive: true })
	const body = toMarkdown(payload, now)
	const prefix = datePrefix(now)
	for (let n = (await lastNumber(dir, prefix)) + 1; n <= MAX_PER_DAY; n++) {
		const name = `${prefix}-${String(n).padStart(3, '0')}.md`
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
	throw new Error(`その日の連番（${MAX_PER_DAY}）を使い切りました`)
}
