// フィードバックの入力欄（コメント・載せる情報・送信）を組み立てる。
// 下から出るシートと選択のそばの popover で中身は同じ。popover は縦を詰めるため、載せる情報を <details> に畳む。
import { SECTION_KEYS, SECTION_TITLES, type Section, type SectionKey } from '../core/protocol'
import { el } from '../shared/dom'
import { onKeyInside } from '../shared/keys'
import { COPY_NOTE } from './copy'
import type { Draft } from './drafts'

export type Sections = Partial<Record<SectionKey, Section>>

export type DialogHandlers = {
	/** チェックされた節だけを渡す。 */
	onSubmit: (comment: string, checked: Sections, target?: 'page' | 'site') => void
	onCancel: () => void
	/** 「Markdown をコピー」。保存と同じ文をクリップボードへ。成功したか返す。 */
	onCopy: (comment: string, checked: Sections, target?: 'page' | 'site') => Promise<boolean>
	/** コメントかチェックが変わったとき（下書きの保存）。 */
	onDraft?: (draft: Draft) => void
	/** 「親を選ぶ」。選んだものを親の要素へ広げる。 */
	onParent?: () => void
}

export type DialogExtra = {
	/** 下書き（書きかけのコメントとチェック）。 */
	initial?: Draft | null
	/** 「親を選ぶ」を出せるか（まだ親がある）。 */
	canParent?: boolean
}

export type DialogParts = {
	root: HTMLElement
	textarea: HTMLTextAreaElement
	submit: HTMLButtonElement
	error: HTMLElement
	/** 上端の見出し。popover ではここをつかんで動かす。 */
	head: HTMLElement
	/** 今のコメントとチェックの状態（「親を選ぶ」で開き直すときに引き継ぐ）。 */
	draft(): Draft
}

export function buildDialog(
	sections: Sections,
	remembered: Partial<Record<SectionKey, boolean>>,
	h: DialogHandlers,
	popover: boolean,
	/** true なら一番上に「対象」（このページ / サイト全体）の選択を置く。何も選ばずに開いたとき。 */
	withTarget = false,
	extra: DialogExtra = {},
): DialogParts {
	const root = el('div', popover ? 'dialog popover' : 'dialog')
	const textarea = el('textarea')
	textarea.placeholder = 'コメント'
	const grow = () => {
		textarea.style.height = 'auto'
		textarea.style.height = `${textarea.scrollHeight + 2}px`
	}
	if (popover) {
		textarea.rows = 3
		// 書くほど伸びる（上限は CSS の max-height）。
		textarea.addEventListener('input', grow)
	}
	if (extra.initial?.comment) {
		textarea.value = extra.initial.comment
		if (popover) queueMicrotask(grow)
	}
	// 見出し: popover ではここをつかんで動かす。「親を選ぶ」は押しても動かさない（ボタンの上のドラッグは移動にしない）。
	const head = el('div', 'head')
	head.append(el('h2', undefined, 'フィードバック'))
	if (extra.canParent) {
		const parent = el('button', 'parent', '親を選ぶ')
		parent.type = 'button'
		parent.title = '選んだものを、ひとつ外側の要素に広げる（Alt+↑）'
		parent.addEventListener('click', () => h.onParent?.())
		head.append(parent)
	}
	// 対象の選択（既定は「このページ」）。
	const targetBox = el('fieldset', 'target')
	const targetInputs: Record<'page' | 'site', HTMLInputElement> = { page: el('input'), site: el('input') }
	for (const [value, text] of [['page', 'このページ'], ['site', 'サイト全体']] as const) {
		const input = targetInputs[value]
		input.type = 'radio'
		input.name = 'fb-target'
		input.value = value
		input.checked = value === (extra.initial?.target ?? 'page')
		const label = el('label')
		label.append(input, text)
		targetBox.append(label)
	}
	const checks = new Map<SectionKey, HTMLInputElement>()
	const draft = (): Draft => ({
		comment: textarea.value,
		checks: Object.fromEntries([...checks].map(([k, b]) => [k, b.checked])),
		...(withTarget ? { target: targetInputs.site.checked ? 'site' as const : 'page' as const } : {}),
	})
	const emitDraft = () => h.onDraft?.(draft())
	targetBox.addEventListener('change', () => emitDraft())
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
		box.checked = extra.initial?.checks[key] ?? remembered[key] ?? true
		box.id = `chk-${key}`
		checks.set(key, box)
		const label = el('label', undefined, SECTION_TITLES[key])
		label.htmlFor = box.id
		// summary の中の click で details が開閉しないようにする（チェックだけ切り替えたい）。
		box.addEventListener('click', (e) => e.stopPropagation())
		box.addEventListener('change', () => { updateCount(); emitDraft() })
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
	const copy = el('button', 'copy', 'Markdown をコピー')
	copy.type = 'button'
	submit.disabled = copy.disabled = textarea.value.trim() === ''
	textarea.addEventListener('input', () => { submit.disabled = copy.disabled = textarea.value.trim() === ''; emitDraft() })
	const checkedSections = () => {
		const checked: Sections = {}
		for (const [k, box] of checks) if (box.checked) checked[k] = sections[k]
		return checked
	}
	let copyTimer: ReturnType<typeof setTimeout> | undefined
	copy.addEventListener('click', async () => {
		if (copy.disabled) return
		const ok = await h.onCopy(textarea.value, checkedSections(), withTarget ? (targetInputs.site.checked ? 'site' : 'page') : undefined)
		copy.textContent = ok ? 'コピーしました' : 'コピーできませんでした'
		clearTimeout(copyTimer)
		copyTimer = setTimeout(() => { copy.textContent = 'Markdown をコピー' }, 2000)
	})
	const copyNote = el('div', 'note', COPY_NOTE)
	const doSubmit = () => {
		if (submit.disabled) return
		error.textContent = ''
		h.onSubmit(textarea.value, checkedSections(), withTarget ? (targetInputs.site.checked ? 'site' : 'page') : undefined)
	}
	submit.addEventListener('click', doSubmit)
	cancel.addEventListener('click', h.onCancel)
	// Cmd/Ctrl+Enter で送信。Esc は shared/keys.ts の window の capture（quick は quick.ts）が受けて全体をやめる。
	// モード中は guardKeys が window の capture でキーを止めるので、addEventListener ではなく onKeyInside で付ける。
	onKeyInside(root, 'keydown', (e) => {
		if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); doSubmit() }
		// 文字を打つ最中の ↑ はカーソル移動に使うので、Alt を押したときだけ親を選ぶ。
		if (e.key === 'ArrowUp' && e.altKey && extra.canParent) { e.preventDefault(); h.onParent?.() }
	})
	const actions = el('div', 'actions')
	actions.append(copy, cancel, submit)
	if (popover) {
		const info = el('details', 'info')
		const infoSummary = el('summary')
		infoSummary.append(summaryText)
		info.append(infoSummary, ...items)
		root.append(head, ...(withTarget ? [targetBox] : []), textarea, info, error, actions, copyNote)
	} else {
		root.append(head, ...(withTarget ? [targetBox] : []), textarea, ...items, error, actions, copyNote)
	}
	return { root, textarea, submit, error, head, draft }
}
