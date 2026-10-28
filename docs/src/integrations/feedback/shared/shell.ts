// Feedback と Memo が共有する「殻」: shadow host・トースト・シートと popover の出し分け。
// どちらも UI は 1 つの shadow root の下に置き、host を remove するだけで全部消える。
import { placePopover } from './anchor'
import { attachDrag, clampToView, parsePos, type Pos } from './drag'
import { el } from './dom'

/**
 * shadow host を作って画面に付ける。ダイアログ内のキー入力がページ側（Starlight の検索ショートカットなど）に届かないようにする。
 * css は shadow root の先頭の <style> に入る。
 */
export function createHost(tag: string, css: string): { host: HTMLElement; root: ShadowRoot } {
	const host = document.createElement(tag)
	const root = host.attachShadow({ mode: 'open' })
	const style = document.createElement('style')
	style.textContent = css
	root.append(style)
	for (const type of ['keydown', 'keyup', 'keypress']) host.addEventListener(type, (e) => e.stopPropagation())
	document.documentElement.append(host)
	return { host, root }
}

/** 保存を知らせる小さな通知。UI 全体が消えたあとも残すため、自前の host で出す。 */
export function showToast(tag: string, css: string, message: string, ms = 2000) {
	const host = document.createElement(tag)
	const root = host.attachShadow({ mode: 'open' })
	const style = document.createElement('style')
	style.textContent = css
	root.append(style, el('div', 'toast', message))
	document.documentElement.append(host)
	setTimeout(() => host.remove(), ms)
}

/** ソフトキーボードで見える範囲が縮んでも、シートが見える範囲に収まるようにする。外す関数を返す。 */
export function fitToVisualViewport(backdrop: HTMLElement): () => void {
	const vv = window.visualViewport
	if (!vv) return () => {}
	const fit = () => {
		backdrop.style.top = `${vv.offsetTop}px`
		backdrop.style.height = `${vv.height}px`
	}
	fit()
	vv.addEventListener('resize', fit)
	vv.addEventListener('scroll', fit)
	return () => { vv.removeEventListener('resize', fit); vv.removeEventListener('scroll', fit) }
}

export type PanelOptions = {
	/** true なら popover、false なら下から出るシート。呼び出し側が isNarrow() で決める（中身の作りも変わるため）。 */
	popover: boolean
	/** popover を置く基準の矩形（選んだもののそば）。無ければ置き直しをしない（CSS の固定位置に任せる）。 */
	anchor?: () => DOMRect
	/** シートの裏に幕を敷く（Feedback）。false なら幕は無く、ページは見えたまま触れる（Memo）。 */
	backdrop?: boolean
	/**
	 * 指定すると、popover を handle（header）のつかみで動かせる（シートでは動かさない）。
	 * 動かしたら anchor を追うのをやめる。storageKey があれば localStorage に位置を覚えて次も使う。ダブルクリックで元に戻す。
	 */
	drag?: { handle: HTMLElement; storageKey?: string }
}

export type PanelHandle = {
	popover: boolean
	/** 画面に付けた一番外側（幕があれば幕、無ければ panel 自身）。 */
	outer: HTMLElement
	/** popover を anchor の今の位置に置き直す。シートでは何もしない。 */
	update(): void
	close(): void
}

/** 位置の保存。読み書きできなくても動く（プライベートウィンドウなど）。 */
const loadPos = (key?: string): Pos | null => {
	if (!key) return null
	try {
		return parsePos(localStorage.getItem(key))
	} catch {
		return null
	}
}
const savePos = (key: string | undefined, pos: Pos | null) => {
	if (!key) return
	try {
		if (pos) localStorage.setItem(key, JSON.stringify(pos))
		else localStorage.removeItem(key)
	} catch {
		// 覚えられなくても動く。
	}
}

/** panel を root に付ける。popover は anchor のそばに置き、中身の高さが変わったら置き直す。 */
export function mountPanel(root: ShadowRoot, panel: HTMLElement, o: PanelOptions): PanelHandle {
	let resizeObserver: ResizeObserver | null = null
	let viewportCleanup: (() => void) | null = null
	let outer: HTMLElement = panel
	const abort = new AbortController()
	/** 利用者が動かした位置。あれば anchor より優先し、画面に収め続ける。 */
	let moved: Pos | null = o.popover ? loadPos(o.drag?.storageKey) : null
	const update = () => {
		if (!o.popover) return
		if (moved) {
			// 画面の大きさや中身の高さが変わっても、はみ出さないように戻す。
			const pos = clampToView(moved, { width: panel.offsetWidth, height: panel.offsetHeight }, { width: innerWidth, height: innerHeight })
			Object.assign(panel.style, { left: `${pos.left}px`, top: `${pos.top}px`, right: 'auto', bottom: 'auto' })
			return
		}
		if (!o.anchor) {
			// 動かす前の位置は CSS に任せる（動かしたあとで戻したときに、inline の指定を外す）。
			Object.assign(panel.style, { left: '', top: '', right: '', bottom: '' })
			return
		}
		const pos = placePopover(o.anchor(), { width: panel.offsetWidth, height: panel.offsetHeight }, { width: innerWidth, height: innerHeight })
		panel.style.left = `${pos.left}px`
		panel.style.top = `${pos.top}px`
	}
	if (o.popover) {
		root.append(panel)
		update()
		// 書いたり畳んだ項目を開いたりして高さが変わったら置き直す（下に入らなくなれば上へ）。
		if (o.anchor || o.drag) {
			resizeObserver = new ResizeObserver(update)
			resizeObserver.observe(panel)
		}
		if (o.drag) {
			attachDrag({
				panel,
				handle: o.drag.handle,
				signal: abort.signal,
				onMove(pos) {
					moved = pos
					update()
				},
				onEnd: (pos) => savePos(o.drag?.storageKey, pos),
			})
			// 戻す（ダブルクリック）ときは、覚えた位置も消す。
			o.drag.handle.addEventListener('dblclick', () => { if (!moved) savePos(o.drag?.storageKey, null) }, { signal: abort.signal })
			window.addEventListener('resize', update, { signal: abort.signal })
		}
	} else {
		// シートは幕（Feedback）か、触れない枠（Memo。ページは見えたまま触れる）の中に置く。どちらもソフトキーボードで見える範囲に収める。
		const frame = el('div', o.backdrop ? 'backdrop' : 'sheet-frame')
		frame.append(panel)
		root.append(frame)
		viewportCleanup = fitToVisualViewport(frame)
		outer = frame
	}
	return {
		popover: o.popover,
		outer,
		update,
		close() {
			abort.abort()
			resizeObserver?.disconnect()
			viewportCleanup?.()
			outer.remove()
		},
	}
}
