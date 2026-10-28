// フィードバックの入力欄（コメント・載せる情報・送信）を組み立てる。
// 下から出るシートと選択のそばの popover で中身は同じ。popover は縦を詰めるため、載せる情報を <details> に畳む。
import { SECTION_KEYS, SECTION_TITLES, type Section, type SectionKey } from './protocol'
import { el } from './dom'

export type Sections = Partial<Record<SectionKey, Section>>

export type DialogHandlers = {
	/** チェックされた節だけを渡す。 */
	onSubmit: (comment: string, checked: Sections, target?: 'page' | 'site') => void
	onCancel: () => void
}

export type DialogParts = {
	root: HTMLElement
	textarea: HTMLTextAreaElement
	submit: HTMLButtonElement
	error: HTMLElement
}

export function buildDialog(
	sections: Sections,
	remembered: Partial<Record<SectionKey, boolean>>,
	h: DialogHandlers,
	popover: boolean,
	/** true なら一番上に「対象」（このページ / サイト全体）の選択を置く。何も選ばずに開いたとき。 */
	withTarget = false,
): DialogParts {
	const root = el('div', popover ? 'dialog popover' : 'dialog')
	const textarea = el('textarea')
	textarea.placeholder = 'コメント'
	if (popover) {
		textarea.rows = 3
		// 書くほど伸びる（上限は CSS の max-height）。
		textarea.addEventListener('input', () => {
			textarea.style.height = 'auto'
			textarea.style.height = `${textarea.scrollHeight + 2}px`
		})
	}
	// 対象の選択（既定は「このページ」）。
	const targetBox = el('fieldset', 'target')
	const targetInputs: Record<'page' | 'site', HTMLInputElement> = { page: el('input'), site: el('input') }
	for (const [value, text] of [['page', 'このページ'], ['site', 'サイト全体']] as const) {
		const input = targetInputs[value]
		input.type = 'radio'
		input.name = 'fb-target'
		input.value = value
		input.checked = value === 'page'
		const label = el('label')
		label.append(input, text)
		targetBox.append(label)
	}
	const checks = new Map<SectionKey, HTMLInputElement>()
	const summaryText = el('span')
	const updateCount = () => {
		const on = [...checks.values()].filter((b) => b.checked).length
		summaryText.textContent = `載せる情報 (${on}/${checks.size})`
	}
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
		box.addEventListener('change', updateCount)
		label.addEventListener('click', (e) => e.stopPropagation())
		summary.append(box, label)
		const pre = el('pre', undefined, sections[key]!.map(([l, v]) => `${l}: ${v}`).join('\n'))
		details.append(summary, pre)
		return details
	})
	updateCount()
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
		h.onSubmit(textarea.value, checked, withTarget ? (targetInputs.site.checked ? 'site' : 'page') : undefined)
	}
	submit.addEventListener('click', doSubmit)
	cancel.addEventListener('click', h.onCancel)
	// Cmd/Ctrl+Enter で送信。Esc はモード側の document リスナーが全体をやめる。
	root.addEventListener('keydown', (e) => {
		if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); doSubmit() }
	})
	const actions = el('div', 'actions')
	actions.append(cancel, submit)
	if (popover) {
		const info = el('details', 'info')
		const infoSummary = el('summary')
		infoSummary.append(summaryText)
		info.append(infoSummary, ...items)
		root.append(...(withTarget ? [targetBox] : []), textarea, info, error, actions)
	} else {
		root.append(el('h2', undefined, 'フィードバック'), ...(withTarget ? [targetBox] : []), textarea, ...items, error, actions)
	}
	return { root, textarea, submit, error }
}
