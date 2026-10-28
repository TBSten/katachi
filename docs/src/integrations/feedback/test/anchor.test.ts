import { describe, expect, it } from 'vitest'
import { placePopover } from '../shared/anchor'

const view = { width: 1280, height: 800 }
const size = { width: 360, height: 300 }
// 下端の案内の帯（left 12 / bottom 56 / 幅 420 / 高さ 50 ぐらい）
const banner = { left: 12, right: 432, top: 694, bottom: 744 }
const overlaps = (p: { left: number; top: number }, a: typeof banner) =>
	p.left < a.right && p.left + size.width > a.left && p.top < a.bottom && p.top + size.height > a.top

describe('placePopover と下端の帯', () => {
	const rect = { left: 100, top: 400, right: 300, bottom: 430 }
	it('帯を避けないと、選んだものの下に置いた popover が帯を覆う（避けない呼び方の基準）', () => {
		expect(overlaps(placePopover(rect, size, view), banner)).toBe(true)
	})
	it('帯を渡すと、帯を覆わない位置（選んだものの上）に置く', () => {
		const p = placePopover(rect, size, view, [banner])
		expect(overlaps(p, banner)).toBe(false)
		expect(p.top + size.height).toBeLessThanOrEqual(rect.top)
	})
	it('帯と重ならない置き場所なら、今までの位置のまま', () => {
		const r = { left: 100, top: 20, right: 300, bottom: 50 }
		expect(placePopover(r, size, view, [banner])).toEqual(placePopover(r, size, view))
	})
	it('どこにも収まらないときは投げず、今までの位置を返す', () => {
		const tall = { width: 360, height: 780 }
		expect(placePopover(rect, tall, view, [banner])).toEqual(placePopover(rect, tall, view))
	})
})
