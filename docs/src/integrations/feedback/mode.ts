// モード（toolbar のアプリを on にしている間）の操作。要素のクリック・テキストの選択・右クリックのメニュー・浮かぶボタン。
import { rectOfPick } from './anchor'
import type { Pick } from './collect'
import type { Flow } from './flow'

/** 長押しの選択は範囲が何度も変わる。この時間変わらなければ選び終えたとみなす。 */
const SELECTION_DEBOUNCE_MS = 200
/** この距離以内・この時間以内で離したタッチだけを「タップ」とみなす（それ以外はスクロールや長押し）。 */
const TAP_SLOP_PX = 10
const TAP_MAX_MS = 400

export function enterMode(flow: Flow) {
	const s = flow.begin(false)
	if (!s) return
	const { ui, abort } = s
	const opts = { capture: true, signal: abort.signal }
	// 自前の UI と Astro の toolbar 自身の操作は、選ぶ対象にしない。
	const isOwn = (e: Event) => e.composedPath().some((n) => n === ui.host || (n as Element).tagName === 'ASTRO-DEV-TOOLBAR')
	const targetOf = (e: Event) => (e.target instanceof Element ? e.target : null)
	let swallowClick = false
	let pointerType = 'mouse' // 直近に押された入力の種類。タッチかどうかで挙動を変える。
	let touchDown: { x: number; y: number; time: number } | null = null // タップ判定用。動いたり長く押したりしたらタップではない。
	let mouseDown = false // マウスのドラッグ中は選択が途中なので、selectionchange では確定しない。
	let selTimer: ReturnType<typeof setTimeout> | undefined
	abort.signal.addEventListener('abort', () => clearTimeout(selTimer))
	/** 枠と、選択のそばのボタンを今の選択に合わせて置き直す。 */
	const refreshPicked = () => {
		const p = s.pick
		ui.setPicked(p?.kind === 'element' ? p.el.getBoundingClientRect() : null)
		ui.setFab(p && p.kind !== 'page' && !ui.hasDialog() ? rectOfPick(p) : null)
		ui.updatePopover()
	}
	const clearPick = () => {
		s.pick = null
		refreshPicked()
		if (s.sawText) getSelection()?.removeAllRanges()
	}
	const pickText = () => {
		const sel = getSelection()
		const text = sel?.toString() ?? ''
		if (!sel || sel.rangeCount === 0 || text.trim() === '') return false
		s.pick = { kind: 'text', range: sel.getRangeAt(0).cloneRange(), text }
		s.sawText = true
		refreshPicked()
		return true
	}

	document.addEventListener('pointermove', (e) => {
		// タッチには hover が無いので枠を出さない。
		if (e.pointerType === 'touch') return
		const t = targetOf(e)
		ui.setHover(t && !isOwn(e) && !ui.hasDialog() ? t.getBoundingClientRect() : null)
	}, opts)
	document.addEventListener('pointerdown', (e) => {
		pointerType = e.pointerType
		mouseDown = e.pointerType === 'mouse' && e.button === 0
		touchDown = e.pointerType === 'touch' && !isOwn(e) ? { x: e.clientX, y: e.clientY, time: e.timeStamp } : null
		if (isOwn(e) || e.button !== 0) return
		swallowClick = false
		// マウスはメニューの外、タッチはボタンが出ているときの選択の外が「やめる」。
		// どちらもこの1回で別の要素を選び直しはしない。長押しのように click が来ない操作でも、次の押下で解除される。
		// popover が出ているときは、外を押しても別の要素を選び直さない。コメントが空なら閉じ、書いてあれば書いた文を守って閉じない。
	if (ui.hasPopover()) {
		swallowClick = true
		if (!ui.hasComment()) { ui.closeDialog(); clearPick() }
		return
	}
	const dismiss = e.pointerType === 'touch' ? s.pick !== null && !ui.hasDialog() : ui.hasMenu()
		if (dismiss) {
			ui.hideMenu()
			clearPick()
			swallowClick = true
		}
	}, opts)
	// iOS Safari は、click ハンドラを持たない要素（段落など）のタップでは click を出さない。
	// document の capture リスナーは数えられないので、タッチのタップは pointerup で自分で判定して要素を選ぶ。
	document.addEventListener('pointerup', (e) => {
		mouseDown = false
		const down = touchDown
		touchDown = null
		if (e.pointerType !== 'touch' || !down || isOwn(e)) return
		const isTap = Math.hypot(e.clientX - down.x, e.clientY - down.y) < TAP_SLOP_PX && e.timeStamp - down.time < TAP_MAX_MS
		if (swallowClick) { swallowClick = false; return }
		const t = targetOf(e)
		if (!isTap || !t) return
		if (s.sawText) getSelection()?.removeAllRanges()
		s.pick = { kind: 'element', el: t }
		refreshPicked()
	}, opts)
	document.addEventListener('pointercancel', () => { mouseDown = false; touchDown = null }, opts)
	document.addEventListener('mouseup', (e) => {
		if (isOwn(e) || e.button !== 0 || swallowClick) return
		pickText()
		if (s.pick?.kind === 'text') ui.setPicked(null)
	}, opts)
	// スマホの長押し選択にはマウスのドラッグが無い。選択範囲が変わったら、落ち着いてからテキストを選んだことにする。
	document.addEventListener('selectionchange', () => {
		clearTimeout(selTimer)
		selTimer = setTimeout(() => {
			if (mouseDown || ui.hasDialog()) return
			pickText()
		}, SELECTION_DEBOUNCE_MS)
	}, { signal: abort.signal })
	const onClick = (e: MouseEvent) => {
		if (isOwn(e)) return
		// リンクやボタンを動かさない（タップも click になるので、これでスマホでも動かない）。
		e.preventDefault()
		e.stopPropagation()
		// タッチの要素選択は pointerup で済んでいる。ここはリンクを動かさないためだけに通る。
		if (pointerType === 'touch') return
		if (swallowClick) { swallowClick = false; return }
		// 長押し・ドラッグでテキストを選んだ直後の click は、要素の選択で上書きしない。
		if (e.type === 'click' && s.pick?.kind === 'text' && (getSelection()?.toString().trim() ?? '') !== '') return
		const t = targetOf(e)
		if (!t || e.type !== 'click') return
		if (s.sawText) getSelection()?.removeAllRanges()
		s.pick = { kind: 'element', el: t }
		refreshPicked()
	}
	document.addEventListener('click', onClick, opts)
	document.addEventListener('auxclick', onClick, opts)
	document.addEventListener('contextmenu', (e) => {
		if (isOwn(e) || ui.hasDialog()) return
		// 何も選んでいないときはブラウザ標準のメニューのまま。
		if (!s.pick) return
		e.preventDefault()
		e.stopPropagation()
		// タッチの長押しでも contextmenu が来る端末がある。そのときは自前メニューを出さず、そばのボタンに任せる。
		if (pointerType === 'touch') return
		ui.setHover(null)
		ui.showMenu(e.clientX, e.clientY, flow.openDialog)
	}, opts)
	document.addEventListener('keydown', (e) => {
		if (e.key === 'Escape') { e.preventDefault(); flow.exit() }
	}, opts)
	// 選んだ枠とボタンが、スクロールや画面サイズの変更に追従するように。
	window.addEventListener('scroll', refreshPicked, opts)
	window.addEventListener('resize', refreshPicked, opts)
	// ページ遷移（View Transitions を使うサイトでも）に UI を持ち越さない。
	document.addEventListener('astro:before-swap', flow.exit, opts)
	window.addEventListener('pagehide', flow.cleanup, opts)
}
