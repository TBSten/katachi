// モード中のキーをページに渡さない。サイトのショートカット（Starlight の検索の `/` や Ctrl+K など）を動かさないため。
export const TOOLBAR_TAG = 'astro-dev-toolbar'
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

type KeyType = 'keydown' | 'keyup' | 'keypress'
type KeyHandler = (e: KeyboardEvent) => void
/** onKeyInside で付けた処理。guardKeys が window の capture から呼ぶ。 */
const insideHandlers = new WeakMap<EventTarget, { type: KeyType; handler: KeyHandler }[]>()

/**
 * 自前の UI（host の中）の要素にキーの処理を付ける。host の中で keydown などを聞くときは、addEventListener ではなくこれを使う。
 * モード中・メモを開いている間は、guardKeys が元のイベントを window の capture で止める（document・html の capture にある
 * サイトのショートカットより先に止めるには、そこしか無い）ので、host の中のリスナーには届かない。代わりに guardKeys がここで付けた処理を
 * 元のイベントのまま呼ぶ。guardKeys の無い間（クイックの入力欄など）は、普通のリスナーとして動く。
 * 処理の中では e.target が host に付け替わっていることがある。対象が要るなら e.composedPath()[0] を見る。
 */
export function onKeyInside(el: EventTarget, type: KeyType, handler: KeyHandler) {
	insideHandlers.set(el, [...(insideHandlers.get(el) ?? []), { type, handler }])
	el.addEventListener(type, handler as EventListener)
}

/**
 * host の中のキーを、ページへは渡さず止め、host の中で onKeyInside で付けた処理だけを、対象から host へ向かう順（bubble と同じ）に呼ぶ。
 * 合成のイベントを投げ直さないのは、Firefox が KeyboardEvent の composed: false を無視して、写しをページへ出してしまうため。
 * 文字の入力・IME・改行・Tab の移動・ボタンの Enter / Space は元のイベントの既定の動作なので、止めずに残る。
 */
export function runInsideHost(e: KeyboardEvent, host: Element) {
	e.stopPropagation()
	for (const n of e.composedPath()) {
		if (n === host) break
		for (const { type, handler } of insideHandlers.get(n) ?? []) if (type === e.type) handler(e)
	}
}

/**
 * window の capture で keydown・keyup・keypress を止める（ページのどのリスナーより先に来る）。
 * - Astro の toolbar の上のキーは通す。
 * - 自前の UI（host）の中のキーもページへは渡さず、onKeyInside で付けた処理にだけ届ける（runInsideHost）。文字入力・IME・改行・ボタン操作は残る。
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
			// 自前の UI の中は、ページへは渡さず、dialog・add-field 自身のキー処理（onKeyInside）にだけ届ける。既定の動作（文字の入力・改行・ボタン操作）も残る。
			if (e.composedPath().includes(o.host)) return runInsideHost(e, o.host)
			e.stopImmediatePropagation()
		}, { capture: true, signal: o.signal })
	}
}
