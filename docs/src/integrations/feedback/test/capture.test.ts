// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { blobToBase64, frameRects, shouldSkip, stripInvalidAttributes, withEagerImages } from '../feedback/capture'
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

describe('withEagerImages（画面外の lazy の画像を待ち続けない）', () => {
	/** complete を決め打ちした img（happy-dom は読み込まないので）。 */
	function img(loading: 'lazy' | 'eager', complete: boolean) {
		const i = document.createElement('img')
		i.loading = loading
		i.src = 'https://example.invalid/a.png'
		Object.defineProperty(i, 'complete', { value: complete })
		document.body.append(i)
		return i
	}
	it('読み込まれていない lazy だけを、撮る間 eager にし、撮ったあと lazy に戻す', async () => {
		document.body.innerHTML = ''
		const pending = img('lazy', false)
		const loaded = img('lazy', true)
		const eager = img('eager', false)
		const during = await withEagerImages(document, async () => [pending.loading, loaded.loading, eager.loading])
		expect(during).toEqual(['eager', 'lazy', 'eager'])
		expect([pending.loading, loaded.loading, eager.loading]).toEqual(['lazy', 'lazy', 'eager'])
	})
	it('撮影が失敗しても lazy に戻す', async () => {
		document.body.innerHTML = ''
		const pending = img('lazy', false)
		await expect(withEagerImages(document, async () => {
			expect(pending.loading).toBe('eager')
			throw new Error('boom')
		})).rejects.toThrow('boom')
		expect(pending.loading).toBe('lazy')
	})
	it('fn の戻り値をそのまま返す', async () => {
		document.body.innerHTML = ''
		expect(await withEagerImages(document, async () => 42)).toBe(42)
	})
})
