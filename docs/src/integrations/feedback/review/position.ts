// popup の置き場所（純粋な計算）。指摘の要素の右 → 左 → 下か上、の順に試す。
import { placePopover } from '../shared/anchor'

type Box = { left: number; top: number; right: number; bottom: number }
type Size = { width: number; height: number }

export type Side = 'right' | 'left' | 'below'

const GAP = 12
const MARGIN = 8

/** 横に置けるか（幅 min 以上の空きがあるか）を見て、置く側と幅を決める。横に置けなければ要素の下か上（幅 want）。 */
export function chooseSide(rect: Box, view: Size, want: number, min: number): { side: Side; width: number } {
	const right = view.width - rect.right - GAP - MARGIN
	const left = rect.left - GAP - MARGIN
	if (right >= min) return { side: 'right', width: Math.min(want, right) }
	if (left >= min) return { side: 'left', width: Math.min(want, left) }
	return { side: 'below', width: Math.min(want, view.width - MARGIN * 2) }
}

/** 決めた側と、測った大きさから左上を決める。横に置くときは要素の上端にそろえ、画面に収める。 */
export function placeAt(side: Side, rect: Box, size: Size, view: Size): { left: number; top: number } {
	if (side === 'below') return placePopover(rect, size, view)
	const left = side === 'right' ? rect.right + GAP : rect.left - GAP - size.width
	const maxTop = Math.max(MARGIN, view.height - size.height - MARGIN)
	const top = Math.min(Math.max(rect.top, MARGIN), maxTop)
	return { left, top }
}
