// popover を header のつかみでドラッグして動かす。Feedback と Memo の共通部分（mountPanel から使う）。
// 純粋な計算（clampToView・parsePos）と、pointer events の配線（attachDrag）を分けてある。

export type Pos = { left: number; top: number }

/** 画面の端に残す余白。 */
const EDGE = 4

/** パネルが画面の外にはみ出さない位置に収める。パネルが画面より大きければ左上に寄せる。 */
export function clampToView(pos: Pos, size: { width: number; height: number }, view: { width: number; height: number }, edge = EDGE): Pos {
	const max = (v: number, s: number) => Math.max(edge, v - s - edge)
	return { left: Math.min(Math.max(pos.left, edge), max(view.width, size.width)), top: Math.min(Math.max(pos.top, edge), max(view.height, size.height)) }
}

/** 保存した文字列から位置を読む。壊れていれば null。 */
export function parsePos(raw: string | null): Pos | null {
	if (!raw) return null
	try {
		const v = JSON.parse(raw) as Partial<Pos>
		return typeof v.left === 'number' && typeof v.top === 'number' && Number.isFinite(v.left) && Number.isFinite(v.top) ? { left: v.left, top: v.top } : null
	} catch {
		return null
	}
}

/** ドラッグの始点にしない要素（操作部品）。 */
export const NO_DRAG = 'button, input, textarea, select, summary, label, a, [contenteditable]'

/** header にこの class を付けると、つかめる見た目（move カーソルとつまみ）になる。shadow root の CSS に足す。 */
export const DRAG_CSS = `
.draggable { cursor: move; touch-action: none; user-select: none; -webkit-user-select: none; }
.draggable::before { content: '⠿'; flex: none; color: #a3a3a3; font-size: 16px; line-height: 1; margin-right: 2px; }
.draggable button, .draggable input, .draggable label, .draggable summary { cursor: pointer; }
`

export type DragOptions = {
	panel: HTMLElement
	handle: HTMLElement
	/** 動かした（または戻した）とき。null なら元の位置に戻した。 */
	onMove(pos: Pos | null): void
	/** 動かし終えたとき（保存の契機）。 */
	onEnd(pos: Pos): void
	signal: AbortSignal
}

/** handle のつかみで panel を動かす。ボタンなどの上から始めたドラッグは動かさない。ダブルクリックで戻す。 */
export function attachDrag(o: DragOptions) {
	const { panel, handle, signal } = o
	handle.classList.add('draggable')
	let grab: { dx: number; dy: number; id: number } | null = null
	let last: Pos | null = null
	const place = (e: PointerEvent) => {
		const size = { width: panel.offsetWidth, height: panel.offsetHeight }
		return clampToView({ left: e.clientX - grab!.dx, top: e.clientY - grab!.dy }, size, { width: innerWidth, height: innerHeight })
	}
	handle.addEventListener('pointerdown', (e) => {
		if (e.button !== 0 || (e.target instanceof Element && e.target.closest(NO_DRAG))) return
		const r = panel.getBoundingClientRect()
		grab = { dx: e.clientX - r.left, dy: e.clientY - r.top, id: e.pointerId }
		last = null
		handle.setPointerCapture(e.pointerId)
		e.preventDefault()
	}, { signal })
	handle.addEventListener('pointermove', (e) => {
		if (!grab || e.pointerId !== grab.id) return
		last = place(e)
		o.onMove(last)
	}, { signal })
	const end = (e: PointerEvent) => {
		if (!grab || e.pointerId !== grab.id) return
		grab = null
		if (last) o.onEnd(last)
	}
	handle.addEventListener('pointerup', end, { signal })
	handle.addEventListener('pointercancel', end, { signal })
	handle.addEventListener('dblclick', (e) => {
		if (e.target instanceof Element && e.target.closest(NO_DRAG)) return
		o.onMove(null)
	}, { signal })
}
