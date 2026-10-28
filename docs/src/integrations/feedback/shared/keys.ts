// モード中のキーをページに渡さない。サイトのショートカット（Starlight の検索の `/` や Ctrl+K など）を動かさないため。
const TOOLBAR_TAG = 'astro-dev-toolbar'
export const inToolbar = (e: Event) => e.composedPath().some((n) => n instanceof Element && n.localName === TOOLBAR_TAG)

/** 次の Esc の keyup を toolbar に渡さない（Esc で閉じたあとに届く keyup が toolbar を隠さないように）。 */
export function swallowEscKeyup() {
	const swallow = (e: KeyboardEvent) => {
		if (e.key !== 'Escape') return
		e.stopPropagation()
		window.removeEventListener('keyup', swallow, true)
	}
	window.addEventListener('keyup', swallow, true)
	setTimeout(() => window.removeEventListener('keyup', swallow, true), 1000)
}

/**
 * window の capture で keydown・keyup・keypress を止める（ページのどのリスナーより先に来る）。
 * - Astro の toolbar の上のキーは通す。
 * - 自前の UI（host）の中は素通し。host 側（createHost）が伝わるのを止める。文字入力・改行・ボタン操作は残る。
 * - ページの上のキーは、ページのリスナーへ届かせない（stopImmediatePropagation）だけ。preventDefault はしないので、ブラウザの既定の動作
 *   （Cmd+C のコピー・Cmd+F の検索・Space や矢印のスクロール・Tab の移動）は残る。Enter・Space でリンクやボタンが動くのは、
 *   それが click になり、モード側の click の横取り（preventDefault + stopPropagation）で止まるため。
 * - Esc（IME の変換中を除く）は onEscape があればここで扱う。無ければ素通し（呼び出し側が自前で扱う）。
 */
export function guardKeys(o: { host: Element; signal: AbortSignal; onEscape?: () => void }) {
	for (const type of ['keydown', 'keyup', 'keypress'] as const) {
		window.addEventListener(type, (e) => {
			if (inToolbar(e)) return
			if (e.key === 'Escape' && !e.isComposing) {
				if (!o.onEscape) return
				e.preventDefault()
				e.stopPropagation()
				if (type === 'keydown') {
					swallowEscKeyup()
					o.onEscape()
				}
				return
			}
			// 自前の UI の中は、ここでは止めない（dialog・note-editor 自身のキー処理を生かす）。
			// ページへ伝わるのは host の bubble で止まる（shell.ts の createHost）。既定の動作（文字の入力・改行・ボタン操作）も残る。
			if (e.composedPath().includes(o.host)) return
			e.stopImmediatePropagation()
		}, { capture: true, signal: o.signal })
	}
}
