// @vitest-environment happy-dom
// モード中は、ページの編集欄にフォーカスが入らない（Tab や mousedown で入ると、クリックの横取りでは止まらず中身を変えられる）。
import { afterEach, describe, expect, it } from 'vitest'
import { guardFocus } from '../shared/focus'
import { createHost } from '../shared/shell'

afterEach(() => {
	document.body.replaceChildren()
	document.querySelectorAll('x-fb, x-memo').forEach((e) => e.remove())
})

/** ページの編集欄と、編集欄でない要素。 */
const page = () => {
	const input = document.createElement('input')
	const textarea = document.createElement('textarea')
	const select = document.createElement('select')
	const editable = document.createElement('div')
	editable.contentEditable = 'true'
	const button = document.createElement('button')
	const withTab = document.createElement('input')
	withTab.setAttribute('tabindex', '3')
	document.body.append(input, textarea, select, editable, button, withTab)
	return { input, textarea, select, editable, button, withTab }
}
/** 自前の UI（shadow root を持つ host）と、その中の textarea。 */
const ownUi = (tag = 'x-fb') => {
	const { host, root } = createHost(tag, '')
	const textarea = document.createElement('textarea')
	root.append(textarea)
	return { host, root, textarea }
}
const focused = (el: HTMLElement) => document.activeElement === el

describe('guardFocus', () => {
	it('モード中は、ページの input・textarea・select・contenteditable にフォーカスが入らない', () => {
		const p = page()
		const { host } = ownUi()
		const ac = new AbortController()
		guardFocus({ host, signal: ac.signal })
		const got = Object.fromEntries((['input', 'textarea', 'select', 'editable', 'withTab'] as const).map((k) => {
			p[k].focus()
			return [k, focused(p[k])]
		}))
		expect(got).toEqual({ input: false, textarea: false, select: false, editable: false, withTab: false })
		ac.abort()
	})

	it('外した欄は Tab の順から抜け（tabindex=-1）、モードを閉じると元の tabindex に戻ってフォーカスできる', () => {
		const p = page()
		const { host } = ownUi()
		const ac = new AbortController()
		guardFocus({ host, signal: ac.signal })
		p.input.focus()
		p.withTab.focus()
		expect([p.input.getAttribute('tabindex'), p.withTab.getAttribute('tabindex')]).toEqual(['-1', '-1'])
		ac.abort()
		expect([p.input.getAttribute('tabindex'), p.withTab.getAttribute('tabindex')]).toEqual([null, '3'])
		p.input.focus()
		expect(focused(p.input)).toBe(true)
	})

	it('編集欄でないボタンにはフォーカスが入る（Tab で先へ進める）', () => {
		const p = page()
		const { host } = ownUi()
		const ac = new AbortController()
		guardFocus({ host, signal: ac.signal })
		p.button.focus()
		expect(focused(p.button)).toBe(true)
		expect(p.button.hasAttribute('tabindex')).toBe(false)
		ac.abort()
	})

	it('自前の UI（Feedback とメモ）の入力欄は使えるまま', () => {
		page()
		const fb = ownUi('x-fb')
		const memo = ownUi('x-memo')
		const ac = new AbortController()
		guardFocus({ host: fb.host, signal: ac.signal })
		fb.textarea.focus()
		expect(fb.root.activeElement).toBe(fb.textarea)
		memo.textarea.focus()
		expect(memo.root.activeElement).toBe(memo.textarea)
		expect(fb.textarea.hasAttribute('tabindex') || memo.textarea.hasAttribute('tabindex')).toBe(false)
		ac.abort()
	})

	it('自前の UI の入力欄からページの編集欄へ移ろうとしたら、自前の入力欄に戻る', () => {
		const p = page()
		const fb = ownUi()
		const ac = new AbortController()
		guardFocus({ host: fb.host, signal: ac.signal })
		fb.textarea.focus()
		p.input.focus()
		expect(focused(p.input)).toBe(false)
		expect(fb.root.activeElement).toBe(fb.textarea)
		ac.abort()
	})

	it('モードに入る前からページの編集欄にあったフォーカスは外す', () => {
		const p = page()
		p.textarea.focus()
		expect(focused(p.textarea)).toBe(true)
		const { host } = ownUi()
		const ac = new AbortController()
		guardFocus({ host, signal: ac.signal })
		expect(focused(p.textarea)).toBe(false)
		ac.abort()
		expect(p.textarea.hasAttribute('tabindex')).toBe(false)
	})

	it('ページの shadow root の中の編集欄にも入らない', () => {
		const wrap = document.createElement('div')
		document.body.append(wrap)
		const inner = document.createElement('input')
		wrap.attachShadow({ mode: 'open' }).append(inner)
		const { host } = ownUi()
		const ac = new AbortController()
		guardFocus({ host, signal: ac.signal })
		inner.focus()
		expect(wrap.shadowRoot!.activeElement).not.toBe(inner)
		ac.abort()
		inner.focus()
		expect(wrap.shadowRoot!.activeElement).toBe(inner)
	})
})
