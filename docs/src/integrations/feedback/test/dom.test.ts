// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { el } from '../shared/dom'

describe('shared/dom（happy-dom が使えることの確認）', () => {
	it('el はクラスとテキストを付けた要素を作る', () => {
		const e = el('button', 'a b', 'ok')
		expect(e.tagName).toBe('BUTTON')
		expect(e.className).toBe('a b')
		expect(e.textContent).toBe('ok')
	})
})
