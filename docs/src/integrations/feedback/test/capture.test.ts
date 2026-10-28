// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { blobToBase64, frameRects, shouldSkip, stripInvalidAttributes } from '../feedback/capture'
import { parentOf } from '../feedback/flow'

describe('capture の部品', () => {
	it('自分の UI と Astro の toolbar は絵にしない。ほかは残す', () => {
		for (const tag of ['katachi-feedback-root', 'katachi-memo-root', 'katachi-feedback-toast', 'astro-dev-toolbar']) expect(shouldSkip(document.createElement(tag))).toBe(true)
		expect(shouldSkip(document.createElement('p'))).toBe(false)
		expect(shouldSkip(document.createTextNode('x'))).toBe(false)
	})
	it('display: none の HTML 要素は飛ばすが、SVG の中は飛ばさない', () => {
		const hidden = document.createElement('div')
		hidden.style.display = 'none'
		document.body.append(hidden)
		expect(shouldSkip(hidden)).toBe(true)
		const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs')
		expect(shouldSkip(defs)).toBe(false)
	})
	it('XML として不正な属性名（:xmlns）だけ外す', () => {
		const div = document.createElement('div')
		div.innerHTML = '<svg :xmlns="http://www.w3.org/2000/svg" xmlns:xlink="x" viewBox="0 0 1 1" data-a-b="1"></svg>'
		const svg = div.firstElementChild!
		expect(svg.hasAttribute(':xmlns')).toBe(true)
		stripInvalidAttributes(svg)
		expect(svg.hasAttribute(':xmlns')).toBe(false)
		expect([...svg.attributes].map((a) => a.name).sort()).toEqual(['data-a-b', 'viewBox', 'xmlns:xlink'])
	})
	it('base64 にできる', async () => {
		expect(await blobToBase64(new Blob([new Uint8Array([1, 2, 3])]))).toBe('AQID')
		const big = new Uint8Array(100_000).fill(65)
		expect(atob(await blobToBase64(new Blob([big]))).length).toBe(100_000)
	})
	it('ページ全体には枠が無い。要素には 1 つ', () => {
		expect(frameRects({ kind: 'page' })).toEqual([])
		expect(frameRects({ kind: 'element', el: document.createElement('p') })).toHaveLength(1)
	})
})

describe('parentOf（親を選ぶ）', () => {
	it('要素は親へ。body・html までは広げない', () => {
		document.body.innerHTML = '<main><section><p id="a">x</p></section></main>'
		const p = document.querySelector('#a')!
		const section = parentOf({ kind: 'element', el: p })!
		expect(section.tagName).toBe('SECTION')
		expect(parentOf({ kind: 'element', el: section })!.tagName).toBe('MAIN')
		expect(parentOf({ kind: 'element', el: document.querySelector('main')! })).toBeNull()
		expect(parentOf({ kind: 'page' })).toBeNull()
	})
	it('テキストは、それを含む要素へ', () => {
		document.body.innerHTML = '<main><p id="a">本文</p></main>'
		const text = document.querySelector('#a')!.firstChild!
		const range = document.createRange()
		range.selectNodeContents(text)
		expect(parentOf({ kind: 'text', range, text: '本文' })!.id).toBe('a')
	})
})
