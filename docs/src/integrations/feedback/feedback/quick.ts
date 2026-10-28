// モードに入っていなくても、テキストを選んで右クリックすれば「フィードバック…」を出す。
// 常設するのは contextmenu のリスナー 1 つだけ。メニューや popover を出している間だけ他のリスナーを付け、閉じたら外す。
import type { Flow } from './flow'

const HINT = 'Shift+右クリックでブラウザのメニュー'

/** 入力欄の中かどうか。ここの右クリックはブラウザ標準のメニュー（コピー・貼り付けなど）を使いたい。 */
const isEditable = (t: EventTarget | null) =>
	t instanceof Element && (t.closest('input, textarea, select') !== null || (t as HTMLElement).isContentEditable)

export function setupQuick(flow: Flow) {
	document.addEventListener('contextmenu', (e) => {
		const current = flow.session
		// モードの中の右クリックはモード側が扱う。
		if (current && !current.quick) return
		// 自前の UI と Astro の toolbar の上は何もしない。
		if (e.composedPath().some((n) => n === current?.ui.host || (n as Element).tagName === 'ASTRO-DEV-TOOLBAR')) return
		// 出していた分は、書きかけがなければ片付ける。選択は消さない（このあと出し直すかもしれない）。
		if (current) {
			if (current.ui.hasComment()) return // 書きかけの popover は、別の場所の右クリックでは壊さない
			current.sawText = false
			flow.cleanup()
		}
		// Shift+右クリックはブラウザ標準のメニュー。タッチの長押し（pointer: coarse）も標準のまま。
		if (e.shiftKey || matchMedia('(pointer: coarse)').matches) return
		if (isEditable(e.target)) return
		const sel = getSelection()
		const text = sel?.toString() ?? ''
		if (!sel || sel.rangeCount === 0 || text.trim() === '') return

		const s = flow.begin(true)
		if (!s) return
		e.preventDefault()
		s.pick = { kind: 'text', range: sel.getRangeAt(0).cloneRange(), text }
		s.sawText = true
		open(flow, s, e.clientX, e.clientY)
	})
}

function open(flow: Flow, s: NonNullable<Flow['session']>, x: number, y: number) {
	const { ui } = s
	// ここから先のリスナーは、この1回ぶんの signal で登録する。flow.cleanup() でまとめて外れる。
	const opts = { capture: true, signal: s.abort.signal }
	ui.showMenu(x, y, flow.openDialog, HINT)
	// 自前の UI の外を押したらやめる。ただしコメントを書いている popover は、書いた文を失わないよう閉じない。
	// 右ボタンは、続く contextmenu 側で扱う（選択を消さずに出し直せるように）。
	document.addEventListener('pointerdown', (e) => {
		if (e.button === 2 || e.composedPath().includes(ui.host)) return
		if (ui.hasDialog() && ui.hasComment()) return
		flow.exit()
	}, opts)
	document.addEventListener('keydown', (e) => {
		if (e.key === 'Escape') { e.preventDefault(); flow.exit() }
	}, opts)
	// popover が選択を追うように。
	window.addEventListener('scroll', () => ui.updatePopover(), opts)
	window.addEventListener('resize', () => ui.updatePopover(), opts)
	// ページ遷移に UI を持ち越さない。
	document.addEventListener('astro:before-swap', flow.exit, opts)
	window.addEventListener('pagehide', flow.cleanup, opts)
}
