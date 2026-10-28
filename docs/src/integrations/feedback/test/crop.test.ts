import { describe, expect, it } from 'vitest'
import { cropRect } from '../core/crop'

const view = { width: 500, height: 400 }

describe('cropRect', () => {
	it('dpr を掛けて画像の中に収める', () => {
		expect(cropRect({ x: 100, y: 100, width: 50, height: 20 }, view, { width: 1000, height: 800 })).toEqual({ x: 152, y: 152, width: 196, height: 136 })
	})
	it('画面の外なら null', () => {
		expect(cropRect({ x: 900, y: 900, width: 10, height: 10 }, view, { width: 1000, height: 800 })).toBeNull()
	})
	it('端にかかるときは画像の端で切る', () => {
		expect(cropRect({ x: -10, y: -10, width: 40, height: 40 }, view, { width: 500, height: 400 })).toEqual({ x: 0, y: 0, width: 54, height: 54 })
	})
	it('viewport が 0 でも落ちない', () => {
		expect(cropRect({ x: 0, y: 0, width: 10, height: 10 }, { width: 0, height: 0 }, { width: 100, height: 100 })).not.toBeNull()
	})
})
