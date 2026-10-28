// メモ 1 件の入力欄: field の行 → 「+ field を追加」→ 大きい textarea。ページのメモも箇所のメモも同じ形。
import { el, isCoarse, isNarrow } from '../shared/dom'
import { normalizeFieldName, visibleFields, withValue, type Note } from './model'

export type NoteEditorHandlers = {
	/** 値や本文が変わった。新しい Note を渡す。 */
	onChange(note: Note): void
	/** ひな形（全ページ共通）に足す。 */
	onAddField(name: string): void
	/** ひな形から外す（保存済みの値は残る）。 */
	onRemoveField(name: string): void
}

export type NoteEditor = {
	root: HTMLElement
	textarea: HTMLTextAreaElement
	/** ひな形が変わったので field の行を作り直す（入力中の値は note から入れ直す）。 */
	setTemplate(template: readonly string[]): void
	/** 画面に付いたあとに、textarea の高さを中身に合わせる。 */
	refit(): void
}

/** textarea の最初の行数。スマホ 3 行・PC 5 行。 */
export const initialRows = () => (isCoarse() || isNarrow() ? 3 : 5)

export function createNoteEditor(initial: Note, template: readonly string[], h: NoteEditorHandlers): NoteEditor {
	let note = initial
	const root = el('div', 'note')
	const fieldsBox = el('div', 'fields')
	const addBox = el('div', 'add-box')
	const textarea = el('textarea', 'note-text')
	textarea.rows = initialRows()
	textarea.placeholder = 'メモ'
	textarea.value = note.text

	const refit = () => {
		textarea.style.height = 'auto'
		textarea.style.height = `${textarea.scrollHeight + 2}px`
	}
	textarea.addEventListener('input', () => {
		note = { ...note, text: textarea.value }
		refit()
		h.onChange(note)
	})

	function renderFields(names: readonly string[]) {
		fieldsBox.replaceChildren()
		for (const [name, value] of visibleFields(names, note)) {
			const row = el('div', 'field')
			row.dataset.field = name
			const label = el('label', 'field-label', name)
			const input = el('input')
			input.type = 'text'
			input.value = value
			input.setAttribute('aria-label', name)
			input.addEventListener('input', () => {
				note = withValue(note, name, input.value)
				h.onChange(note)
			})
			const drop = el('button', 'field-drop', '×')
			drop.type = 'button'
			drop.title = `「${name}」をひな形から外す（保存済みの値は残る）`
			drop.setAttribute('aria-label', `${name} をひな形から外す`)
			drop.addEventListener('click', () => h.onRemoveField(name))
			row.append(label, input, drop)
			fieldsBox.append(row)
		}
	}

	// 「+ field を追加」: 押すと名前の入力に変わる。Enter で足す・Esc でやめる。
	const addButton = el('button', 'add-field', '+ field を追加')
	addButton.type = 'button'
	const showAddButton = () => addBox.replaceChildren(addButton)
	addButton.addEventListener('click', () => {
		const input = el('input', 'add-input')
		input.type = 'text'
		input.placeholder = 'field の名前'
		input.dataset.esc = 'local' // Esc は入力のやめるに使う（パネル全体は閉じない）
		const ok = el('button', 'add-ok', '追加')
		ok.type = 'button'
		const cancel = el('button', 'add-cancel', 'やめる')
		cancel.type = 'button'
		const commit = () => {
			const n = normalizeFieldName(input.value)
			if (n) h.onAddField(n)
			showAddButton()
		}
		input.addEventListener('keydown', (e) => {
			if (e.isComposing) return
			if (e.key === 'Enter') { e.preventDefault(); commit() }
			if (e.key === 'Escape') { e.preventDefault(); showAddButton() }
		})
		ok.addEventListener('click', commit)
		cancel.addEventListener('click', showAddButton)
		addBox.replaceChildren(input, ok, cancel)
		input.focus()
	})
	showAddButton()

	renderFields(template)
	root.append(fieldsBox, addBox, textarea)
	return {
		root,
		textarea,
		setTemplate: (t) => renderFields(t),
		refit,
	}
}
