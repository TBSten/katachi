// Memo の画面の骨組み: shadow host・パネル（右上の popover / 下からのシート）・保存の表示・選んでいる間の帯。
// 片付けは host を remove するだけで、パネル・印・枠が全部消える。
import { el, isNarrow } from '../shared/dom'
import { createHost, mountPanel, type PanelHandle } from '../shared/shell'
import type { SaveStatus } from './controller'
import { css } from './css'

/** 動かした位置を覚える localStorage の鍵（次に開いたときも同じところに出す）。 */
const POS_KEY = 'katachi-memo:position'

export type UiHandlers = {
	onClose(): void
	/** 「要素を選ぶ」ボタン。 */
	onPick(): void
	/** 選んでいる間の帯の「やめる」。 */
	onCancelPick(): void
}

/** 保存の表示の文字。 */
export function statusText(s: SaveStatus): string {
	const two = (n: number) => String(n).padStart(2, '0')
	switch (s.kind) {
		case 'loading': return '読み込み中…'
		case 'idle': return ''
		case 'dirty': return '未保存…'
		case 'saving': return '保存中…'
		case 'saved': return `保存済み ${two(s.at.getHours())}:${two(s.at.getMinutes())}:${two(s.at.getSeconds())}`
		case 'error': return `保存できません: ${s.message}`
	}
}

export class MemoUi {
	private readonly shell = createHost('katachi-memo-root', css)
	readonly host = this.shell.host
	readonly root = this.shell.root
	/** 本文が入るスクロール領域。 */
	readonly body = el('div', 'body')
	/** 「要素を選ぶ」ボタン。 */
	readonly pickButton = el('button', 'pick', '要素を選ぶ')
	/** 箇所のメモの欄を並べる場所。 */
	readonly spotsBox = el('div', 'spots')
	private readonly statusEl = el('span', 'status')
	private readonly hover = el('div', 'frame')
	private readonly pill = el('div', 'pill')
	private readonly panel = el('div')
	private readonly handle: PanelHandle

	constructor(private readonly handlers: UiHandlers) {
		const popover = !isNarrow()
		this.panel.className = `panel ${popover ? 'popover' : 'sheet'}`
		const head = el('header', 'head')
		const title = el('h2', undefined, 'memo')
		this.statusEl.dataset.state = 'loading'
		this.statusEl.setAttribute('role', 'status')
		const close = el('button', 'close', '×')
		close.type = 'button'
		close.title = '閉じる（Esc）'
		close.setAttribute('aria-label', '閉じる')
		close.addEventListener('click', () => handlers.onClose())
		head.append(title, this.statusEl, close)
		this.pickButton.type = 'button'
		this.pickButton.addEventListener('click', () => handlers.onPick())
		this.panel.append(head, this.body)

		const cancel = el('button', 'pill-cancel', 'やめる')
		cancel.type = 'button'
		cancel.addEventListener('click', () => handlers.onCancelPick())
		this.pill.append(el('span', undefined, '要素をクリック／タップ、またはテキストを選ぶ（Esc でやめる）'), cancel)
		this.pill.hidden = true
		this.root.append(this.hover, this.pill)
		this.handle = mountPanel(this.root, this.panel, { popover, backdrop: false, drag: popover ? { handle: head, storageKey: POS_KEY } : undefined })
	}

	get isSheet() { return !this.handle.popover }

	setStatus(s: SaveStatus) {
		this.statusEl.dataset.state = s.kind
		this.statusEl.textContent = statusText(s)
		this.statusEl.title = this.statusEl.textContent
	}

	/** 選んでいる間は、パネルを隠してページを触れるようにし、帯だけ出す。 */
	setPicking(on: boolean) {
		this.handle.outer.hidden = on
		this.pill.hidden = !on
		this.pickButton.disabled = on
		if (!on) this.setHover(null)
	}

	/** シートが選んだ場所を隠さないよう、ms のあいだだけパネルを引っ込める（シートのときだけ。popover は右上に寄っているので隠さない）。 */
	peek(ms: number) {
		if (!this.isSheet) return
		this.handle.outer.hidden = true
		setTimeout(() => { if (this.host.isConnected && this.pill.hidden) this.handle.outer.hidden = false }, ms)
	}

	setHover(rect: DOMRect | null) {
		if (!rect) return void (this.hover.style.display = 'none')
		Object.assign(this.hover.style, { display: 'block', left: `${rect.left}px`, top: `${rect.top}px`, width: `${rect.width}px`, height: `${rect.height}px` })
	}

	destroy() {
		this.handle.close()
		this.host.remove()
	}
}
