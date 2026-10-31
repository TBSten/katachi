// メモ 1 件の入力欄: field の行 → 「+ field を追加」（add-field.ts）→ 大きい textarea。ページのメモも箇所のメモも同じ形。
// field の行の入力欄は種類ごとに field-input.ts が作る。
import { el, isCoarse, isNarrow } from '../shared/dom'
import { createAddFieldBox } from './add-field'
import { createFieldInput } from './field-input'
import { visibleFields, type FieldDef } from './fields'
import { withValue, type Note } from './model'

export type NoteEditorHandlers = {
	/** 値や本文が変わった。新しい Note を渡す。 */
	onChange(note: Note): void
	/** ひな形（全ページ共通）に足す。同じ名前があれば種類・選択肢が置き換わる。 */
	onAddField(field: FieldDef): void
	/** ひな形から外す（保存済みの値は残る）。 */
	onRemoveField(name: string): void
}

export type NoteEditor = {
	root: HTMLElement
	textarea: HTMLTextAreaElement
	/** ひな形が変わったので field の行を作り直す（入力中の値は note から入れ直す）。 */
	setTemplate(template: readonly FieldDef[]): void
	/** 画面に付いたあとに、textarea の高さを中身に合わせる。 */
	refit(): void
}

/** textarea の最初の行数。スマホ 3 行・PC 5 行。 */
export const initialRows = () => (isCoarse() || isNarrow() ? 3 : 5)

export function createNoteEditor(initial: Note, template: readonly FieldDef[], h: NoteEditorHandlers): NoteEditor {
	let note = initial
	const root = el('div', 'note')
	const fieldsBox = el('div', 'fields')
	const addBox = createAddFieldBox((field) => h.onAddField(field))
	// TODO: maxLength = MEMO_LIMITS.textChars が無く、100,000 字を超えて保存でき、読み直すと切れる（review L10）。
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

	// 値の変更は種類を問わず onChange に渡す（保存のまとめ方は controller の自動保存のまま）。
	function renderFields(fields: readonly FieldDef[]) {
		fieldsBox.replaceChildren()
		for (const [field, value] of visibleFields(fields, note)) {
			const name = field.name
			const row = el('div', 'field')
			row.dataset.field = name
			row.dataset.type = field.type
			const label = el('label', 'field-label', name)
			const input = createFieldInput(field, value, (v) => {
				note = withValue(note, name, v)
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

	renderFields(template)
	root.append(fieldsBox, addBox, textarea)
	return {
		root,
		textarea,
		setTemplate: (t) => renderFields(t),
		refit,
	}
}
