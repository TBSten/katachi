// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { clampToView, parsePos } from '../shared/drag'

const view = { width: 1000, height: 800 }
const size = { width: 300, height: 200 }

describe('drag', () => {
	it('画面の中ならそのまま、外なら端に留める', () => {
		expect(clampToView({ left: 100, top: 100 }, size, view)).toEqual({ left: 100, top: 100 })
		expect(clampToView({ left: -50, top: -50 }, size, view)).toEqual({ left: 4, top: 4 })
		expect(clampToView({ left: 5000, top: 5000 }, size, view)).toEqual({ left: 696, top: 596 })
	})
	it('パネルが画面より大きければ左上に寄せる', () => {
		expect(clampToView({ left: 300, top: 300 }, { width: 2000, height: 2000 }, view)).toEqual({ left: 4, top: 4 })
	})
	it('保存した位置の読み出し: 壊れていれば null', () => {
		expect(parsePos('{"left":10,"top":20}')).toEqual({ left: 10, top: 20 })
		for (const bad of [null, '', '{', '{"left":"a","top":1}', '{"left":1}', 'null']) expect(parsePos(bad)).toBeNull()
	})
})
