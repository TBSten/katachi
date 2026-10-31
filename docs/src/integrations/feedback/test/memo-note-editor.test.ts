// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import { createNoteEditor } from '../memo/note-editor'
import { checkOptions } from '../memo/add-field'
import type { FieldDef } from '../memo/fields'
import { emptyNote, type Note } from '../memo/model'

const text = (name: string): FieldDef => ({ name, type: 'text' })
const setup = (template: FieldDef[] = [text('読みやすさ')], note: Note = emptyNote()) => {
	const h = { onChange: vi.fn(), onAddField: vi.fn(), onRemoveField: vi.fn() }
	const ed = createNoteEditor(note, template, h)
	document.body.replaceChildren(ed.root)
	return { ed, h }
}
const type = (input: HTMLInputElement | HTMLTextAreaElement, v: string) => { input.value = v; input.dispatchEvent(new Event('input', { bubbles: true })) }
const choose = (select: HTMLSelectElement, v: string) => { select.value = v; select.dispatchEvent(new Event('change', { bubbles: true })) }
const key = (target: Element, k: string, init: KeyboardEventInit = {}) => target.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, ...init }))
const q = <T extends Element>(root: Element, sel: string) => root.querySelector(sel) as T
const lastNote = (h: { onChange: ReturnType<typeof vi.fn> }) => h.onChange.mock.lastCall?.[0] as Note

describe('note-editor', () => {
	it('並びは field の行 → + field を追加 → textarea', () => {
		const { ed } = setup()
		const kids = [...ed.root.children].map((c) => c.className)
		expect(kids).toEqual(['fields', 'add-box', 'note-text'])
		expect(ed.root.querySelector('.field-label')?.textContent).toBe('読みやすさ')
		expect(ed.root.querySelector('.add-field')?.textContent).toBe('+ field を追加')
	})
	it('textarea の最初の行数は PC で 5', () => {
		expect(setup().ed.textarea.getAttribute('rows')).toBe('5')
	})
	it('field の値と本文の変更が onChange に届く', () => {
		const { ed, h } = setup()
		type(q(ed.root, '.field input'), '3')
		expect(h.onChange).toHaveBeenLastCalledWith({ values: { 読みやすさ: '3' }, text: '' })
		type(ed.textarea, '本文')
		expect(h.onChange).toHaveBeenLastCalledWith({ values: { 読みやすさ: '3' }, text: '本文' })
	})
	it('× で onRemoveField・ひな形が変わると行を作り直しても入力済みの値は残る', () => {
		const { ed, h } = setup()
		type(q(ed.root, '.field input'), '3')
		ed.setTemplate([text('読みやすさ'), text('密度')])
		const inputs = [...ed.root.querySelectorAll<HTMLInputElement>('.field input')]
		expect(inputs.map((i) => i.value)).toEqual(['3', ''])
		q<HTMLElement>(ed.root, '.field-drop').click()
		expect(h.onRemoveField).toHaveBeenCalledWith('読みやすさ')
	})
})

describe('check の field', () => {
	it('押す → "true" → もう一度押す → 値が消える → また押す → "true"', () => {
		const { ed, h } = setup([{ name: '確認', type: 'check' }])
		const box = q<HTMLInputElement>(ed.root, '.field input[type=checkbox]')
		expect(box.checked).toBe(false)
		expect(box.getAttribute('aria-label')).toBe('確認')
		box.click()
		expect(lastNote(h).values).toEqual({ 確認: 'true' })
		box.click()
		expect(lastNote(h).values).toEqual({})
		box.click()
		expect(lastNote(h).values).toEqual({ 確認: 'true' })
		expect(h.onChange).toHaveBeenCalledTimes(3)
	})
	it('保存済みの "true" は入った状態で出る。ほかの値と一緒に変えても他の値は残る', () => {
		const { ed, h } = setup([text('a'), { name: '確認', type: 'check' }], { values: { a: 'x', 確認: 'true' }, text: '' })
		const box = q<HTMLInputElement>(ed.root, '.field input[type=checkbox]')
		expect(box.checked).toBe(true)
		box.click()
		expect(lastNote(h).values).toEqual({ a: 'x' })
	})
	it('型の合わない値（"7"）は外れとして出し、値は消さずに横に見せる。押すと "true" に変わる', () => {
		const { ed, h } = setup([{ name: '確認', type: 'check' }], { values: { 確認: '7' }, text: '' })
		const box = q<HTMLInputElement>(ed.root, '.field input[type=checkbox]')
		expect(box.checked).toBe(false)
		const raw = q<HTMLElement>(ed.root, '.field-raw')
		expect(raw.hidden).toBe(false)
		expect(raw.textContent).toContain('7')
		expect(h.onChange).not.toHaveBeenCalled()
		box.click()
		expect(lastNote(h).values).toEqual({ 確認: 'true' })
		expect(raw.hidden).toBe(true)
	})
})

describe('select の field', () => {
	const sel: FieldDef = { name: '評価', type: 'select', options: ['良い', '普通', '悪い'] }
	it('先頭は空の「—」。選ぶ → 値、別のを選ぶ → 置き換わる、「—」に戻す → 値が消える', () => {
		const { ed, h } = setup([sel])
		const s = q<HTMLSelectElement>(ed.root, '.field select')
		expect([...s.options].map((o) => o.textContent)).toEqual(['—', '良い', '普通', '悪い'])
		expect(s.value).toBe('')
		choose(s, '普通')
		expect(lastNote(h).values).toEqual({ 評価: '普通' })
		choose(s, '悪い')
		expect(lastNote(h).values).toEqual({ 評価: '悪い' })
		choose(s, '')
		expect(lastNote(h).values).toEqual({})
	})
	it('保存済みの値が選ばれた状態で出る', () => {
		const { ed } = setup([sel], { values: { 評価: '良い' }, text: '' })
		expect(q<HTMLSelectElement>(ed.root, '.field select').value).toBe('良い')
	})
	it('選択肢に無い値は「(値)」を 1 つ足して選んだ状態にし、値は消さない。別のを選ぶと置き換わる', () => {
		const { ed, h } = setup([sel], { values: { 評価: '7' }, text: '' })
		const s = q<HTMLSelectElement>(ed.root, '.field select')
		expect(s.value).toBe('7')
		expect([...s.options].map((o) => o.textContent)).toEqual(['—', '良い', '普通', '悪い', '(7)'])
		expect(h.onChange).not.toHaveBeenCalled()
		choose(s, '良い')
		expect(lastNote(h).values).toEqual({ 評価: '良い' })
	})
	it('ひな形の種類が変わると入力欄が作り直され、値は名前で引き継ぐ', () => {
		const { ed } = setup([text('評価')])
		type(q(ed.root, '.field input'), '良い')
		ed.setTemplate([sel])
		expect(q<HTMLSelectElement>(ed.root, '.field select').value).toBe('良い')
		expect(q<HTMLElement>(ed.root, '.field').dataset.type).toBe('select')
	})
})

describe('+ field を追加', () => {
	const open = (root: HTMLElement) => {
		q<HTMLElement>(root, '.add-field').click()
		return {
			name: q<HTMLInputElement>(root, '.add-input'),
			kind: q<HTMLSelectElement>(root, '.add-type'),
			options: q<HTMLTextAreaElement>(root, '.add-options'),
			error: q<HTMLElement>(root, '.add-error'),
		}
	}
	it('名前を入れて Enter でテキストの field（種類の既定はテキスト）', () => {
		const { ed, h } = setup()
		const f = open(ed.root)
		expect(f.name.dataset.esc).toBe('local')
		expect(f.kind.value).toBe('text')
		expect([...f.kind.options].map((o) => o.textContent)).toEqual(['テキスト', 'チェック', '選択肢'])
		expect(f.options.hidden).toBe(true)
		f.name.value = '  密度 '
		key(f.name, 'Enter')
		expect(h.onAddField).toHaveBeenCalledWith({ name: '密度', type: 'text' })
		expect(ed.root.querySelector('.add-field')).not.toBeNull()
		expect(ed.root.querySelector('.add-input')).toBeNull()
	})
	it('種類にチェックを選んで Enter でチェックの field', () => {
		const { ed, h } = setup()
		const f = open(ed.root)
		f.name.value = '確認'
		choose(f.kind, 'check')
		expect(f.options.hidden).toBe(true)
		key(f.kind, 'Enter')
		expect(h.onAddField).toHaveBeenCalledWith({ name: '確認', type: 'check' })
	})
	it('選択肢を選ぶと選択肢の入力が出る → カンマと改行で入れて Enter で選択肢の field', () => {
		const { ed, h } = setup()
		const f = open(ed.root)
		f.name.value = '評価'
		choose(f.kind, 'select')
		expect(f.options.hidden).toBe(false)
		expect(f.options.dataset.esc).toBe('local')
		f.options.value = '良い, 普通\n悪い，良い'
		key(f.options, 'Enter')
		expect(h.onAddField).toHaveBeenCalledWith({ name: '評価', type: 'select', options: ['良い', '普通', '悪い'] })
	})
	it('選択肢の入力の中の Shift+Enter は足さない（改行に使う）', () => {
		const { ed, h } = setup()
		const f = open(ed.root)
		f.name.value = '評価'
		choose(f.kind, 'select')
		f.options.value = '良い'
		key(f.options, 'Enter', { shiftKey: true })
		expect(h.onAddField).not.toHaveBeenCalled()
		q<HTMLElement>(ed.root, '.add-ok').click()
		expect(h.onAddField).toHaveBeenCalledWith({ name: '評価', type: 'select', options: ['良い'] })
	})
	it('選択肢が 0 個なら足さず、理由を出して入力に残る。入れ直せば足せる', () => {
		const { ed, h } = setup()
		const f = open(ed.root)
		f.name.value = '評価'
		choose(f.kind, 'select')
		f.options.value = ' , \n '
		key(f.name, 'Enter')
		expect(h.onAddField).not.toHaveBeenCalled()
		expect(f.error.hidden).toBe(false)
		expect(f.error.textContent).toContain('1 つ以上')
		expect(ed.root.querySelector('.add-input')).not.toBeNull()
		f.options.value = 'a'
		key(f.options, 'Enter')
		expect(h.onAddField).toHaveBeenCalledWith({ name: '評価', type: 'select', options: ['a'] })
	})
	it('選択肢を選んだあとテキストに戻すと、選択肢の入力は隠れてテキストで足す', () => {
		const { ed, h } = setup()
		const f = open(ed.root)
		f.name.value = 'x'
		choose(f.kind, 'select')
		choose(f.kind, 'text')
		expect(f.options.hidden).toBe(true)
		key(f.name, 'Enter')
		expect(h.onAddField).toHaveBeenCalledWith({ name: 'x', type: 'text' })
	})
	it.each(['.add-input', '.add-type', '.add-options'])('%s で Esc を押すとやめて何も足さない', (sel) => {
		const { ed, h } = setup()
		const f = open(ed.root)
		f.name.value = 'x'
		choose(f.kind, 'select')
		f.options.value = 'a'
		key(q(ed.root, sel), 'Escape')
		expect(h.onAddField).not.toHaveBeenCalled()
		expect(ed.root.querySelector('.add-input')).toBeNull()
		expect(ed.root.querySelector('.add-field')).not.toBeNull()
	})
	it('「やめる」でもやめる。名前が空の Enter は何も足さずに閉じる', () => {
		const { ed, h } = setup()
		open(ed.root)
		q<HTMLElement>(ed.root, '.add-cancel').click()
		expect(ed.root.querySelector('.add-input')).toBeNull()
		const f = open(ed.root)
		key(f.name, 'Enter')
		expect(h.onAddField).not.toHaveBeenCalled()
		expect(ed.root.querySelector('.add-input')).toBeNull()
	})
	it('IME の変換中の Enter では足さない', () => {
		const { ed, h } = setup()
		const f = open(ed.root)
		f.name.value = 'あ'
		key(f.name, 'Enter', { isComposing: true })
		expect(h.onAddField).not.toHaveBeenCalled()
	})
	it('3 種類を続けて足す操作の列', () => {
		const { ed, h } = setup()
		const steps: Array<[string, string, string]> = [['t', 'text', ''], ['c', 'check', ''], ['s', 'select', 'x,y']]
		for (const [name, kind, opts] of steps) {
			const f = open(ed.root)
			f.name.value = name
			choose(f.kind, kind)
			if (opts) f.options.value = opts
			q<HTMLElement>(ed.root, '.add-ok').click()
		}
		expect(h.onAddField.mock.calls.map((c) => c[0])).toEqual([{ name: 't', type: 'text' }, { name: 'c', type: 'check' }, { name: 's', type: 'select', options: ['x', 'y'] }])
	})
})

describe('checkOptions', () => {
	it('0 個・21 個・41 文字は理由つきで断る', () => {
		expect(checkOptions('')).toMatchObject({ ok: false })
		expect(checkOptions(Array.from({ length: 21 }, (_, i) => `o${i}`).join(','))).toMatchObject({ ok: false, reason: expect.stringContaining('20') })
		expect(checkOptions('x'.repeat(41))).toMatchObject({ ok: false, reason: expect.stringContaining('40') })
		expect(checkOptions(`${'x'.repeat(40)},a,a`)).toEqual({ ok: true, options: ['x'.repeat(40), 'a'] })
	})
})
