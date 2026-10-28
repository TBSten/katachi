// Shift+クリックで要素を足す・外す（Feedback）。DOM を触らない純粋な計算。
// 選んだものは 1 つなら `element`、2 つ以上なら `elements` の形に保つ（記録の kind がそのまま決まる）。
import type { Pick } from './pick'

/** 一度に選べる要素の数の上限。 */
export const MAX_ELEMENTS = 20

/** 今の選択に入っている要素（要素・複数の要素以外は空）。 */
export function elementsOf(pick: Pick | null): Element[] {
	if (pick?.kind === 'element') return [pick.el]
	if (pick?.kind === 'elements') return [...pick.els]
	return []
}

/** 要素のリストを Pick にする。0 個は null。 */
export function pickOf(els: Element[]): Pick | null {
	if (els.length === 0) return null
	return els.length === 1 ? { kind: 'element', el: els[0]! } : { kind: 'elements', els }
}

export type Toggled = { pick: Pick | null; /** 上限で足せなかった。 */ full: boolean }

/** Shift+クリックした要素を、選択に足す。すでに入っていれば外す。 */
export function togglePick(current: Pick | null, el: Element, max = MAX_ELEMENTS): Toggled {
	const els = elementsOf(current)
	const at = els.indexOf(el)
	if (at >= 0) {
		els.splice(at, 1)
		return { pick: pickOf(els), full: false }
	}
	if (els.length >= max) return { pick: current, full: true }
	return { pick: pickOf([...els, el]), full: false }
}
