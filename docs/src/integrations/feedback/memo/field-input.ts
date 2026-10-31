// field 1 つの入力欄。種類（text / check / select）ごとの部品を作り、値の変更を文字列で返す。
// 値は文字列のまま持つ: check は入っていれば CHECKED、外せば ''（呼び出し側の withValue が値ごと消す）。select は選んだ選択肢、「—」は ''。
// ひな形の種類と合わない値（check の field に "7" など）は消さない。check は外れとして出し、今の値を横に小さく見せる。
// select は「(値)」の選択肢を 1 つ足して選んだ状態にする（別の選択肢を選ぶまで値はそのまま）。
import { el } from '../shared/dom'
import { CHECKED, type FieldDef } from './fields'

/** 入力欄のセル（label の隣に置く要素）を作る。 */
export function createFieldInput(field: FieldDef, value: string, onValue: (value: string) => void): HTMLElement {
	if (field.type === 'check') return checkInput(field.name, value, onValue)
	if (field.type === 'select') return selectInput(field.name, field.options, value, onValue)
	const input = el('input')
	input.type = 'text'
	input.value = value
	input.setAttribute('aria-label', field.name)
	input.addEventListener('input', () => onValue(input.value))
	return input
}

function checkInput(name: string, value: string, onValue: (value: string) => void): HTMLElement {
	const box = el('span', 'field-check')
	const input = el('input')
	input.type = 'checkbox'
	input.checked = value === CHECKED
	input.setAttribute('aria-label', name)
	box.append(input)
	const mismatch = value !== '' && value !== CHECKED
	const raw = el('span', 'field-raw', mismatch ? `（今の値: ${value}）` : undefined)
	raw.hidden = !mismatch
	box.append(raw)
	input.addEventListener('change', () => {
		raw.hidden = true
		onValue(input.checked ? CHECKED : '')
	})
	return box
}

function selectInput(name: string, options: readonly string[], value: string, onValue: (value: string) => void): HTMLElement {
	const select = el('select', 'field-select')
	select.setAttribute('aria-label', name)
	const none = el('option', undefined, '—')
	none.value = ''
	select.append(none)
	for (const o of options) {
		const opt = el('option', undefined, o)
		opt.value = o
		select.append(opt)
	}
	if (value !== '' && !options.includes(value)) {
		const extra = el('option', undefined, `(${value})`)
		extra.value = value
		extra.dataset.extra = ''
		select.append(extra)
	}
	select.value = value
	select.addEventListener('change', () => onValue(select.value))
	return select
}
