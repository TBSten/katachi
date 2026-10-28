// 「要素を選ぶ」の操作（ブラウザ側）: hover の枠・クリック／タップで要素・テキストの選択。
// 1 回選んだら終わる。Shift を押したままの選択だけ続く（続けて足す）。
// Esc でやめるのは session.ts が受ける（ここは選ぶことだけ）。
import type { SinglePick } from '../shared/pick'

/** この距離以内・この時間以内で離したタッチだけを「タップ」とみなす（それ以外はスクロールや長押し）。 */
const TAP_SLOP_PX = 10
const TAP_MAX_MS = 400
/** 長押しの選択は範囲が何度も変わる。この時間変わらなければ選び終えたとみなす。 */
const SELECTION_DEBOUNCE_MS = 200

export type PickerOptions = {
	/** 自前の UI の host。ここへの操作は選ばない。 */
	host: Element
	/** more が true なら、Shift+クリックで足しているところ（選ぶ操作は続く）。false なら、これで選び終えた。 */
	onPick(pick: SinglePick, more: boolean): void
	/** hover している要素の矩形（無ければ null）。 */
	onHover(rect: DOMRect | null): void
}

/** 選ぶ操作を始める。返す関数でやめる（リスナーを全部外す）。 */
export function startPicker(o: PickerOptions): () => void {
	const abort = new AbortController()
	const opts = { capture: true, signal: abort.signal }
	const isOwn = (e: Event) => e.composedPath().some((n) => n === o.host || (n as Element).tagName === 'ASTRO-DEV-TOOLBAR')
	const targetOf = (e: Event) => (e.target instanceof Element ? e.target : null)
	let touchDown: { x: number; y: number; time: number } | null = null
	let mouseDown = false
	/** マウスを押した位置。動いて離したものは、クリックではなくドラッグ。 */
	let downAt: { x: number; y: number } | null = null
	let dragged = false
	let selTimer: ReturnType<typeof setTimeout> | undefined
	let done = false

	const stop = () => {
		abort.abort()
		clearTimeout(selTimer)
		o.onHover(null)
	}
	/** 選び終えた。続いて来る click でリンクが動かないよう、1 回だけ飲み込む。 */
	const finish = (pick: SinglePick) => {
		if (done) return
		done = true
		stop()
		const swallow = (e: Event) => { e.preventDefault(); e.stopPropagation() }
		document.addEventListener('click', swallow, { capture: true, once: true })
		setTimeout(() => document.removeEventListener('click', swallow, true), 600)
		o.onPick(pick, false)
	}
	const pickText = (): boolean => {
		const sel = getSelection()
		const text = sel?.toString() ?? ''
		if (!sel || sel.rangeCount === 0 || text.trim() === '') return false
		finish({ kind: 'text', range: sel.getRangeAt(0).cloneRange(), text })
		getSelection()?.removeAllRanges()
		return true
	}

	document.addEventListener('pointermove', (e) => {
		if (e.pointerType === 'touch') return // タッチには hover が無い
		const t = targetOf(e)
		o.onHover(t && !isOwn(e) ? t.getBoundingClientRect() : null)
	}, opts)
	document.addEventListener('pointerdown', (e) => {
		mouseDown = e.pointerType === 'mouse' && e.button === 0
		downAt = mouseDown ? { x: e.clientX, y: e.clientY } : null
		dragged = false
		touchDown = e.pointerType === 'touch' && !isOwn(e) ? { x: e.clientX, y: e.clientY, time: e.timeStamp } : null
	}, opts)
	// iOS Safari は click ハンドラの無い要素のタップで click を出さないので、タッチのタップは pointerup で自分で判定する。
	document.addEventListener('pointerup', (e) => {
		mouseDown = false
		if (downAt) dragged = Math.hypot(e.clientX - downAt.x, e.clientY - downAt.y) > TAP_SLOP_PX
		const down = touchDown
		touchDown = null
		if (e.pointerType !== 'touch' || !down || isOwn(e)) return
		const tap = Math.hypot(e.clientX - down.x, e.clientY - down.y) < TAP_SLOP_PX && e.timeStamp - down.time < TAP_MAX_MS
		const t = targetOf(e)
		if (tap && t) finish({ kind: 'element', el: t })
	}, opts)
	document.addEventListener('pointercancel', () => { mouseDown = false; touchDown = null }, opts)
	// Shift+クリックでテキストの選択が広がらないようにする（要素を足すのに使う）。
	document.addEventListener('mousedown', (e) => { if (e.shiftKey && !isOwn(e)) e.preventDefault() }, opts)
	document.addEventListener('mouseup', (e) => {
		if (isOwn(e) || e.button !== 0) return
		pickText()
	}, opts)
	// スマホの長押し選択にはマウスのドラッグが無い。選択範囲が落ち着いてからテキストを選んだことにする。
	document.addEventListener('selectionchange', () => {
		clearTimeout(selTimer)
		selTimer = setTimeout(() => { if (!mouseDown) pickText() }, SELECTION_DEBOUNCE_MS)
	}, { signal: abort.signal })
	const onClick = (e: MouseEvent) => {
		if (isOwn(e)) return
		// リンクやボタンを動かさない。
		e.preventDefault()
		e.stopPropagation()
		const t = targetOf(e)
		// タッチの選択は pointerup で済んでいる。テキストを選んだ直後の click は要素の選択で上書きしない。
		if (!t || e.type !== 'click' || (e as PointerEvent).pointerType === 'touch') return
		if ((getSelection()?.toString().trim() ?? '') !== '') return
		// ドラッグで離した click は、押した場所と離した場所の共通の祖先に飛ぶ。要素の選択とはみなさない。
		if (dragged) return
		// Shift を押したままのクリックは、選ぶ操作を続けて要素を足していく。Shift を離したクリックか Esc で終える。
		if (e.shiftKey) return o.onPick({ kind: 'element', el: t }, true)
		finish({ kind: 'element', el: t })
	}
	document.addEventListener('click', onClick, opts)
	document.addEventListener('auxclick', onClick, opts)
	return stop
}
