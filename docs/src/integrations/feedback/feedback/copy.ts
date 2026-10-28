// 「Markdown をコピー」。保存と同じ文（core/record.ts）をクリップボードへ。サーバーが決める部分（id・commit・git・ビルド）は入らない。
import type { SavePayload } from '../core/protocol'
import { toCopyMarkdown } from '../core/record'

/** コピーボタンの近くに出す断り書き。 */
export const COPY_NOTE = '保存される記録と同じ文です。git・ビルドの節と id・commit はサーバーが保存するときに付けるので、コピーには入りません。'

/** コピーする文。 */
export const copyText = (payload: SavePayload, now = new Date()) => toCopyMarkdown(payload, now)

/** クリップボードに書く。成功したら true。localhost は安全な文脈なので clipboard API が使える。使えなければ textarea で代用する。 */
export async function copyToClipboard(text: string): Promise<boolean> {
	try {
		await navigator.clipboard.writeText(text)
		return true
	} catch {
		const area = document.createElement('textarea')
		area.value = text
		area.style.cssText = 'position:fixed;left:-9999px;top:0'
		document.body.append(area)
		area.select()
		try {
			return document.execCommand('copy')
		} catch {
			return false
		} finally {
			area.remove()
		}
	}
}
