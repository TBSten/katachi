// ダイアログの入力から保存の依頼（SavePayload）を作る。保存とコピーが同じ依頼を使うので 1 か所にまとめる。
import type { SavePayload, SectionKey } from '../core/protocol'
import type { Pick } from './collect'

export type PayloadInput = {
	requestId: string
	comment: string
	pick: Pick
	/** ダイアログに出ていた節の全体。 */
	offered: SavePayload['sections']
	/** チェックされた節。 */
	checked: SavePayload['sections']
	target?: SavePayload['target']
}

export function buildPayload(i: PayloadInput): SavePayload {
	const omitted = (Object.keys(i.offered) as SectionKey[]).filter((k) => !(k in i.checked))
	return {
		requestId: i.requestId,
		comment: i.comment.trim(),
		kind: i.target ?? i.pick.kind,
		...(i.target ? { target: i.target } : {}),
		sections: i.checked,
		omitted,
		// ページを開いた時刻。サーバーがソースの更新時刻と比べて stale を決める。
		pageOpenedAt: Math.round(performance.timeOrigin),
	}
}
