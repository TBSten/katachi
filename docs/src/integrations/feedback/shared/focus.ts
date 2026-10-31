// モード中は、ページの編集欄（input・textarea・select・contenteditable）にフォーカスを入れない。
// クリックの横取り（mode.ts）は click で止めるので、それより前の mousedown や Tab でフォーカスが入ると、
// キーの伝播を止めても既定の編集（文字の入力・貼り付け・select の矢印）で中身が変わってしまう。
import { DEV_UI_ATTR } from './shell'
import { inToolbar, TOOLBAR_TAG } from './keys'

/** ページの編集欄か。 */
export const isPageEditor = (n: unknown): n is HTMLElement =>
	n instanceof HTMLElement && (n.matches('input, textarea, select') || n.isContentEditable)

/** 自前の UI（Feedback・メモの host）か Astro の toolbar の中のイベントか。 */
export const inDevUi = (e: Event) =>
	inToolbar(e) || e.composedPath().some((n) => n instanceof Element && n.hasAttribute(DEV_UI_ATTR))

/** フォーカスのあるページの要素を、ページの shadow root の中までたどる。自前の UI と toolbar の中なら null。 */
function pageActiveElement(): Element | null {
	let a = document.activeElement
	while (a) {
		if (a.hasAttribute(DEV_UI_ATTR) || a.localName === TOOLBAR_TAG) return null
		if (!a.shadowRoot?.activeElement) return a
		a = a.shadowRoot.activeElement
	}
	return null
}

/**
 * signal が abort されるまで、ページの編集欄にフォーカスが入ったら外す。モードに入る前から入っていた分も外す。
 * - 自前の UI（Feedback・メモ）と Astro の toolbar の入力欄は使えるまま。
 * - 外したら、直前まで自前の UI（host の中）にあったフォーカスをそこへ戻す（入力欄から Tab で抜けても書く場所を失わない）。
 * - 外すだけだと Firefox は次の Tab がまた同じ欄に入って先へ進めないので、外した欄は Tab の順から抜き（tabindex=-1）、
 *   abort のときに元の tabindex に戻す。
 */
export function guardFocus(o: { host: Element; signal: AbortSignal }) {
	const untabbed = new Map<HTMLElement, string | null>()
	let lastOwn: HTMLElement | null = null
	const keepOut = (el: HTMLElement) => {
		if (!untabbed.has(el)) {
			untabbed.set(el, el.getAttribute('tabindex'))
			el.tabIndex = -1
		}
		el.blur()
		if (lastOwn?.isConnected) lastOwn.focus({ preventScroll: true })
	}
	window.addEventListener('focusin', (e) => {
		const t = e.composedPath()[0]
		if (inDevUi(e)) {
			if (e.composedPath().includes(o.host) && t instanceof HTMLElement) lastOwn = t
			return
		}
		if (isPageEditor(t)) keepOut(t)
	}, { capture: true, signal: o.signal })
	const active = pageActiveElement()
	if (isPageEditor(active)) keepOut(active)
	o.signal.addEventListener('abort', () => {
		for (const [el, tabindex] of untabbed) {
			if (tabindex === null) el.removeAttribute('tabindex')
			else el.setAttribute('tabindex', tabindex)
		}
		untabbed.clear()
	}, { once: true })
}
