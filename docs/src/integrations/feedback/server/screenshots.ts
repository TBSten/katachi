// 保存の依頼に載ってきたスクリーンショット（base64 の PNG）を Store に置き、記録の「スクリーンショット」の節と frontmatter の参照にする。
// 添付は記録より先に置く（参照を記録に書くため）。置けなかった・撮れなかったときも記録は保存し、理由を節に書く。
import { SHOT_ELEMENT, SHOT_WINDOW, type Section, type SavePayload } from '../core/protocol'
import type { Store } from './store'

/** 節の行の見出し。 */
const LABELS: Record<string, string> = {
	[SHOT_WINDOW]: '画面全体（選んだ場所を赤枠で囲んだもの）',
	[SHOT_ELEMENT]: '選んだ場所のまわり',
}

export type ScreenshotResult = {
	/** 節の中身。スクリーンショットをチェックしていなければ null（節を作らない）。 */
	section: Section | null
	/** frontmatter の attachments に書く参照。 */
	refs: string[]
	/** 添付のまとめ先（記録が書けなかったとき消すために返す）。置かなければ null。 */
	dir: string | null
}

export async function storeScreenshots(store: Store, payload: SavePayload, now: Date): Promise<ScreenshotResult> {
	if (!payload.sections.screenshot) return { section: null, refs: [], dir: null }
	const failed = (reason: string): ScreenshotResult => ({ section: [['撮れませんでした', reason]], refs: [], dir: null })
	const attachments = payload.attachments ?? []
	if (attachments.length === 0) return failed(payload.screenshotError || 'ブラウザから画像が届きませんでした')
	const put = await store.putAttachments(
		attachments.map((a) => ({ name: a.name, mime: a.mime, data: new Uint8Array(Buffer.from(a.base64, 'base64')) })),
		{ now },
	)
	if (put.error) return failed(`画像を置けませんでした: ${put.error}`)
	const section: Section = attachments.map((a) => [LABELS[a.name] ?? a.name, put.refs[a.name]!])
	// 切り抜きだけ撮れなかったとき（選んだ場所が画面の外など）は、その旨を添える。
	if (payload.screenshotError) section.push(['撮れなかったもの', payload.screenshotError])
	return { section, refs: attachments.map((a) => put.refs[a.name]!), dir: put.dir }
}
