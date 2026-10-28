// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import { createNoteEditor } from '../memo/note-editor'
import { emptyNote } from '../memo/model'

const setup = (template = ['読みやすさ']) => {
	const h = { onChange: vi.fn(), onAddField: vi.fn(), onRemoveField: vi.fn() }
	const ed = createNoteEditor(emptyNote(), template, h)
	document.body.append(ed.root)
	return { ed, h }
}
const type = (input: HTMLInputElement | HTMLTextAreaElement, v: string) => { input.value = v; input.dispatchEvent(new Event('input', { bubbles: true })) }

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
		type(ed.root.querySelector('.field input') as HTMLInputElement, '3')
		expect(h.onChange).toHaveBeenLastCalledWith({ values: { 読みやすさ: '3' }, text: '' })
		type(ed.textarea, '本文')
		expect(h.onChange).toHaveBeenLastCalledWith({ values: { 読みやすさ: '3' }, text: '本文' })
	})
	it('+ field を追加 → 名前を入れて Enter で onAddField', () => {
		const { ed, h } = setup()
		;(ed.root.querySelector('.add-field') as HTMLElement).click()
		const input = ed.root.querySelector('.add-input') as HTMLInputElement
		expect(input.dataset.esc).toBe('local')
		input.value = '  密度 '
		input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
		expect(h.onAddField).toHaveBeenCalledWith('密度')
		expect(ed.root.querySelector('.add-field')).not.toBeNull()
	})
	it('追加を Esc でやめると何も足さない', () => {
		const { ed, h } = setup()
		;(ed.root.querySelector('.add-field') as HTMLElement).click()
		ed.root.querySelector('.add-input')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
		expect(h.onAddField).not.toHaveBeenCalled()
		expect(ed.root.querySelector('.add-input')).toBeNull()
	})
	it('× で onRemoveField・ひな形が変わると行を作り直しても入力済みの値は残る', () => {
		const { ed, h } = setup()
		type(ed.root.querySelector('.field input') as HTMLInputElement, '3')
		ed.setTemplate(['読みやすさ', '密度'])
		const inputs = [...ed.root.querySelectorAll<HTMLInputElement>('.field input')]
		expect(inputs.map((i) => i.value)).toEqual(['3', ''])
		;(ed.root.querySelector('.field-drop') as HTMLElement).click()
		expect(h.onRemoveField).toHaveBeenCalledWith('読みやすさ')
	})
})
