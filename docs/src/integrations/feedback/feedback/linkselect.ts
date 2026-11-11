// カード全体を a::before の覆いでクリックにしている部品（Starlight / docs の LinkCard）の中の文字を、ドラッグで選べるようにする。
// 覆いが文字の上にあるのと、a の中のドラッグはリンクのドラッグになってしまうのが原因。本番の見た目と動きは変えず、dev のツールだけが直す。
//   - モード（toolbar のアプリを on にしている間）: 常に選べる。クリックは要素の選択になるので、リンクは動かない。
//   - モードの外: Alt を押している間だけ選べる（押しながらドラッグ）。Alt を押さない普段のクリックはリンクのまま。
// 「選べる」状態は <html data-katachi-fb-select> で表し、効かせる CSS は dev でだけ head に足す（本番ビルドには出ない）。

const ATTR = 'data-katachi-fb-select'
const STYLE_ID = 'katachi-fb-select-style'
const CSS = `
html[${ATTR}] .sl-link-card a::before { pointer-events: none !important; }
html[${ATTR}] a, html[${ATTR}] a * { -webkit-user-drag: none; user-select: text; -webkit-user-select: text; }
`

function ensureStyle() {
	if (document.getElementById(STYLE_ID)) return
	const style = document.createElement('style')
	style.id = STYLE_ID
	style.textContent = CSS
	document.head.append(style)
}

const set = (on: boolean) => document.documentElement.toggleAttribute(ATTR, on)

/** モードの間だけ、リンクカードの中の文字を選べるようにする。 */
export function setLinkSelectMode(on: boolean) {
	if (on) ensureStyle()
	set(on)
}

/**
 * モードの外の Alt+ドラッグ。常設するのは keydown 1 つだけで、Alt を押している間だけ他のリスナーを付ける。
 * 押下の瞬間（pointerdown）に attribute を付けても遅い: ブラウザは押下の対象を先に決めるので、覆いの上の押下になってしまう。
 * だから Alt を押した時点で付ける。
 */
export function setupAltSelect(isModeOn: () => boolean) {
	document.addEventListener('keydown', (e) => {
		if (e.key !== 'Alt' || e.repeat || isModeOn()) return
		ensureStyle()
		set(true)
		const ctl = new AbortController()
		const opts = { capture: true, signal: ctl.signal }
		const done = () => {
			ctl.abort()
			set(false)
		}
		// Alt+クリックはブラウザがリンク先を保存しようとする。ドラッグで文字を選んだあとの click では、それを動かさない。
		document.addEventListener('click', (ev) => {
			if (ev.altKey && (getSelection()?.toString() ?? '') !== '') ev.preventDefault()
		}, opts)
		document.addEventListener('keyup', (ev) => { if (ev.key === 'Alt') done() }, opts)
		window.addEventListener('blur', done, opts)
	}, true)
}
