import { SECTION_KEYS, SECTION_TITLES, type Section, type SectionKey } from './protocol'
import { css } from './ui.css'

type Sections = Partial<Record<SectionKey, Section>>

const el = <K extends keyof HTMLElementTagNameMap>(tag: K, cls?: string, text?: string) => {
	const e = document.createElement(tag)
	if (cls) e.className = cls
	if (text) e.textContent = text
	return e
}

export type DialogHandlers = {
	/** チェックされた節だけを渡す。 */
	onSubmit: (comment: string, checked: Sections) => void
	onCancel: () => void
}

/**
 * フィードバックの UI は全部この1つの shadow root の下に置く。
 * 片付けは host を remove するだけで、枠・メニュー・案内・ダイアログが全部消える。
 */
export class FeedbackUi {
	readonly host = document.createElement('katachi-feedback-root')
	private readonly root = this.host.attachShadow({ mode: 'open' })
	private readonly hover = el('div', 'frame')
	private readonly picked = el('div', 'frame picked')
	private menu: HTMLElement | null = null
	private dialog: HTMLElement | null = null
	private submitButton: HTMLButtonElement | null = null
	private errorBox: HTMLElement | null = null

	constructor() {
		const style = document.createElement('style')
		style.textContent = css
		const banner = el('div', 'banner', '要素をクリック、またはテキストを選んで右クリック → フィードバック / Esc でやめる')
		this.root.append(style, this.hover, this.picked, banner)
		// ダイアログ内のキー入力がページ側（Starlight の検索ショートカットなど）に届かないようにする。
		for (const type of ['keydown', 'keyup', 'keypress']) this.host.addEventListener(type, (e) => e.stopPropagation())
		document.documentElement.append(this.host)
	}

	private place(frame: HTMLElement, rect: DOMRect | null) {
		if (!rect) return void (frame.style.display = 'none')
		Object.assign(frame.style, { display: 'block', left: `${rect.left}px`, top: `${rect.top}px`, width: `${rect.width}px`, height: `${rect.height}px` })
	}
	setHover(rect: DOMRect | null) { this.place(this.hover, rect) }
	setPicked(rect: DOMRect | null) { this.place(this.picked, rect) }

	showMenu(x: number, y: number, onChoose: () => void) {
		this.hideMenu()
		const menu = el('div', 'menu')
		const item = el('button', undefined, 'フィードバック…')
		item.type = 'button'
		item.addEventListener('click', (e) => { e.stopPropagation(); onChoose() })
		menu.append(item)
		// 画面の端で切れないよう、見えている範囲に収める。
		menu.style.left = `${Math.min(x, innerWidth - 180)}px`
		menu.style.top = `${Math.min(y, innerHeight - 50)}px`
		this.root.append(menu)
		this.menu = menu
	}
	hideMenu() { this.menu?.remove(); this.menu = null }
	hasMenu() { return this.menu !== null }

	showDialog(sections: Sections, remembered: Partial<Record<SectionKey, boolean>>, h: DialogHandlers) {
		this.hideMenu()
		const backdrop = el('div', 'backdrop')
		const dialog = el('div', 'dialog')
		const textarea = el('textarea')
		textarea.placeholder = 'コメント'
		const checks = new Map<SectionKey, HTMLInputElement>()
		const items = SECTION_KEYS.filter((k) => sections[k]).map((key) => {
			const details = el('details', 'item')
			const summary = el('summary')
			const box = el('input')
			box.type = 'checkbox'
			box.checked = remembered[key] ?? true
			box.id = `chk-${key}`
			checks.set(key, box)
			const label = el('label', undefined, SECTION_TITLES[key])
			label.htmlFor = box.id
			// summary の中の click で details が開閉しないようにする（チェックだけ切り替えたい）。
			box.addEventListener('click', (e) => e.stopPropagation())
			label.addEventListener('click', (e) => e.stopPropagation())
			summary.append(box, label)
			const pre = el('pre', undefined, sections[key]!.map(([l, v]) => `${l}: ${v}`).join('\n'))
			details.append(summary, pre)
			return details
		})
		const error = el('div', 'error')
		const cancel = el('button', undefined, 'キャンセル')
		const submit = el('button', 'primary', '送信')
		cancel.type = submit.type = 'button'
		submit.disabled = true
		textarea.addEventListener('input', () => { submit.disabled = textarea.value.trim() === '' })
		const doSubmit = () => {
			if (submit.disabled) return
			const checked: Sections = {}
			for (const [k, box] of checks) if (box.checked) checked[k] = sections[k]
			error.textContent = ''
			h.onSubmit(textarea.value, checked)
		}
		submit.addEventListener('click', doSubmit)
		cancel.addEventListener('click', h.onCancel)
		// Cmd/Ctrl+Enter で送信。Esc は mode 側の document リスナーが全体をやめる。
		dialog.addEventListener('keydown', (e) => {
			if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); doSubmit() }
		})
		const actions = el('div', 'actions')
		actions.append(cancel, submit)
		dialog.append(el('h2', undefined, 'フィードバック'), textarea, ...items, error, actions)
		backdrop.append(dialog)
		this.root.append(backdrop)
		this.dialog = backdrop
		this.submitButton = submit
		this.errorBox = error
		textarea.focus()
	}
	hasDialog() { return this.dialog !== null }
	/** 送信中は二重送信を防ぐ。 */
	setBusy(busy: boolean) { if (this.submitButton) this.submitButton.disabled = busy }
	showError(message: string) { if (this.errorBox) this.errorBox.textContent = `保存できませんでした: ${message}`; this.setBusy(false) }

	/** 保存を知らせる小さな通知。UI 全体が消えたあとも残すため、host ごとではなく自前の host で出す。 */
	static toast(message: string, ms = 2000) {
		const host = document.createElement('katachi-feedback-toast')
		const root = host.attachShadow({ mode: 'open' })
		const style = document.createElement('style')
		style.textContent = css
		root.append(style, el('div', 'toast', message))
		document.documentElement.append(host)
		setTimeout(() => host.remove(), ms)
	}

	destroy() {
		this.host.remove()
	}
}
