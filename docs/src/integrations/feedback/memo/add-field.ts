// 「+ field を追加」: 押すと 名前の入力・種類（テキスト / チェック / 選択肢）・追加・やめる に変わる。
// 種類が「選択肢」のときだけ、選択肢の入力（カンマか改行で区切る）が出る。Enter で足す・Esc でやめる。
// 選択肢の入力の中では Shift+Enter で改行する。選択肢が 0 個・多すぎる・長すぎるときは足さず、理由を出して入力に戻す。
import { el } from '../shared/dom'
import { onKeyInside } from '../shared/keys'
import { normalizeFieldName, sanitizeOptions, splitOptions, type FieldDef, type FieldType } from './fields'
import { MEMO_LIMITS } from './model'

const TYPE_LABELS: Record<FieldType, string> = { text: 'テキスト', check: 'チェック', select: '選択肢' }

/** 選択肢の文字を確かめる。使えれば選択肢の並び、使えなければ理由。 */
export function checkOptions(text: string): { ok: true; options: string[] } | { ok: false; reason: string } {
	const raw = splitOptions(text)
	if (raw.length === 0) return { ok: false, reason: '選択肢を 1 つ以上入れてください' }
	if (raw.length > MEMO_LIMITS.fieldOptions) return { ok: false, reason: `選択肢は ${MEMO_LIMITS.fieldOptions} 個までです` }
	if (raw.some((o) => o.length > MEMO_LIMITS.fieldOptionChars)) return { ok: false, reason: `選択肢は 1 つ ${MEMO_LIMITS.fieldOptionChars} 文字までです` }
	return { ok: true, options: sanitizeOptions(raw) }
}

/** 「+ field を追加」の置き場を作る。onAdd には足す field を渡す。 */
export function createAddFieldBox(onAdd: (field: FieldDef) => void): HTMLElement {
	const box = el('div', 'add-box')
	const addButton = el('button', 'add-field', '+ field を追加')
	addButton.type = 'button'
	const showAddButton = () => box.replaceChildren(addButton)

	addButton.addEventListener('click', () => {
		const form = el('div', 'add-form')
		const name = el('input', 'add-input')
		name.type = 'text'
		name.placeholder = 'field の名前'
		const type = el('select', 'add-type')
		type.setAttribute('aria-label', 'field の種類')
		for (const t of Object.keys(TYPE_LABELS) as FieldType[]) {
			const o = el('option', undefined, TYPE_LABELS[t])
			o.value = t
			type.append(o)
		}
		const options = el('textarea', 'add-options')
		options.rows = 2
		options.placeholder = '選択肢（カンマか改行で区切る）'
		options.setAttribute('aria-label', '選択肢')
		options.hidden = true
		const error = el('div', 'add-error')
		error.setAttribute('role', 'alert')
		error.hidden = true
		const ok = el('button', 'add-ok', '追加')
		ok.type = 'button'
		const cancel = el('button', 'add-cancel', 'やめる')
		cancel.type = 'button'

		const fail = (reason: string, focus: HTMLElement) => {
			error.textContent = reason
			error.hidden = false
			focus.focus()
		}
		const commit = () => {
			const n = normalizeFieldName(name.value)
			if (!n) return showAddButton()
			const t = type.value as FieldType
			if (t !== 'select') {
				onAdd({ name: n, type: t })
				return showAddButton()
			}
			const checked = checkOptions(options.value)
			if (!checked.ok) return fail(checked.reason, options)
			onAdd({ name: n, type: 'select', options: checked.options })
			showAddButton()
		}
		type.addEventListener('change', () => {
			options.hidden = type.value !== 'select'
			error.hidden = true
			if (!options.hidden) options.focus()
		})
		for (const input of [name, type, options] as HTMLElement[]) {
			input.dataset.esc = 'local' // Esc は追加のやめるに使う（パネル全体は閉じない）
			onKeyInside(input, 'keydown', (e) => {
				if (e.isComposing) return
				if (e.key === 'Escape') { e.preventDefault(); showAddButton() }
				if (e.key === 'Enter' && !(input === options && e.shiftKey)) { e.preventDefault(); commit() }
			})
		}
		ok.addEventListener('click', commit)
		cancel.addEventListener('click', showAddButton)
		const row = el('div', 'add-row')
		row.append(name, type, ok, cancel)
		form.append(row, options, error)
		box.replaceChildren(form)
		name.focus()
	})
	showAddButton()
	return box
}
