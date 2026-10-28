import type { SectionKey } from '../core/protocol'
import { placeFloating } from '../shared/anchor'
import { buildDialog, type DialogHandlers, type Sections } from './dialog'
import { el, isCoarse, isNarrow } from '../shared/dom'
import { createHost, mountPanel, showToast, type PanelHandle } from '../shared/shell'
import { css } from './ui.css'

export type UiHandlers = {
	/** 案内の「やめる」ボタン。Esc と同じ動きにする。 */
	onExit: () => void
	/** 選択のそばに浮かぶ「フィードバック…」ボタン。 */
	onOpen: () => void
	/** 案内の帯の「このページ・サイトについて…」。何も選ばずに開く。 */
	onOpenPage: () => void
	/** false なら案内を出さない（モードの外で、右クリックから開いたとき）。 */
	banner?: boolean
}

/**
 * フィードバックの UI は全部この1つの shadow root の下に置く。
 * 片付けは host を remove するだけで、枠・メニュー・案内・ダイアログが全部消える。
 */
export class FeedbackUi {
	private readonly shell = createHost('katachi-feedback-root', css)
	readonly host = this.shell.host
	private readonly root = this.shell.root
	private readonly hover = el('div', 'frame')
	private readonly picked = el('div', 'frame picked')
	private readonly bannerEl = el('div', 'banner')
	private menu: HTMLElement | null = null
	/** 入力欄（シートなら幕つき、popover なら popover 自身）。 */
	private dialog: PanelHandle | null = null
	private textarea: HTMLTextAreaElement | null = null
	private submitButton: HTMLButtonElement | null = null
	private errorBox: HTMLElement | null = null
	private fab: HTMLButtonElement | null = null

	constructor(private readonly handlers: UiHandlers) {
		const banner = this.bannerEl
		banner.append(el('span', undefined, isCoarse()
			? '要素をタップ、または長押しでテキストを選んで → フィードバック…'
			: '要素をクリック、またはテキストを選んで → フィードバック…（右クリックでも）/ Esc でやめる'))
		const exit = el('button', 'exit', 'やめる')
		exit.type = 'button'
		exit.addEventListener('click', (e) => { e.stopPropagation(); this.handlers.onExit() })
		const about = el('button', 'about', 'このページ・サイトについて…')
		about.type = 'button'
		about.addEventListener('click', (e) => { e.stopPropagation(); this.handlers.onOpenPage() })
		banner.append(about, exit)
		this.root.append(this.hover, this.picked)
		if (handlers.banner !== false) this.root.append(banner)
	}

	private place(frame: HTMLElement, rect: DOMRect | null) {
		if (!rect) return void (frame.style.display = 'none')
		Object.assign(frame.style, { display: 'block', left: `${rect.left}px`, top: `${rect.top}px`, width: `${rect.width}px`, height: `${rect.height}px` })
	}
	setHover(rect: DOMRect | null) { this.place(this.hover, rect) }
	setPicked(rect: DOMRect | null) { this.place(this.picked, rect) }
	/** 案内の帯の矩形（何も選ばずに開いた popover の置き場所）。 */
	bannerRect() { return this.bannerEl.getBoundingClientRect() }

	/** 選んだものの近くに「フィードバック…」ボタンを出す。rect が null か画面の外なら隠す。 */
	setFab(rect: DOMRect | null) {
		if (!rect) return void (this.fab && (this.fab.style.display = 'none'))
		if (!this.fab) {
			const fab = el('button', 'fab', 'フィードバック…')
			fab.type = 'button'
			// 押した瞬間にページ側の選択が解除されたりフォーカスが移ったりしないようにする。
			fab.addEventListener('pointerdown', (e) => e.preventDefault())
			fab.addEventListener('click', (e) => { e.stopPropagation(); this.handlers.onOpen() })
			this.root.append(fab)
			this.fab = fab
		}
		this.fab.style.display = 'block'
		const pos = placeFloating(rect, { width: this.fab.offsetWidth, height: this.fab.offsetHeight }, { width: innerWidth, height: innerHeight })
		if (!pos) return void (this.fab.style.display = 'none')
		this.fab.style.left = `${pos.left}px`
		this.fab.style.top = `${pos.top}px`
	}

	/** hint があればメニューの下に小さく添える。 */
	showMenu(x: number, y: number, onChoose: () => void, hint?: string) {
		this.hideMenu()
		const menu = el('div', 'menu')
		const item = el('button', undefined, 'フィードバック…')
		item.type = 'button'
		// 押した瞬間にページ側の選択が解除されないようにする。
		item.addEventListener('pointerdown', (e) => e.preventDefault())
		item.addEventListener('click', (e) => { e.stopPropagation(); onChoose() })
		menu.append(item)
		if (hint) menu.append(el('div', 'hint', hint))
		this.root.append(menu)
		// 画面の端で切れないよう、置いてから大きさを測って見えている範囲に収める。
		menu.style.left = `${Math.max(0, Math.min(x, innerWidth - menu.offsetWidth - 8))}px`
		menu.style.top = `${Math.max(0, Math.min(y, innerHeight - menu.offsetHeight - 8))}px`
		this.menu = menu
	}
	hideMenu() { this.menu?.remove(); this.menu = null }
	hasMenu() { return this.menu !== null }

	/**
	 * 入力欄を出す。幅 600px 以下は下から出るシート、それより広ければ選択のそばの popover。
	 * popover は幕を敷かないので、ページは見えたままスクロールもできる。anchor は選んだものの今の矩形を返す。
	 */
	showDialog(sections: Sections, remembered: Partial<Record<SectionKey, boolean>>, h: DialogHandlers, anchor: () => DOMRect, withTarget = false) {
		this.hideMenu()
		this.setFab(null)
		const popover = !isNarrow()
		const parts = buildDialog(sections, remembered, h, popover, withTarget)
		this.dialog = mountPanel(this.root, parts.root, { popover, anchor, backdrop: true })
		this.textarea = parts.textarea
		this.submitButton = parts.submit
		this.errorBox = parts.error
		parts.textarea.focus({ preventScroll: true })
	}
	/** popover を選んだものの今の位置に置き直す（スクロール・画面サイズ・高さの変化のとき）。シートでは何もしない。 */
	updatePopover() { this.dialog?.update() }
	/** 入力欄だけを閉じる（モードは続く）。 */
	closeDialog() {
		this.dialog?.close()
		this.dialog = this.textarea = this.submitButton = this.errorBox = null
	}
	hasDialog() { return this.dialog !== null }
	/** popover が出ているか（外のクリックの扱いがシートと違う）。 */
	hasPopover() { return this.dialog?.popover ?? false }
	/** コメントが書いてあるか。書いてあるうちは外のクリックで閉じない。 */
	hasComment() { return (this.textarea?.value.trim() ?? '') !== '' }
	/** 送信中は二重送信を防ぐ。 */
	setBusy(busy: boolean) { if (this.submitButton) this.submitButton.disabled = busy }
	showError(message: string) { if (this.errorBox) this.errorBox.textContent = `保存できませんでした: ${message}`; this.setBusy(false) }

	/** 保存を知らせる小さな通知。UI 全体が消えたあとも残すため、host ごとではなく自前の host で出す。 */
	static toast(message: string, ms = 2000) { showToast('katachi-feedback-toast', css, message, ms) }

	destroy() {
		this.closeDialog()
		this.host.remove()
	}
}
